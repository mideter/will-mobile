package com.will.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerClockTest {

    private var phoneMonotonic = 1_000 * NS
    private var phoneWall = 1_700_000_000 * NS
    private val clock = ServerClock({ phoneMonotonic }, { phoneWall })

    @Test
    fun `before the first check the server clock runs as the phone's`() {
        assertEquals(phoneWall, clock.nowNs())
        phoneMonotonic += 5 * NS
        assertEquals(phoneWall + 5 * NS, clock.nowNs())
    }

    @Test
    fun `the server stamped halfway between sending and the answer`() {
        // Часы телефона отстают на 10 с: сервер поставил метку на 10 с позже середины.
        val start = phoneWall
        val sent = clock.phoneNowNs()
        phoneMonotonic += 2 * NS
        phoneWall += 2 * NS
        val received = clock.phoneNowNs()
        clock.observe(stampNs = start + 1 * NS + 10 * NS, sentAt = sent, receivedAt = received)
        assertEquals(start + 12 * NS, clock.nowNs())
        // На хронометре телефона та же метка — на 10 с раньше.
        assertEquals((start + 1 * NS) / 1_000_000, clock.toWallMs(start + 11 * NS))
    }

    @Test
    fun `setting the phone's clock does not move the server clock`() {
        val before = clock.nowNs()
        phoneWall += 3_600 * NS
        assertEquals(before, clock.nowNs())
    }

    @Test
    fun `an answer that took too long is not trusted`() {
        val before = clock.nowNs()
        val sent = clock.phoneNowNs()
        phoneMonotonic += 30 * NS
        clock.observe(stampNs = 0, sentAt = sent, receivedAt = clock.phoneNowNs())
        assertEquals(before + 30 * NS, clock.nowNs())
    }

    private companion object {
        const val NS = 1_000_000_000L
    }
}
