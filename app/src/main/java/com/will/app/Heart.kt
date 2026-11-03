package com.will.app

class Heart(private val silenceMs: Long = 3_000) {

    private var beatAtMs: Long? = null
    private var awaiting = false

    data class Pulse(val beat: Boolean, val nextInMs: Long)

    fun wake() {
        beatAtMs = null
        awaiting = false
    }

    fun hear() {
        awaiting = false
    }

    fun pulse(nowMs: Long, paceMs: Long): Pulse? {
        val waited = beatAtMs?.let { nowMs - it }
        if (awaiting && waited != null) {
            if (waited >= silenceMs) return null
            return Pulse(beat = false, nextInMs = minOf(paceMs, silenceMs - waited))
        }
        if (waited != null && waited < paceMs) return Pulse(beat = false, nextInMs = paceMs - waited)
        beatAtMs = nowMs
        awaiting = true
        return Pulse(beat = true, nextInMs = minOf(paceMs, silenceMs))
    }
}
