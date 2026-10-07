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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.ExerciseItem
import com.will.app.View
import com.will.app.WillSession
import com.will.app.WillState
import com.will.app.WordItem
import will.v1.MessengerOuterClass.Word

/**
 * Комната: лента слов отражаемого места. Поле ввода — только там, где можно писать
 * (своя Келья; Ведение — у Тренера; там же — «Тренировка»). Послушник исполняет Веление,
 * нажав на него самого; тренировку — отчётом о сделанном.
 */
@Composable
fun RoomScreen(state: WillState, view: View.Room, session: WillSession) {
    val fulfilled = view.words.filter { it.kind == Word.Kind.DEED }.map { it.behestId }.toSet()
    // Своя комната, где писать нельзя, — Послушание: здесь исполняют.
    val novice = view.host.isEmpty() && !view.writable
    // Писать можно и не в Келье — значит, это Ведение: здесь велят и тренировки.
    val trainer = view.writable && view.room != WillSession.CELL
    var fulfilling by remember { mutableStateOf<WordItem?>(null) }
    var composing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val names = view.words.flatMap { w -> w.exercises.map { it.name } }.distinct()

    if (composing) {
        TrainingEditor(
            report = false,
            heading = view.room,
            initial = emptyList(),
            names = names,
            onDone = { title, exercises ->
                session.train(title, exercises)
                composing = false
            },
            onCancel = { composing = false },
        )
        return
    }
    fulfilling?.takeIf { it.exercises.isNotEmpty() }?.let { training ->
        TrainingEditor(
            report = true,
            heading = training.body,
            initial = training.exercises,
            names = names,
            onDone = { report, done ->
                session.fulfil(training.id, report, done)
                fulfilling = null
            },
            onCancel = { fulfilling = null },
        )
        return
    }

    LaunchedEffect(view.words.size) {
        if (view.words.isNotEmpty()) listState.scrollToItem(view.words.size - 1)
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
                items(view.words, key = { it.id }) { word ->
                    WordRow(
                        word = word,
                        willed = if (word.kind == Word.Kind.DEED) {
                            view.words.firstOrNull { it.id == word.behestId }?.exercises
                        } else {
                            null
                        },
                        onFulfil = if (novice && word.kind == Word.Kind.BEHEST && word.id !in fulfilled) {
                            { fulfilling = word }
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
        var report by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { fulfilling = null },
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
                TextButton(onClick = {
                    session.fulfil(behest.id, report)
                    fulfilling = null
                }) { Text("Выполнить") }
            },
            dismissButton = { TextButton(onClick = { fulfilling = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun WordRow(word: WordItem, willed: List<ExerciseItem>?, onFulfil: (() -> Unit)?, done: Boolean) {
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
                Text(if (word.exercises.isEmpty()) "Задание" else "Тренировка", fontSize = 12.sp, color = WillColors.Accent)
                Text(word.body, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (word.exercises.isNotEmpty()) ExercisesView(word.exercises)
                if (done) Text("выполнено", fontSize = 12.sp, color = WillColors.Muted)
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
                Text("✓ Выполнено", fontSize = 12.sp, color = WillColors.Accent)
                Text(word.body, fontSize = 15.sp)
                if (word.exercises.isNotEmpty()) ExercisesView(word.exercises, willed)
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
