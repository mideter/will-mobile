package com.will.app.ui

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** Бирюза значка (`ic_launcher_background`). */
private val Teal = Color(0xFF0D7377)

/** Сколько стоит готовый значок перед замыслом и сколько длится сам замысел, в секундах. */
private const val HOLD_S = 1.2f
private const val STORY_S = 2.6f

/**
 * Пока приложение подключается к серверу: значок того же размера и на том же месте, что в
 * системной заставке, — переход из неё незаметен; связи всё нет — значок проигрывает свой
 * замысел, по кругу.
 */
@Composable
fun ConnectingScreen() {
    // Заставка ставит значок в середину окна, а экран начинается под строкой состояния:
    // сдвинуть значок туда, где он был в заставке.
    val window = LocalView.current.rootView
    var shift by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { box ->
                shift = window.height / 2f - (box.positionInWindow().y + box.size.height / 2f)
            },
    ) {
        val y = shift.toInt()
        Emblem(Modifier.align(Alignment.Center).offset { IntOffset(0, y) })
        Text(
            "Подключение к серверу…",
            color = WillColors.Muted,
            modifier = Modifier.align(Alignment.Center).offset { IntOffset(0, y) }.offset(y = 120.dp),
        )
    }
}

/**
 * Значок, который складывается: точка в середине поднимается, от неё вниз прорисовывается
 * линия; линия отходит вправо, из неё выходят две таких же, и она укорачивает себя перед
 * ними. «И кто хочет между вами быть первым, да будет вам рабом» (Мф. 20:27). Кончается
 * значком приложения. Без анимаций в системе — неподвижный значок.
 *
 * Размер — как в системной заставке: круг значка 192 dp.
 */
@Composable
fun Emblem(modifier: Modifier = Modifier) {
    val resolver = LocalContext.current.contentResolver
    val still = remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    // Миг замысла в секундах; отрицательный — готовый значок.
    var t by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val cycle = ((now - start) / 1e9f) % (HOLD_S + STORY_S)
                t = if (cycle < HOLD_S) -1f else cycle - HOLD_S
            }
        }
    }
    Canvas(modifier.size(192.dp)) { drawEmblem(t) }
}

/** Доля пути от [from] до [to] к мигу [t], сглаженная. */
private fun part(t: Float, from: Float, to: Float) = FastOutSlowInEasing.transform(((t - from) / (to - from)).coerceIn(0f, 1f))

private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

/**
 * Значок в миг [t] замысла (секунды; отрицательный — готовый). Единицы — как в значке
 * приложения: холст 108, круг радиусом 36 в середине, полосы шириной 8 от высоты 38.
 */
private fun DrawScope.drawEmblem(t: Float) {
    val u = size.width / 72f
    fun at(x: Float) = (x - 18f) * u
    fun bar(x: Float, length: Float) = drawRect(Color.White, Offset(at(x), at(38f)), Size(8f * u, 32f * length * u))

    drawCircle(Teal, radius = 36f * u, center = Offset(at(54f), at(54f)))
    if (t < 0f) {
        bar(34f, 1f)
        bar(50f, 1f)
        bar(66f, 0.625f)
        return
    }
    // Точка: появляется в середине (0,10–0,35 с) и поднимается к верху линии (0,35–0,70 с).
    if (t in 0.1f..1.1f) {
        val rise = 12f * (1f - part(t, 0.35f, 0.7f))
        drawCircle(Color.White, radius = 4f * u * part(t, 0.1f, 0.35f), center = Offset(at(54f), at(42f + rise)))
    }
    // Линия: от точки вниз (0,70–1,10 с), вправо (1,20–1,55 с), и умаляется (2,15–2,60 с).
    if (t >= 0.7f) {
        val length = when {
            t < 1.1f -> lerp(0.25f, 1f, part(t, 0.7f, 1.1f))
            else -> lerp(1f, 0.625f, part(t, 2.15f, 2.6f))
        }
        bar(50f + 16f * part(t, 1.2f, 1.55f), length)
    }
    // Две из неё: средняя (1,60–2,00 с), за ней левая — дальше (1,70–2,10 с).
    if (t >= 1.6f) bar(50f + 16f * (1f - part(t, 1.6f, 2.0f)), 1f)
    if (t >= 1.7f) bar(34f + 32f * (1f - part(t, 1.7f, 2.1f)), 1f)
}
