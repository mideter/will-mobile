package com.will.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.Aspect
import com.will.app.View
import com.will.app.Waiting
import com.will.app.WillSession
import com.will.app.WillState

/** Две стороны своей Обители: [Deeds] — что ждёт ответа, [Rooms] — сами комнаты. */
enum class AbodeTab { Deeds, Rooms }

/**
 * Обитель. Своя — двумя вкладками: Дела — Просьбы, что мне выполнить, что проверить и чего я
 * ожидаю от других, каждое ведёт в свою комнату; Комнаты — по частям: во внешней — Врата, через
 * них выходят; во внутренней — Кормчая, где отвечают на Просьбы. Чужая: только комнаты,
 * открытые моему роду; назад — к её Вратам.
 */
@Composable
fun AbodeScreen(
    state: WillState,
    view: View.Abode,
    session: WillSession,
    tab: AbodeTab,
    onTab: (AbodeTab) -> Unit,
) {
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

        Box(Modifier.weight(1f)) {
            if (own && tab == AbodeTab.Deeds) Deeds(state, view, session) else Rooms(view, session)
        }

        if (own) {
            HorizontalDivider(color = WillColors.Divider)
            Row(Modifier.fillMaxWidth()) {
                // Из Комнат тихо сказано, сколько ждёт в Делах; на самих Делах число повторяло бы их.
                val waiting = if (tab == AbodeTab.Rooms) view.owed.size + view.unreviewed.size + state.supplications.size else 0
                Tab("Дела", waiting, tab == AbodeTab.Deeds) { onTab(AbodeTab.Deeds) }
                Tab("Комнаты", 0, tab == AbodeTab.Rooms) { onTab(AbodeTab.Rooms) }
            }
        }
    }
}

@Composable
private fun Deeds(state: WillState, view: View.Abode, session: WillSession) {
    val helm = view.rooms.firstOrNull { it.aspect == Aspect.Dwellers }?.name
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (state.supplications.isNotEmpty()) {
            item { SectionTitle("Просьбы") }
            item {
                Group(state.supplications, WillColors.SupplicationRow) { suppliant ->
                    Line(suppliant, { Text("Просит вас стать Тренером", fontSize = 13.sp, color = WillColors.Supplication) }) {
                        helm?.let { session.enter(it) }
                    }
                }
            }
        }
        if (view.owed.isNotEmpty()) {
            item { SectionTitle("Выполнить") }
            item { Group(view.owed) { waiting -> WaitingRow(waiting) { session.enter(waiting.room) } } }
        }
        if (view.unreviewed.isNotEmpty()) {
            item { SectionTitle("Проверить") }
            item { Group(view.unreviewed) { waiting -> WaitingRow(waiting) { session.enter(waiting.room) } } }
        }
        if (view.awaited.isNotEmpty()) {
            item { SectionTitle("Ожидаю") }
            item { Group(view.awaited) { waiting -> WaitingRow(waiting) { session.enter(waiting.room) } } }
        }
        if (state.supplications.isEmpty() && view.owed.isEmpty() && view.unreviewed.isEmpty() && view.awaited.isEmpty()) {
            item { Hint("Дел нет") }
        }
    }
}

@Composable
private fun Rooms(view: View.Abode, session: WillSession) {
    val own = view.host.isEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (view.rooms.isEmpty()) {
            item { Hint(if (own) "Комнат нет" else "Ни одна комната вам здесь не открыта") }
        }
        for ((title, outer) in listOf("Внутренняя часть" to false, "Внешняя часть" to true)) {
            // Ядро части — Врата или Кормчая — впереди её Келий.
            val rooms = view.rooms.filter { it.outer == outer }.sortedBy { it.aspect == Aspect.Words }
            if (rooms.isEmpty()) continue
            item { SectionTitle(title) }
            item { Group(rooms) { room -> Line(room.name) { session.enter(room.name) } } }
        }
    }
}

@Composable
private fun RowScope.Tab(title: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 15.sp, color = if (selected) WillColors.Accent else WillColors.Muted)
        if (count > 0) Text("$count", fontSize = 13.sp, color = WillColors.Muted, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun WaitingRow(waiting: Waiting, onClick: () -> Unit) {
    val what = if (waiting.word.exercises.isEmpty()) "задание" else "тренировка"
    Line(waiting.word.body, {
        Row {
            Text("${waiting.counterpart} · $what", fontSize = 13.sp, color = WillColors.Muted)
            waiting.underway?.let {
                Text(" · ", fontSize = 13.sp, color = WillColors.Muted)
                Text(
                    if (it.endedAtNs == null) "идёт подход" else "подход закончен — как прошёл?",
                    fontSize = 13.sp,
                    color = WillColors.Accent,
                )
            }
        }
    }, onClick)
}

@Composable
private fun Line(title: String, below: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (below == null) 12.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            below?.invoke()
        }
        Text("›", fontSize = 18.sp, color = WillColors.Muted)
    }
}
