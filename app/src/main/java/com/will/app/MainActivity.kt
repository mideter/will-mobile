package com.will.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.will.app.ui.WillApp
import com.will.app.ui.WillTheme

class MainActivity : ComponentActivity() {

    private val session get() = (application as WillApplication).session

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Заставка доигрывает «Смирение», но не тогда, когда спешат к тренировке: из её
        // уведомления или назад, посреди неё, после выгрузки процесса.
        if (savedInstanceState == null && !intent.getBooleanExtra(EXTRA_TRAINING, false)) {
            val shownAt = SystemClock.uptimeMillis()
            splash.setKeepOnScreenCondition { SystemClock.uptimeMillis() - shownAt < SPLASH_MS }
        }
        // Система выгрузила процесс, пока экран был в фоне: вернуться туда, где он был.
        val resumed = savedInstanceState?.let { it.getString(KEY_HOST).orEmpty() to it.getString(KEY_ROOM).orEmpty() }
        session.start(resumed ?: ("" to ""))
        setContent {
            WillTheme { WillApp(session) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val (host, room) = session.target
        outState.putString(KEY_HOST, host)
        outState.putString(KEY_ROOM, room)
    }

    companion object {
        /** Приложение открывают, чтобы вернуться к тренировке. */
        const val EXTRA_TRAINING = "com.will.app.TRAINING"

        private const val KEY_HOST = "will.host"
        private const val KEY_ROOM = "will.room"
        /** Сколько длится «Смирение» в `splash_icon_animated.xml`. */
        private const val SPLASH_MS = 1_440L
    }
}
