package com.will.app

import android.os.SystemClock

/**
 * Часы сервера на телефоне. Метки времени тренировки ставит сервер, и сравнивать их с
 * «сейчас» телефона нельзя: часы телефона могут разойтись с серверными на секунды.
 *
 * Сверка — при входе (`AuthOk` несёт время сервера) и по ответам на свои действия: сервер
 * ставит метку между отправкой и ответом; его «сейчас» тогда — середина этого промежутка. Идут часы от
 * монотонного времени телефона, так что перевод часов телефона их не сбивает. Пока не
 * сверились — идут как часы телефона. Долгий ответ — на переподключении, например, —
 * слишком неточен, и по нему не сверяются. То, что тикает сама система (хронометр
 * уведомления), получает метку сервера по часам телефона.
 */
class ServerClock(
    private val elapsedNs: () -> Long = SystemClock::elapsedRealtimeNanos,
    private val wallNs: () -> Long = { System.currentTimeMillis() * 1_000_000 },
) {
    @Volatile
    private var baseNs: Long = wallNs() - elapsedNs()

    fun nowNs(): Long = elapsedNs() + baseNs

    fun phoneNowNs(): Long = elapsedNs()

    fun toWallMs(serverNs: Long): Long = (serverNs - nowNs() + wallNs()) / 1_000_000

    fun observe(stampNs: Long, sentAt: Long, receivedAt: Long) {
        val roundTrip = receivedAt - sentAt
        if (roundTrip < 0 || roundTrip > MAX_ROUND_TRIP_NS) return
        baseNs = stampNs - (sentAt + receivedAt) / 2
    }

    private companion object {
        const val MAX_ROUND_TRIP_NS = 5_000_000_000L
    }
}
