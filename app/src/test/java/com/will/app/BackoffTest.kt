package com.will.app

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffTest {

    private fun withoutSpread() = Backoff(spread = 0.0)

    @Test
    fun `each pause is twice the one before, up to the limit`() {
        val backoff = withoutSpread()
        assertEquals(listOf(1_000L, 2_000, 4_000, 8_000, 16_000, 30_000, 30_000), List(7) { backoff.nextPauseMs() })
    }

    @Test
    fun `after a good connection it starts short again`() {
        val backoff = withoutSpread()
        repeat(10) { backoff.nextPauseMs() }
        backoff.reset()
        assertEquals(1_000L, backoff.nextPauseMs())
    }

    @Test
    fun `pauses are spread around the middle and never beyond it by more than the spread`() {
        val backoff = Backoff(random = Random(7))
        repeat(100) {
            backoff.reset()
            val pause = backoff.nextPauseMs()
            assertTrue("$pause", pause in 800..1_200)
        }
    }
}
