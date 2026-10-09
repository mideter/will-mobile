package com.will.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.will.app.ui.captionOf
import com.will.app.ui.restText
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import will.v1.MessengerOuterClass.Word

/**
 * Ведёт идущую тренировку, пока экран погашен: уведомление с таймером подхода или отдыха
 * (его тикает сама система), «Приступить» прямо с экрана блокировки и вибрация, когда
 * заданный отдых вышел. Живёт с первого подхода до завершения тренировки; заброшенную —
 * без подхода дольше [IDLE_MS] после конца отдыха — гасит сам.
 *
 * Тренировку видит из состояния сессии, пока смотрят в её комнату, и помнит последнюю
 * увиденную: отдых считается от меток времени и без связи.
 */
class TrainingService : Service() {

    private val session get() = (application as WillApplication).session
    private val scope = MainScope()

    /** Последнее, что видели о тренировке: она сама и идущий в ней подход. */
    private var training: WordItem? = null
    private var underway: UnderwayItem? = null

    /** Будильник конца отдыха: на какой момент он заведён. */
    private var alarm: Job? = null
    private var alarmAtNs: Long? = null
    private var idle: Job? = null

    private val wakeLock: PowerManager.WakeLock by lazy {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "will:rest")
            .apply { setReferenceCounted(false) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Сейчас по часам сервера: с ними сравнивают метки тренировки. */
    private fun nowNs() = session.clock.nowNs()

    /** Метка сервера для хронометра уведомления: его тикает система по часам телефона. */
    private fun wallMs(serverNs: Long) = session.clock.toWallMs(serverNs)

    override fun onCreate() {
        super.onCreate()
        running = true
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Тренировка", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Подход или отдых идущей тренировки"
                setShowBadge(false)
            },
        )
        // Тихое уведомление сразу: система ждёт его не дольше нескольких секунд.
        ServiceCompat.startForeground(this, NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        scope.launch { session.state.collect(::onState) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_BEGIN) begin()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    private fun onState(state: WillState) {
        val workout = state.workout
        if (workout == null) {
            stopSelf()
            return
        }
        // Комната тренировки перед глазами — её слова и подход свежие.
        val room = (state.view as? View.Room)?.takeIf { it.host == workout.host && it.room == workout.room }
        if (room != null && !state.loading) {
            if (room.words.any { it.kind == Word.Kind.DEED && it.behestId == workout.behestId }) {
                session.stopWorkout()
                return
            }
            room.words.firstOrNull { it.id == workout.behestId }?.let { training = it }
            underway = room.underway?.takeIf { it.behestId == workout.behestId }
        }
        show(state)
        awaitRestEnd(state)
        awaitIdle(state)
    }

    private fun show(state: WillState) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION, notification(state))
        }
    }

    /** Приступить к подходу, который предложен, — нажатием в уведомлении. */
    private fun begin() {
        val t = training ?: return
        val (e, a) = TrainingCourse.selected(t, underway, session.state.value.choice) ?: return
        session.beginApproach(t.id, e, a)
    }

    /** Завести вибрацию на конец заданного отдыха; телефон не уснёт до неё. */
    private fun awaitRestEnd(state: WillState) {
        val t = training
        val rest = t?.let { TrainingCourse.rest(it, TrainingCourse.selected(it, underway, state.choice)) }
        val leftMs = rest?.takeIf { it.willed > 0 }?.let { (it.endNs - nowNs()) / 1_000_000 }
        if (rest == null || leftMs == null || leftMs <= 0) {
            cancelAlarm()
            return
        }
        if (alarmAtNs == rest.endNs) return
        cancelAlarm()
        alarmAtNs = rest.endNs
        wakeLock.acquire(leftMs + WAKE_MARGIN_MS)
        alarm = scope.launch {
            delay(leftMs)
            vibrate()
            show(session.state.value)  // отсчёт кончился — дальше секундомер
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private fun cancelAlarm() {
        alarm?.cancel()
        alarm = null
        alarmAtNs = null
        if (wakeLock.isHeld) wakeLock.release()
    }

    /** Заброшенную тренировку — без подхода дольше [IDLE_MS] после конца отдыха — больше не вести. */
    private fun awaitIdle(state: WillState) {
        idle?.cancel()
        if (underway != null || state.beginning == Reply.Pending) return
        val t = training
        val restEndNs = t?.let { TrainingCourse.rest(it, TrainingCourse.selected(it, null, state.choice)) }?.endNs
        val sinceNs = restEndNs ?: t?.efforts?.maxOfOrNull { it.finishedAtNs } ?: nowNs()
        idle = scope.launch {
            delay((IDLE_MS - (nowNs() - sinceNs) / 1_000_000).coerceAtLeast(0))
            session.stopWorkout()
        }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 250, 500, 250, 500), -1)
        // Как будильник: отдых кончился, даже если телефон в беззвучном режиме.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    /** Уведомление о том, что сейчас: подход, отдых, ожидание первого подхода или всё сделано. */
    private fun notification(state: WillState? = null): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_training)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp())
        val t = training
        if (state == null || t == null) return builder.setContentTitle("Тренировка").build()

        val doing = underway
        if (doing != null) {
            return builder
                .setContentTitle("Подход · " + captionOf(t, doing.exercise, doing.approach))
                .setWhen(wallMs(doing.begunAtNs))
                .setUsesChronometer(true)
                .setShowWhen(true)
                .build()
        }

        val selected = TrainingCourse.selected(t, null, state.choice)
            ?: return builder.setContentTitle("Заданные подходы сделаны").setContentText(t.body).build()
        val next = "Далее: " + captionOf(t, selected.first, selected.second)
        if (state.beginning == Reply.Pending) {
            return builder.setContentTitle("Начинается…").setContentText(next).build()
        }
        builder.addAction(0, "Приступить", beginIntent()).setContentText(next)

        val rest = TrainingCourse.rest(t, selected) ?: return builder.setContentTitle(t.body).build()
        val counting = rest.willed > 0 && nowNs() < rest.endNs
        return builder
            .setContentTitle(if (rest.willed > 0) "Отдых · задано ${restText(rest.willed)}" else "Отдых")
            // Пока заданный не вышел — обратный отсчёт до его конца, потом — сколько отдыхает всего.
            .setWhen(wallMs(if (counting) rest.endNs else rest.sinceNs))
            .setUsesChronometer(true)
            .setChronometerCountDown(counting)
            .setShowWhen(true)
            .build()
    }

    /** Открыть приложение — тем же намерением, что и значок, чтобы вернуться в его задачу. */
    private fun openApp(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    private fun beginIntent(): PendingIntent =
        PendingIntent.getService(
            this, 1,
            Intent(this, TrainingService::class.java).setAction(ACTION_BEGIN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        private const val TAG = "TrainingService"
        private const val CHANNEL = "training"
        private const val NOTIFICATION = 1
        private const val ACTION_BEGIN = "com.will.app.BEGIN"
        private const val WAKE_MARGIN_MS = 10_000L
        /** Без подхода четверть часа после конца отдыха — тренировка заброшена. */
        private const val IDLE_MS = 15 * 60_000L

        @Volatile
        private var running = false

        /** Начать вести тренировку; уже ведёт — ничего. Вызывать, пока приложение на экране. */
        fun start(context: Context) {
            if (running) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, TrainingService::class.java))
            } catch (e: IllegalStateException) {
                // Из фона система сервис не запустит: тренировка идёт и без него, только без уведомления.
                Log.w(TAG, "cannot start: $e")
            }
        }
    }
}
