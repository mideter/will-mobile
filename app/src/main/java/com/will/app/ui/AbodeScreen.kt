package com.will.app.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.RoomItem
import com.will.app.View
import com.will.app.WillSession
import com.will.app.WillState

/**
 * Обитель: её комнаты, внутренние и внешние. Своя: «Внутреннее» ведёт в Кормчую, к самому
 * внутреннему, «Внешнее» — к Вратам, к самому внешнему; у них сказано, что ждёт там: у
 * Внешнего — стук, без имён: стучащие мне ещё никто; у Внутреннего — Просьбы. Под каждой
 * комнатой — что в ней ждёт: дело живёт там, где его выполняют, — что выполнить, что
 * проверить, чего я ожидаю, и идущий подход или отдых. Чужая: только комнаты, что она мне
 * показывает; назад — к её Вратам.
 */
@Composable
fun AbodeScreen(state: WillState, view: View.Abode, session: WillSession) {
    val own = view.host.isEmpty()
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize()) {
        Header(
            // Место зовётся именем хозяина. Вторая строка — где я на пути: из гостей путь назад
            // лежит к своим Вратам, а дома пути назад нет, и что имя моё, понятно само.
            title = if (own) state.ownName else view.host,
            subtitle = if (own) "дома" else "Врата — ${state.ownName}",
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
            for ((title, outer) in listOf("Внутреннее" to false, "Внешнее" to true)) {
                val rooms = view.rooms.filter { it.outer == outer }
                if (rooms.isEmpty() && !own) continue
                // Ждущий ответа — красным: стучащий у Врат и просящий в Кормчей.
                val plea = when {
                    !own -> null
                    // «Стук» никого не называет: число — только когда стучат несколько.
                    outer -> when (view.waiting) {
                        0 -> null
                        1 -> "стук"
                        else -> "стук · ${view.waiting}"
                    }
                    state.supplications.isNotEmpty() ->
                        state.supplications.joinToString(", ") +
                            if (state.supplications.size == 1) " просит стать Тренером" else " просят стать Тренером"
                    else -> null
                }
                val edge = if (!own) null else if (outer) ({ session.gates("") }) else ({ session.helm() })
                item { PartTitle(title, plea, knock = outer, onClick = edge) }
                if (rooms.isEmpty()) continue
                item {
                    Group(rooms) { room ->
                        // Ведут тренировку этой комнаты — значит, в ней идёт подход или отдых.
                        val led = own && state.workout?.let { it.host.isEmpty() && it.room == room.name } == true
                        RoomLine(room, view, led) { session.enter(room.name) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartTitle(title: String, plea: String?, knock: Boolean, onClick: (() -> Unit)?) {
    // Заголовок части своей Обители ведёт к её краю — синий, как всякое действие.
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (onClick != null) WillColors.Accent else WillColors.Muted,
        )
        if (plea != null) {
            Spacer(Modifier.width(10.dp))
            if (knock) Knock()
            Text(plea, fontSize = 13.sp, color = WillColors.Supplication, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        if (onClick != null) Text("›", fontSize = 16.sp, color = WillColors.Muted)
    }
}

@Composable
private fun RoomLine(room: RoomItem, view: View.Abode, led: Boolean, onClick: () -> Unit) {
    val deeds = listOf("выполнить" to view.owed, "проверить" to view.unreviewed, "ожидаю" to view.awaited)
        .mapNotNull { (what, list) -> list.count { it.room == room.name }.takeIf { it > 0 }?.let { "$what $it" } }
    val underway = view.owed.firstNotNullOfOrNull { waiting -> waiting.underway?.takeIf { waiting.room == room.name } }
    val going = when {
        underway != null -> if (underway.endedAtNs == null) "идёт подход" else "подход закончен — как прошёл?"
        led -> "идёт отдых"
        else -> null
    }
    val quiet = deeds.isEmpty() && going == null

    Line(room.said, if (quiet) null else ({
        Row {
            if (deeds.isNotEmpty()) Text(deeds.joinToString(" · "), fontSize = 13.sp, color = WillColors.Muted)
            if (deeds.isNotEmpty() && going != null) Text(" · ", fontSize = 13.sp, color = WillColors.Muted)
            if (going != null) {
                Text(
                    going,
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
