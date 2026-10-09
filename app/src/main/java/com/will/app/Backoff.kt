package com.will.app

import kotlin.random.Random

/**
 * Паузы между попытками переподключиться: каждая вдвое дольше прежней, от [firstMs] до
 * [maxMs], с разбросом ±[spread], чтобы после сбоя сервера телефоны не стучались в него
 * разом. После удачного входа — [reset], и снова с короткой.
 */
class Backoff(
    private val firstMs: Long = 1_000,
    private val maxMs: Long = 30_000,
    private val spread: Double = 0.2,
    private val random: Random = Random.Default,
) {
    private var attempt = 0

    /** Пауза перед следующей попыткой. */
    fun next(): Long {
        val base = (firstMs shl attempt.coerceAtMost(MAX_SHIFT)).coerceAtMost(maxMs)
        if (base < maxMs) attempt++
        return (base * (1 + spread * (2 * random.nextDouble() - 1))).toLong()
    }

    fun reset() {
        attempt = 0
    }

    private companion object {
        /** Сдвиг дальше уже не нужен: пауза давно упёрлась в предел. */
        const val MAX_SHIFT = 20
    }
}
