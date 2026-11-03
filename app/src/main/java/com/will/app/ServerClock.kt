package com.will.app

import android.os.SystemClock

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
