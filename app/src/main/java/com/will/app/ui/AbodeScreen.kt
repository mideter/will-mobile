package com.will.app.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.Aspect
import com.will.app.RoomItem
import com.will.app.View
import com.will.app.WillSession
import com.will.app.WillState

/**
 * Обитель: её комнаты по частям — во внешней Врата, через них выходят; во внутренней Кормчая
 * и Кельи. Своя: под каждой комнатой сказано, что в ней ждёт, — дело живёт там, где его
 * выполняют: во Вратах — стук, без имён: они мне ещё никто; в
 * Кормчей — Просьбы; в комнате Уз — что выполнить, что проверить, чего я ожидаю, и идущий
 * подход. Чужая: только комнаты, открытые моему роду; назад — к её Вратам.
 */
@Composable
fun AbodeScreen(state: WillState, view: View.Abode, session: WillSession) {
    val own = view.host.isEmpty()
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize()) {
        Header(
            // Место зовётся именем хозяина. Вторая строка — где я на пути: в гостях путь назад
            // лежит через Врата, а дома пути назад нет, и что имя моё, понятно само.
            title = if (own) state.ownName else view.host,
            subtitle = if (own) "дома" else "Врата",
            onBack = if (own) null else ({ session.back() }),
            onTitleClick = if (own) ({
                clipboard.setText(AnnotatedString(state.ownName))
                session.tell("Имя ${state.ownName} скопировано")
            }) else null,
        )
        val listState = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize().goneUnder(listState), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
            if (view.rooms.isEmpty()) {
                item { Hint(if (own) "Комнат нет" else "Ни одна комната вам здесь не открыта") }
            }
            for ((title, outer) in listOf("Внутренняя часть" to false, "Внешняя часть" to true)) {
                // Ядро части — Врата или Кормчая — впереди её Келий.
                val rooms = view.rooms.filter { it.outer == outer }.sortedBy { it.aspect == Aspect.Words }
                if (rooms.isEmpty()) continue
                item { SectionTitle(title) }
                item {
                    Group(rooms) { room ->
                        // Ждущий ответа — красным: у Врат и с Просьбой в Кормчей.
                        val plea = when {
                            !own -> null
                            // «Стук» никого не называет: число — только когда стучат несколько.
                            room.aspect == Aspect.Threshold -> when (view.waiting) {
                                0 -> null
                                1 -> "стук"
                                else -> "стук · ${view.waiting}"
                            }
                            room.aspect == Aspect.Dwellers && state.supplications.isNotEmpty() ->
                                state.supplications.joinToString(", ") +
                                    if (state.supplications.size == 1) " просит стать Тренером" else " просят стать Тренером"
                            else -> null
                        }
                        RoomLine(room, view, plea) { session.enter(room.name) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomLine(room: RoomItem, view: View.Abode, plea: String?, onClick: () -> Unit) {
    val deeds = listOf("выполнить" to view.owed, "проверить" to view.unreviewed, "ожидаю" to view.awaited)
        .mapNotNull { (what, list) -> list.count { it.room == room.name }.takeIf { it > 0 }?.let { "$what $it" } }
    val underway = view.owed.firstNotNullOfOrNull { waiting -> waiting.underway?.takeIf { waiting.room == room.name } }
    val quiet = plea == null && deeds.isEmpty() && underway == null

    Line(room.said, if (quiet) null else ({
        if (plea != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (room.aspect == Aspect.Threshold) Knock()
                Text(plea, fontSize = 13.sp, color = WillColors.Supplication)
            }
        }
        Row {
            if (deeds.isNotEmpty()) Text(deeds.joinToString(" · "), fontSize = 13.sp, color = WillColors.Muted)
            if (deeds.isNotEmpty() && underway != null) Text(" · ", fontSize = 13.sp, color = WillColors.Muted)
            if (underway != null) {
                Text(
                    if (underway.endedAtNs == null) "идёт подход" else "подход закончен — как прошёл?",
                    fontSize = 13.sp,
                    color = WillColors.Accent,
                )
            }
        }
    }), onClick)
}

@Composable
private fun Knock() {
    // Стук, а не мигание: два коротких удара — и долгая тишина, чтобы не торопить.
    val beat by rememberInfiniteTransition(label = "knock").animateFloat(
        initialValue = QUIET,
        targetValue = QUIET,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 5000
                QUIET at 0
                1f at 70
                QUIET at 240
                1f at 310
                QUIET at 480
            },
        ),
        label = "beat",
    )
    Box(
        Modifier
            .padding(end = 6.dp)
            .size(7.dp)
            .graphicsLayer { alpha = beat }
            .background(WillColors.Supplication, CircleShape),
    )
}

private const val QUIET = 0.2f
