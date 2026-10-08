package com.will.app.ui

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.ApproachItem
import com.will.app.EffortItem
import com.will.app.UnderwayItem
import com.will.app.WillSession
import com.will.app.WordItem
import kotlinx.coroutines.delay

/** Промежуток как его читают: «1:05». */
fun spanText(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private const val NANOS = 1_000_000_000L

/** Сколько подходов у упражнения: заданные и сделанные сверх них. */
private fun approachCount(training: WordItem, exercise: Int): Int =
    maxOf(
        training.exercises[exercise].approaches.size,
        (training.efforts.filter { it.exercise == exercise }.maxOfOrNull { it.approach } ?: -1) + 1,
    )

/** Следующий подход упражнения по порядку: первый заданный, ещё не сделанный; null — заданные сделаны. */
private fun nextIn(training: WordItem, exercise: Int): Int? =
    training.exercises[exercise].approaches.indices.firstOrNull { a ->
        training.efforts.none { it.exercise == exercise && it.approach == a }
    }

/** Упражнение, к которому переходят сами: первое, где остались заданные подходы. */
private fun nextOf(training: WordItem): Pair<Int, Int>? {
    training.exercises.indices.forEach { e -> nextIn(training, e)?.let { return e to it } }
    return null
}

/** Отдых перед этим усилием: от конца предыдущего по времени до его начала. */
internal fun restBefore(efforts: List<EffortItem>, effort: EffortItem): Long? {
    val before = efforts.filter { it.finishedAtNs <= effort.begunAtNs }.maxByOrNull { it.finishedAtNs } ?: return null
    return (effort.begunAtNs - before.finishedAtNs) / NANOS
}

/**
 * Выполнение тренировки. Подходы упражнения идут по порядку, упражнения — в каком угодно:
 * выбираешь упражнение (первое с несделанными подходами выбрано само) и «Приступить» — идёт
 * таймер подхода; «Завершить подход» — подтверждаешь сделанное, и идёт отдых: обратный
 * отсчёт до заданного, с вибрацией в конце. Каждый подход сразу уходит Тренеру. Экран не
 * гаснет, пока открыт; таймеры считаются от меток времени.
 */
@Composable
fun TrainingRun(training: WordItem, underway: UnderwayItem?, session: WillSession, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val doing = underway?.takeIf { it.behestId == training.id }
    var chosen by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val selected = chosen?.takeIf { (e, a) -> training.efforts.none { it.exercise == e && it.approach == a } } ?: nextOf(training)
    var confirming by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }

    // Часы: раз в четверть секунды.
    var nowNs by remember { mutableLongStateOf(System.currentTimeMillis() * 1_000_000) }
    LaunchedEffect(Unit) {
        while (true) {
            nowNs = System.currentTimeMillis() * 1_000_000
            delay(250)
        }
    }
    // Экран не гаснет, пока идёт выполнение.
    val screen = LocalView.current
    DisposableEffect(Unit) {
        screen.keepScreenOn = true
        onDispose { screen.keepScreenOn = false }
    }

    // Отдых после последнего сделанного подхода — сколько задано после него.
    val last = training.efforts.maxByOrNull { it.finishedAtNs }
    val restWilled = last?.let { training.exercises.getOrNull(it.exercise)?.approaches?.getOrNull(it.approach)?.restSeconds } ?: 0
    val rested = last?.let { (nowNs - it.finishedAtNs) / NANOS } ?: 0
    val restLeft = if (doing == null && last != null) restWilled - rested else 0
    val context = LocalContext.current
    LaunchedEffect(last?.finishedAtNs, restLeft <= 0) {
        if (last != null && doing == null && restWilled > 0 && restLeft <= 0 && rested < restWilled + 3) {
            context.getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Header(title = "Выполнение", subtitle = training.body, onBack = onClose)
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(training.exercises) { e, exercise ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(WillColors.Row)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(exercise.name, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    // Нажимается только следующий подход упражнения: подходы идут по порядку.
                    val next = nextIn(training, e)
                    for (a in 0 until approachCount(training, e)) {
                        val effort = training.efforts.firstOrNull { it.exercise == e && it.approach == a }
                        RunRow(
                            number = a + 1,
                            willed = exercise.approaches.getOrNull(a),
                            effort = effort,
                            restBefore = effort?.let { restBefore(training.efforts, it) },
                            underwayFor = doing?.takeIf { it.exercise == e && it.approach == a }?.let { (nowNs - it.begunAtNs) / NANOS },
                            selected = doing == null && selected == e to a,
                            onSelect = if (doing == null && a == next) ({ chosen = e to a }) else null,
                        )
                    }
                    // Сверх заданного — когда заданные подходы упражнения сделаны.
                    val extra = e to approachCount(training, e)
                    if (doing == null && next == null) {
                        if (selected == extra) {
                            RunRow(
                                number = extra.second + 1,
                                willed = null,
                                effort = null,
                                restBefore = null,
                                underwayFor = null,
                                selected = true,
                                onSelect = null,
                            )
                        } else {
                            TextButton(onClick = { chosen = extra }) { Text("+ подход сверх заданного") }
                        }
                    }
                }
            }
        }

        // Пульт внизу: таймер и главное действие.
        Column(
            Modifier.fillMaxWidth().background(WillColors.Composer).padding(16.dp, 10.dp, 16.dp, 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                doing != null -> {
                    Text("Подход", fontSize = 13.sp, color = WillColors.Muted)
                    Text(spanText((nowNs - doing.begunAtNs) / NANOS), fontSize = 48.sp, fontWeight = FontWeight.Medium)
                    Text(captionOf(training, doing.exercise, doing.approach), fontSize = 14.sp, color = WillColors.Muted)
                }
                restLeft > 0 -> {
                    Text("Отдых", fontSize = 13.sp, color = WillColors.Muted)
                    Text(spanText(restLeft), fontSize = 48.sp, fontWeight = FontWeight.Medium, color = WillColors.Accent)
                    selected?.let { (e, a) -> Text("Далее: " + captionOf(training, e, a), fontSize = 14.sp, color = WillColors.Muted) }
                }
                else -> {
                    if (last != null && restWilled > 0) Text("Отдых окончен", fontSize = 13.sp, color = WillColors.Accent)
                    selected?.let { (e, a) -> Text("Далее: " + captionOf(training, e, a), fontSize = 15.sp) }
                        ?: Text("Все заданные подходы сделаны", fontSize = 15.sp, color = WillColors.Muted)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (doing != null) {
                BottomButton(text = "Завершить подход", trouble = null) { confirming = true }
            } else {
                BottomButton(
                    text = "Приступить",
                    trouble = if (selected == null) "Заданные подходы сделаны: завершите тренировку или добавьте подход" else null,
                ) { selected?.let { (e, a) -> session.beginApproach(training.id, e, a) } }
                TextButton(onClick = { closing = true }) { Text("Завершить тренировку") }
            }
        }
    }

    if (confirming && doing != null) {
        // Сделанное — как задано, если не поправить; сверх заданного — как последний подход.
        val asked = training.exercises[doing.exercise].approaches.getOrNull(doing.approach)
        val like = asked ?: training.efforts.lastOrNull { it.exercise == doing.exercise }?.let { ApproachItem(it.weightGrams, it.repetitions) }
        val draft = remember(doing) { ApproachDraft(like?.weightGrams ?: 0, like?.repetitions ?: 10) }
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Что сделано") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeightStepper(draft, Modifier.weight(1.3f))
                    Text("×", fontSize = 16.sp, color = WillColors.Muted, modifier = Modifier.padding(horizontal = 4.dp))
                    RepetitionsStepper(draft, Modifier.weight(1f))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    session.finishApproach(training.id, draft.grams, draft.repetitions)
                    confirming = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Ещё не закончил") } },
        )
    }

    if (closing) {
        var remark by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { closing = false },
            title = { Text("Завершить тренировку") },
            text = {
                Column {
                    Text("Сделано подходов: ${training.efforts.size}", color = WillColors.Muted)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = remark,
                        onValueChange = { remark = it },
                        placeholder = { Text("Замечание (необязательно)") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    session.fulfil(training.id, remark)
                    closing = false
                    onClose()
                }) { Text("Завершить") }
            },
            dismissButton = { TextButton(onClick = { closing = false }) { Text("Отмена") } },
        )
    }
}

/** «Присед · подход 2 · 100 кг × 5». */
private fun captionOf(training: WordItem, exercise: Int, approach: Int): String {
    val name = training.exercises[exercise].name
    val asked = training.exercises[exercise].approaches.getOrNull(approach)
    return "$name · подход ${approach + 1}" +
        (asked?.let { " · ${weightText(it.weightGrams)} × ${it.repetitions}" } ?: " · сверх заданного")
}

/** Подход в выполнении: сделан, идёт или ещё впереди (и, может быть, выбран). */
@Composable
private fun RunRow(
    number: Int,
    willed: ApproachItem?,
    effort: EffortItem?,
    restBefore: Long?,
    underwayFor: Long?,
    selected: Boolean,
    onSelect: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) WillColors.Background else WillColors.Row)
            .let { if (onSelect != null) it.clickable(onClick = onSelect) else it }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when {
                effort != null -> "✓"
                underwayFor != null -> "▶"
                selected -> "●"
                else -> "○"
            },
            color = if (effort != null || underwayFor != null || selected) WillColors.Accent else WillColors.Muted,
            modifier = Modifier.width(22.dp),
        )
        Text("$number", fontSize = 14.sp, color = WillColors.Muted, modifier = Modifier.width(20.dp))
        val (line, colour) = when {
            effort != null -> {
                val changed = willed != null && (willed.weightGrams != effort.weightGrams || willed.repetitions != effort.repetitions)
                val took = (effort.finishedAtNs - effort.begunAtNs) / NANOS
                ("${weightText(effort.weightGrams)} × ${effort.repetitions} · ${spanText(took)}" +
                    (restBefore?.let { " · отдых ${spanText(it)}" } ?: "")) to
                    (if (changed || willed == null) WillColors.Accent else WillColors.Ink)
            }
            underwayFor != null -> "выполняется ${spanText(underwayFor)}" to WillColors.Accent
            willed != null -> ("${weightText(willed.weightGrams)} × ${willed.repetitions}" +
                (if (willed.restSeconds != 0) " · отдых ${restText(willed.restSeconds)}" else "")) to
                (if (selected) WillColors.Ink else WillColors.Muted)
            else -> "сверх заданного" to WillColors.Accent
        }
        Text(line, fontSize = 15.sp, color = colour)
    }
}

/**
 * Ход тренировки в ленте: строка на подход — заданное и сделанное, длительность и отдых
 * перед подходом; идущий подход — «выполняется»; сверх заданного — «+». У выполненной
 * тренировки несделанные — «не сделан».
 */
@Composable
fun EffortsView(training: WordItem, underway: UnderwayItem?, finished: Boolean) {
    Column(Modifier.padding(top = 4.dp)) {
        training.exercises.forEachIndexed { e, exercise ->
            Text(exercise.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            for (a in 0 until approachCount(training, e)) {
                val asked = exercise.approaches.getOrNull(a)
                val effort = training.efforts.firstOrNull { it.exercise == e && it.approach == a }
                val (line, colour) = when {
                    effort != null -> {
                        val did = "${effort.repetitions}"
                        val head = when {
                            asked == null -> "+ ${weightText(effort.weightGrams)} × $did"
                            asked.weightGrams == effort.weightGrams && asked.repetitions == effort.repetitions ->
                                "${weightText(asked.weightGrams)} × ${asked.repetitions} ✓"
                            asked.weightGrams == effort.weightGrams ->
                                "${weightText(asked.weightGrams)} × ${asked.repetitions} → × $did"
                            else -> "${weightText(asked.weightGrams)} × ${asked.repetitions} → ${weightText(effort.weightGrams)} × $did"
                        }
                        val took = (effort.finishedAtNs - effort.begunAtNs) / NANOS
                        val rest = restBefore(training.efforts, effort)?.let { " · отдых ${spanText(it)}" } ?: ""
                        ("$head · ${spanText(took)}$rest") to
                            (if (head.endsWith("✓")) WillColors.Ink else WillColors.Accent)
                    }
                    underway != null && underway.exercise == e && underway.approach == a ->
                        "▶ выполняется…" to WillColors.Accent
                    asked != null -> ("${weightText(asked.weightGrams)} × ${asked.repetitions}" +
                        if (finished) " — не сделан" else "") to WillColors.Muted
                    else -> "+ сверх заданного…" to WillColors.Accent
                }
                Text("${a + 1}. $line", fontSize = 14.sp, color = colour, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}
