package com.will.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.RoomItem
import com.will.app.View
import com.will.app.Waiting
import com.will.app.WillSession
import com.will.app.WillState

/**
 * Обитель. Своя: обзор — что ждёт, — и комнаты; долгое нажатие на комнате переносит её
 * в другую часть. Чужая: комнаты, открытые моему роду.
 */
@Composable
fun AbodeScreen(
    state: WillState,
    view: View.Abode,
    session: WillSession,
    menu: @Composable () -> Unit,
) {
    val own = view.host.isEmpty()
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize()) {
        Header(
            title = if (own) "Моя Обитель" else "Обитель ${view.host}",
            subtitle = if (own) "Я — ${state.ownName}" else "Вы обитаете здесь",
            onBack = if (own) null else ({ session.back() }),
            onTitleClick = if (own) ({
                clipboard.setText(AnnotatedString(state.ownName))
                session.tell("Имя ${state.ownName} скопировано")
            }) else null,
            actions = { if (own) menu() },
        )
        ConnectionBanner(state.connection)

        LazyColumn(Modifier.fillMaxSize()) {
            if (own) {
                item { SectionTitle("Ждёт") }
                if (view.waiting.isEmpty()) {
                    item { Hint("Ничего не ждёт") }
                } else {
                    items(view.waiting) { waiting -> WaitingCard(waiting) { session.enter(waiting.room) } }
                }
            }

            if (view.rooms.isEmpty()) {
                item { SectionTitle("Комнаты") }
                item { Hint(if (own) "Комнат нет" else "Ни одна комната вам здесь не открыта") }
            }
            for ((title, outer) in listOf("Внутренние комнаты" to false, "Внешние комнаты" to true)) {
                val rooms = view.rooms.filter { it.outer == outer }
                if (rooms.isEmpty()) continue
                item { SectionTitle(title) }
                items(rooms) { room ->
                    RoomRow(room = room, onClick = { session.enter(room.name) })
                }
            }
        }
    }

}

@Composable
private fun WaitingCard(waiting: Waiting, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(WillColors.Row, RoundedCornerShape(10.dp))
            .combinedClickableCompat(onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(waiting.body, fontSize = 15.sp)
        Text(waiting.room, fontSize = 12.sp, color = WillColors.Muted)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoomRow(room: RoomItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(room.name, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text("›", fontSize = 18.sp, color = WillColors.Muted)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = combinedClickable(onClick = onClick)
