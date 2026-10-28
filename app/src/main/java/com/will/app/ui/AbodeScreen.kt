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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * Обитель. Своя — как она устроена: внешнее, а в нём внутреннее, и в каждом — его комнаты:
 * подпись «Внешнее» ведёт к Вратам, к самому внешнему, «Внутреннее» — в Кормчую, к самому
 * внутреннему; рядом сказано, что ждёт там: у Внешнего — стук, без имён: стучащие мне ещё
 * никто; у Внутреннего — Просьбы. Под каждой комнатой — что в ней ждёт: дело живёт там, где
 * его выполняют, — что выполнить, что проверить, чего я ожидаю, и идущий подход или отдых.
 * Чужая — в одном внешнем свете: только комнаты, что она мне показывает; назад — к своим.
 */
@Composable
fun AbodeScreen(state: WillState, view: View.Abode, session: WillSession) {
    val own = view.host.isEmpty()
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize()) {
        Header(
            // Место зовётся именем хозяина. Вторая строка — где я на пути: из гостей путь назад —
            // к своему, и подписан он моим именем; дома пути назад нет, и что имя моё, понятно само.
            title = if (own) state.ownName else view.host,
            subtitle = if (own) "дома" else state.ownName,
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
            val outerRooms = view.rooms.filter { it.outer }
            val innerRooms = view.rooms.filter { !it.outer }
            // Ждущий ответа — красным: стучащий у Врат и просящий в Кормчей. «Стук» никого не
            // называет: число — только когда стучат несколько.
            val knocks = when {
                !own || view.waiting == 0 -> null
                view.waiting == 1 -> "стук"
                else -> "стук · ${view.waiting}"
            }
            val asks = if (!own || state.supplications.isEmpty()) null else
                state.supplications.joinToString(", ") +
                    if (state.supplications.size == 1) " просит стать Тренером" else " просят стать Тренером"
            val room = @Composable { item: RoomItem ->
                // Ведут тренировку этой комнаты — значит, в ней идёт подход или отдых.
                val led = own && state.workout?.let { it.host.isEmpty() && it.room == item.name } == true
                RoomLine(item, view, led) { session.enter(item.name) }
            }
            if (own) item {
                // Своя Обитель — внешнее, а в нём внутреннее: заливка в заливке, у каждой — своя тихая
                // подпись, она же вход к краю.
                Depth(WillColors.Row, Modifier.padding(horizontal = 12.dp)) {
                    PartTitle("Внешнее", knocks, knock = true, onClick = { session.gates("") })
                    Rooms(outerRooms, WillColors.Divider, room)
                    Depth(WillColors.Deeper, Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)) {
                        PartTitle("Внутреннее", asks, knock = false, onClick = { session.helm() })
                        Rooms(innerRooms, WillColors.Row, room)
                    }
                }
            } else if (view.rooms.isNotEmpty()) item {
                // Чужая Обитель обращена ко мне своим внешним, как глубоко бы меня ни пускали: всё,
                // что мне показано, — в одном внешнем свете, без внутреннего и без подписей, чтобы
                // устройство экрана не выдавало, насколько мне доверяют.
                Depth(WillColors.Row, Modifier.padding(horizontal = 12.dp)) {
                    Rooms(view.rooms, WillColors.Divider, room)
                }
            }
        }
    }
}

@Composable
private fun Depth(fill: Color, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(fill)) { content() }
}

@Composable
private fun Rooms(rooms: List<RoomItem>, divider: Color, room: @Composable (RoomItem) -> Unit) {
    // Подпись — имя заливки, а не строка списка: черта лишь между комнатами.
    rooms.forEachIndexed { index, item ->
        if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = divider)
        room(item)
    }
}

@Composable
private fun PartTitle(title: String, plea: String?, knock: Boolean, onClick: (() -> Unit)?) {
    PlaqueTitle(title, onClick, plea?.let {
        {
            Spacer(Modifier.width(10.dp))
            if (knock) Knock()
            Text(it, fontSize = 13.sp, color = WillColors.Supplication, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    })
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
    val said = listOfNotNull(room.role) + deeds
    val quiet = said.isEmpty() && going == null

    Line(room.who, if (quiet) null else ({
        Row {
            if (said.isNotEmpty()) Text(said.joinToString(" · "), fontSize = 13.sp, color = WillColors.Muted)
            if (said.isNotEmpty() && going != null) Text(" · ", fontSize = 13.sp, color = WillColors.Muted)
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
