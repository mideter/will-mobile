package com.will.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.UnderwayItem
import com.will.app.View
import com.will.app.WillSession
import com.will.app.Reply
import com.will.app.WillState
import com.will.app.WordItem
import will.v1.MessengerOuterClass.Word

/**
 * Комната: лента слов отражаемого места. Поле ввода — только там, где можно писать
 * (своя Келья; Ведение — у Тренера; там же — «Тренировка»). Послушник исполняет Веление,
 * нажав на него самого; тренировку — отчётом о сделанном.
 */
/** Слово, которым сервер называет Дело без отчёта. */
private const val SILENT_DEED = "совершено"

@Composable
fun RoomScreen(state: WillState, view: View.Room, session: WillSession) {
    // Отчёт — ответ на задание: он показывается внутри него, а не отдельной записью.
    val deeds = view.words.filter { it.kind == Word.Kind.DEED }.associateBy { it.behestId }
    val fulfilled = deeds.keys
    val shown = feedOf(view.words)
    // Своя комната, где писать нельзя, — Послушание: здесь исполняют.
    val novice = view.host.isEmpty() && !view.writable
    // Писать можно и не в Келье — значит, это Ведение: здесь велят и тренировки.
    val trainer = view.writable && view.room != WillSession.CELL
    // Что открыто, переживает поворот экрана: задание — по номеру, само оно — из ленты.
    var fulfillingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val fulfilling = fulfillingId?.let { id -> view.words.firstOrNull { it.id == id && it.id !in fulfilled } }
    var composing by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val names = view.words.flatMap { w -> w.exercises.map { it.name } }.distinct()

    // Редактор закрывается, когда сервер сказал, что тренировка задана; иначе остаётся с набранным.
    LaunchedEffect(state.willing) {
        if (state.willing == Reply.Granted) {
            composing = false
            session.willingSeen()
        }
    }
    // Окно выполнения закрывается, когда сервер сказал, что задание выполнено.
    LaunchedEffect(state.fulfilling) {
        if (state.fulfilling == Reply.Granted) {
            fulfillingId = null
            session.fulfillingSeen()
        }
    }
    val sendingFulfil = state.fulfilling == Reply.Pending
    if (composing) {
        TrainingEditor(
            heading = view.room,
            names = names,
            sending = state.willing == Reply.Pending,
            onDone = session::train,
            onCancel = { composing = false },
        )
        return
    }
    // Исполненное или пропавшее из ленты закрывается.
    if (fulfillingId != null && fulfilling == null) fulfillingId = null
    // Тренировку выполняют по подходам; берётся живая — с усилиями, пришедшими только что.
    fulfilling?.takeIf { it.exercises.isNotEmpty() }?.let { training ->
        TrainingRun(
            training, view.underway, state.choice, session,
            beginning = state.beginning == Reply.Pending,
            finishing = sendingFulfil,
            onClose = { fulfillingId = null },
        )
        return
    }

    LaunchedEffect(view.words.size) {
        if (shown.isNotEmpty()) listState.scrollToItem(shown.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        Header(
            title = view.room,
            subtitle = if (view.host.isEmpty()) "Моя Обитель" else "Обитель ${view.host}",
            onBack = { session.back() },
        )
        ConnectionBanner(state.connection)

        if (view.words.isEmpty() && !state.loading) {
            Column(Modifier.weight(1f)) { Hint("Здесь пока ничего нет") }
        } else {
            LazyColumn(Modifier.weight(1f), state = listState) {
                items(shown, key = { it.id }) { word ->
                    WordRow(
                        word = word,
                        deed = deeds[word.id],
                        underway = view.underway?.takeIf { it.behestId == word.id },
                        onFulfil = if (novice && word.kind == Word.Kind.BEHEST && word.id !in fulfilled) {
                            { fulfillingId = word.id }
                        } else {
                            null
                        },
                        done = word.kind == Word.Kind.BEHEST && word.id in fulfilled,
                    )
                    HorizontalDivider(color = WillColors.Divider)
                }
            }
        }

        if (trainer) {
            TextButton(onClick = { composing = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("+ Тренировка")
            }
        }
        if (view.writable) {
            Composer(
                hint = if (trainer) "Задать…" else "Написать…",
                onSend = session::say,
            )
        }
    }

    fulfilling?.let { behest ->
        var report by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { fulfillingId = null },
            title = { Text("Выполнить") },
            text = {
                Column {
                    Text(behest.body, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = report,
                        onValueChange = { report = it },
                        placeholder = { Text("Отчёт (необязательно)") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { session.fulfil(behest.id, report) }, enabled = !sendingFulfil) {
                    Text(if (sendingFulfil) "Отправляется…" else "Выполнить")
                }
            },
            dismissButton = { TextButton(onClick = { fulfillingId = null }) { Text("Отмена") } },
        )
    }
}

/**
 * Лента: слова по времени, но отчёт не отдельной записью — он внутри своего задания, а
 * задание с отчётом стоит там, где пришёл отчёт: новое — внизу. Отчёт, чьё задание
 * не видно, остаётся записью.
 */
private fun feedOf(words: List<WordItem>): List<WordItem> {
    val ids = words.map { it.id }.toSet()
    val answeredAt = words.withIndex()
        .filter { it.value.kind == Word.Kind.DEED && it.value.behestId in ids }
        .associate { it.value.behestId to it.index }
    return words.withIndex()
        .filterNot { it.value.kind == Word.Kind.DEED && it.value.behestId in ids }
        .sortedBy { answeredAt[it.value.id] ?: it.index }
        .map { it.value }
}

@Composable
private fun WordRow(word: WordItem, deed: WordItem?, underway: UnderwayItem?, onFulfil: (() -> Unit)?, done: Boolean) {
    // Веление, ждущее Послушника, исполняют нажатием на него самого.
    Column(
        Modifier
            .fillMaxWidth()
            .let { if (onFulfil != null) it.clickable(onClick = onFulfil) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (!word.mine) {
            Text(word.author, fontSize = 12.sp, color = WillColors.Muted)
        }
        when (word.kind) {
            Word.Kind.BEHEST -> {
                Text(
                    (if (word.exercises.isEmpty()) "Задание" else "Тренировка") + if (done) " · ✓ Выполнено" else "",
                    fontSize = 12.sp,
                    color = WillColors.Accent,
                )
                Text(word.body, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                // Отчёт Послушника — его слово, если он что-то сказал.
                if (deed != null && deed.body != SILENT_DEED) {
                    Text("«${deed.body}»", fontSize = 14.sp, color = WillColors.Ink, modifier = Modifier.padding(top = 2.dp))
                }
                when {
                    word.exercises.isEmpty() -> Unit
                    // Выполнение по подходам: заданное и сделанное, вживую.
                    word.efforts.isNotEmpty() || underway != null -> EffortsView(word, underway, finished = deed != null)
                    deed != null -> ComparedExercisesView(word.exercises, deed.exercises)
                    else -> ExercisesView(word.exercises)
                }
                if (onFulfil != null) {
                    Text(
                        "Нажмите, чтобы выполнить ›",
                        fontSize = 12.sp,
                        color = WillColors.Accent,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Word.Kind.DEED -> {
                // Отчёт, чьё задание здесь не видно.
                Text("✓ Выполнено", fontSize = 12.sp, color = WillColors.Accent)
                Text(word.body, fontSize = 15.sp)
                if (word.exercises.isNotEmpty()) ExercisesView(word.exercises)
            }
            else -> Text(word.body, fontSize = 15.sp)
        }
    }
}

@Composable
private fun Composer(hint: String, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().background(WillColors.Composer).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(hint) },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = WillColors.Composer,
                unfocusedContainerColor = WillColors.Composer,
                focusedIndicatorColor = WillColors.Composer,
                unfocusedIndicatorColor = WillColors.Composer,
            ),
            shape = RoundedCornerShape(0.dp),
        )
        TextButton(
            onClick = {
                if (text.isNotBlank()) {
                    onSend(text)
                    text = ""
                }
            },
            enabled = text.isNotBlank(),
        ) { Text("→", fontSize = 20.sp) }
    }
}
