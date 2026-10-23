package com.will.app

/**
 * Сердце сессии: бьётся к серверу с заданным шагом и знает, что связь жива, пока сервер
 * слышен. Проснувшись, бьётся сразу. Удар ждёт ответа; пока ждёт, нового не дают. Ответ —
 * любое слово сервера после удара: всякое доказывает, что он слышит. Не слышно дольше
 * [silenceMs] после удара — связь мертва, сколько бы ни казалась живой. Время — по часам без
 * сна (`uptimeMillis`): пока телефон спит, сердце не бьётся и молчание не копится.
 */
class Heart(private val silenceMs: Long = 3_000) {

    private var beatAtMs: Long? = null
    private var awaiting = false

    /** Удар сейчас ли и через сколько спросить снова; `null` — сервер молчит, связь мертва. */
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
