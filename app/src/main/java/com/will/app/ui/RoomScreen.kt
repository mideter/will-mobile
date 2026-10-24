package com.will.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.Reply
import com.will.app.UnderwayItem
import com.will.app.View
import com.will.app.WillSession
import com.will.app.WillState
import com.will.app.WordItem
import will.v1.MessengerOuterClass.Word

private const val SILENT_DEED = "совершено"

/**
 * Комната: лента слов отражаемого места. Видит её всякий, кому она открыта; действует только
 * хозяин: пишет в Слове, задаёт в Ведении (там же — «Тренировка»), выполняет в Послушании —
 * нажав на задание; тренировку — по подходам или отчётом о сделанном.
 */
@Composable
fun RoomScreen(state: WillState, view: View.Room, session: WillSession) {
    // Отчёт — ответ на задание: он показывается внутри него, а не отдельной записью.
    val deeds = view.words.filter { it.kind == Word.Kind.DEED }.associateBy { it.behestId }
    val fulfilled = deeds.keys
    val shown = feedOf(view.words)
    // Своя Келья, где не пишут, — Послушание: здесь выполняют.
    val novice = view.host.isEmpty() && !view.writable
    // Писать можно и не в Слове — значит, это Ведение: здесь велят и тренировки.
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
            training, view.underways[training.id], state.choice, session,
            room = view.room,
            beginning = state.beginning == Reply.Pending,
            telling = state.telling == Reply.Pending,
            remarking = state.remarking,
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
            subtitle = view.host.ifEmpty { state.ownName },
            onBack = { session.back() },
        )

        if (view.words.isEmpty() && !state.loading) {
            Column(Modifier.weight(1f)) { Hint("Здесь пока ничего нет") }
        } else {
            LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
                items(shown, key = { it.id }) { word ->
                    WordRow(
                        reporting = word.id == fulfilling?.id && word.exercises.isEmpty(),
                        sending = sendingFulfil,
                        onReport = { report -> session.fulfil(word.id, report) },
                        word = word,
                        deed = deeds[word.id],
                        underway = view.underways[word.id],
                        onFulfil = if (novice && word.kind == Word.Kind.BEHEST && word.id !in fulfilled) {
                            { fulfillingId = word.id }
                        } else {
                            null
                        },
                        done = word.kind == Word.Kind.BEHEST && word.id in fulfilled,
                    )
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

    // Простое задание выполняют на месте: карточка раскрывается полем отчёта; «назад» — свернуть.
    BackHandler(enabled = fulfilling != null) { fulfillingId = null }
}

private fun feedOf(words: List<WordItem>): List<WordItem> {
    // Отчёт — внутри своего задания, а задание с отчётом стоит там, где пришёл отчёт.
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
private fun WordRow(
    word: WordItem,
    deed: WordItem?,
    underway: UnderwayItem?,
    onFulfil: (() -> Unit)?,
    done: Boolean,
    reporting: Boolean,
    sending: Boolean,
    onReport: (String) -> Unit,
) {
    // Каждое слово — своя карточка. Чьё оно, в комнате Уз ясно и так: задаёт Тренер, отчитывается
    // ученик. Задание, ждущее ученика, выполняют касанием самой карточки.
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(WillColors.Row)
            .let { if (onFulfil != null) it.clickable(onClick = onFulfil) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            // Выполненное — история: тише целиком, со всем, что в нём; главное в карточке — название.
            .alpha(if (done || word.kind == Word.Kind.DEED) 0.5f else 1f),
    ) {
        when (word.kind) {
            Word.Kind.BEHEST -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(word.body, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (onFulfil != null) Text("›", fontSize = 18.sp, color = WillColors.Muted)
                }
                Text(
                    listOfNotNull(if (word.exercises.isEmpty()) "задание" else "тренировка", "выполнено".takeIf { done })
                        .joinToString(" · "),
                    fontSize = 13.sp,
                    color = WillColors.Muted,
                )
                // Отчёт ученика — его слово, если он что-то сказал.
                if (deed != null && deed.body != SILENT_DEED) {
                    Text("«${deed.body}»", fontSize = 14.sp, color = WillColors.Ink, modifier = Modifier.padding(top = 4.dp))
                }
                if (reporting) ReportField(sending, onReport)
                when {
                    word.exercises.isEmpty() -> Unit
                    // Выполнение по подходам: заданное и сделанное, вживую.
                    word.efforts.isNotEmpty() || underway != null -> EffortsView(word, underway, finished = deed != null)
                    deed != null -> ComparedExercisesView(word.exercises, deed.exercises)
                    else -> ExercisesView(word.exercises)
                }
            }
            Word.Kind.DEED -> {
                // Отчёт, чьё задание здесь не видно.
                Text(word.body, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("выполнено", fontSize = 13.sp, color = WillColors.Muted)
                if (word.exercises.isNotEmpty()) ExercisesView(word.exercises)
            }
            else -> Text(word.body, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ReportField(sending: Boolean, onReport: (String) -> Unit) {
    var report by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(Modifier.fillMaxWidth().keptInSight().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = report,
            onValueChange = { report = it },
            textStyle = TextStyle(fontSize = 15.sp, color = WillColors.Ink),
            cursorBrush = SolidColor(WillColors.Accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!sending) onReport(report) }),
            modifier = Modifier.weight(1f).focusRequester(focus),
            decorationBox = { inner ->
                if (report.isEmpty()) Text("Отчёт, если есть что сказать", fontSize = 15.sp, color = WillColors.Muted)
                inner()
            },
        )
        TextButton(onClick = { onReport(report) }, enabled = !sending) { Text(if (sending) "Отправляется…" else "Выполнить") }
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
