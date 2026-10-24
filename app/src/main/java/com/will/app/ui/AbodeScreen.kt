package com.will.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.Aspect
import com.will.app.View
import com.will.app.RoomItem
import com.will.app.WillSession
import com.will.app.WillState

/**
 * Обитель: её комнаты по частям — во внешней Врата, через них выходят; во внутренней Кормчая и
 * Кельи. Своя: под каждой комнатой сказано, что в ней ждёт, — дело живёт там, где его
 * выполняют: в Кормчей — Просьбы, в комнате Уз — что выполнить, что проверить, чего я ожидаю,
 * и идущий подход. Чужая: только комнаты, открытые моему роду; назад — к её Вратам.
 */
@Composable
fun AbodeScreen(state: WillState, view: View.Abode, session: WillSession) {
    val own = view.host.isEmpty()
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize()) {
        Header(
            // Место зовётся именем хозяина; что это имя — моё, сказано только здесь.
            title = if (own) state.ownName else view.host,
            subtitle = if (own) "это вы" else "Врата",
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
                        val suppliants = if (own && room.aspect == Aspect.Dwellers) state.supplications else emptyList()
                        RoomLine(room, view, suppliants) { session.enter(room.name) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomLine(room: RoomItem, view: View.Abode, suppliants: List<String>, onClick: () -> Unit) {
    val deeds = listOf("выполнить" to view.owed, "проверить" to view.unreviewed, "ожидаю" to view.awaited)
        .mapNotNull { (what, list) -> list.count { it.room == room.name }.takeIf { it > 0 }?.let { "$what $it" } }
    val underway = view.owed.firstNotNullOfOrNull { waiting -> waiting.underway?.takeIf { waiting.room == room.name } }
    val quiet = suppliants.isEmpty() && deeds.isEmpty() && underway == null

    Line(room.said, if (quiet) null else ({
        if (suppliants.isNotEmpty()) {
            Text(
                suppliants.joinToString(", ") + if (suppliants.size == 1) " просит стать Тренером" else " просят стать Тренером",
                fontSize = 13.sp,
                color = WillColors.Supplication,
            )
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
