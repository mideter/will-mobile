package com.will.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Телефон, в котором живёт сессия: токен её тела, сеть — какая сейчас и что появилась или
 * пропала, — время без сна, главный поток с отложенными действиями, служба тренировки и
 * журнал. Сеть телефон только различает: что она такое, сессии знать незачем. Всё, о чём он
 * сообщает, приходит на главный поток. На деле это [AndroidPhone]; в тестах телефон
 * подделывают, и время в нём идёт рукой.
 */
interface Phone {
    val deviceToken: String

    val network: Any?

    fun watchNetwork(onAvailable: (Any) -> Unit, onLost: (Any) -> Unit)

    fun uptimeMs(): Long

    fun postDelayed(action: Runnable, delayMs: Long)

    fun cancel(action: Runnable)

    fun leadTraining()

    fun log(message: String)
}


/** Телефон на Android: собран из [Context] приложения. */
class AndroidPhone(context: Context) : Phone {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)

    override val deviceToken: String get() = DeviceTokenStore.loadOrCreate(appContext)

    override val network: Any? get() = connectivity?.activeNetwork

    override fun watchNetwork(onAvailable: (Any) -> Unit, onLost: (Any) -> Unit) {
        val connectivity = connectivity ?: return
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainHandler.post { onAvailable(network) }
            }

            override fun onLost(network: Network) {
                mainHandler.post { onLost(network) }
            }
        })
    }

    override fun uptimeMs(): Long = SystemClock.uptimeMillis()

    override fun postDelayed(action: Runnable, delayMs: Long) {
        mainHandler.postDelayed(action, delayMs)
    }

    override fun cancel(action: Runnable) {
        mainHandler.removeCallbacks(action)
    }

    override fun leadTraining() = TrainingService.start(appContext)

    override fun log(message: String) {
        Log.w("WillSession", message)
    }
}
