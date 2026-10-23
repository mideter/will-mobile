package com.will.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartTest {

    private fun awake() = Heart(silenceMs = 3_000).apply { wake() }

    @Test
    fun `on waking it beats at once`() {
        assertEquals(Heart.Pulse(beat = true, nextInMs = 500), awake().pulse(1_000, paceMs = 500))
    }

    @Test
    fun `heard, it beats again at its pace`() {
        val heart = awake()
        heart.pulse(1_000, paceMs = 500)
        heart.hear()
        assertEquals(Heart.Pulse(beat = false, nextInMs = 100), heart.pulse(1_400, paceMs = 500))
        assertEquals(Heart.Pulse(beat = true, nextInMs = 500), heart.pulse(1_500, paceMs = 500))
    }

    @Test
    fun `while a beat is unheard no other is given`() {
        val heart = awake()
        heart.pulse(1_000, paceMs = 500)
        assertEquals(Heart.Pulse(beat = false, nextInMs = 500), heart.pulse(1_500, paceMs = 500))
        assertEquals(Heart.Pulse(beat = false, nextInMs = 500), heart.pulse(3_000, paceMs = 500))
    }

    @Test
    fun `unheard longer than the silence, the connection is dead`() {
        val heart = awake()
        heart.pulse(1_000, paceMs = 500)
        assertNull(heart.pulse(4_000, paceMs = 500))
    }

    @Test
    fun `a calm pace still listens for the answer within the silence`() {
        val heart = awake()
        assertEquals(Heart.Pulse(beat = true, nextInMs = 3_000), heart.pulse(1_000, paceMs = 30_000))
        assertNull(heart.pulse(4_000, paceMs = 30_000))
    }

    @Test
    fun `any word of the server after the beat is its answer`() {
        val heart = awake()
        heart.pulse(1_000, paceMs = 30_000)
        heart.hear()
        assertEquals(Heart.Pulse(beat = false, nextInMs = 27_000), heart.pulse(4_000, paceMs = 30_000))
    }
}
