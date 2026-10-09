package com.will.app

import android.os.SystemClock

/**
 * Часы сервера на телефоне. Метки времени тренировки ставит сервер, и сравнивать их с
 * «сейчас» телефона нельзя: часы телефона могут разойтись с серверными на секунды.
 *
 * Сверка — по ответам на свои действия: сервер ставит метку, когда принимает подход, —
 * между отправкой и ответом; его «сейчас» тогда — середина этого промежутка. Идут часы от
 * монотонного времени телефона, так что перевод часов телефона их не сбивает. Пока не
 * сверились — идут как часы телефона.
 */
class ServerClock(
    private val elapsedNs: () -> Long = SystemClock::elapsedRealtimeNanos,
    private val wallNs: () -> Long = { System.currentTimeMillis() * 1_000_000 },
) {
    /** Сервер «сейчас» — это [elapsedNs] плюс [baseNs]. */
    @Volatile
    private var baseNs: Long = wallNs() - elapsedNs()

    /** Сейчас по часам сервера, наносекунды от эпохи. */
    fun nowNs(): Long = elapsedNs() + baseNs

    /** Монотонное «сейчас» телефона — им отмечают отправку и ответ для [observe]. */
    fun mark(): Long = elapsedNs()

    /** Метка сервера по часам телефона, миллисекунды: для того, что тикает сама система. */
    fun toWallMs(serverNs: Long): Long = (serverNs - nowNs() + wallNs()) / 1_000_000

    /**
     * Сервер поставил метку [stampNs] между отправкой [sentAt] и ответом [receivedAt] (по [mark]).
     * Долгий ответ — на переподключении, например, — слишком неточен, и его не берут.
     */
    fun observe(stampNs: Long, sentAt: Long, receivedAt: Long) {
        val roundTrip = receivedAt - sentAt
        if (roundTrip < 0 || roundTrip > MAX_ROUND_TRIP_NS) return
        baseNs = stampNs - (sentAt + receivedAt) / 2
    }

    private companion object {
        const val MAX_ROUND_TRIP_NS = 5_000_000_000L
    }
}
