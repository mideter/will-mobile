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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.ApproachItem
import com.will.app.TrainingCourse
import com.will.app.EffortItem
import com.will.app.UnderwayItem
import com.will.app.WillSession
import com.will.app.WordItem
import kotlinx.coroutines.delay

/** Промежуток как его читают: «1:05». */
fun spanText(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private const val NANOS = 1_000_000_000L

/**
 * Выполнение тренировки. Подходы упражнения идут по порядку, упражнения — в каком угодно:
 * выбираешь упражнение (первое с несделанными подходами выбрано само) и «Приступить» — идёт
 * таймер подхода; «Завершить подход» — подтверждаешь сделанное, и идёт отдых: обратный
 * отсчёт до заданного, с вибрацией в конце. Каждый подход сразу уходит Тренеру. Экран не
 * гаснет, пока открыт; таймеры считаются от меток времени. [beginning] — к подходу приступили,
 * сервер ещё не сказал, что он идёт. [finishing] — тренировка отправлена на завершение и ждёт
 * ответа сервера; закрывает экран сам ответ.
 */
@Composable
fun TrainingRun(
    training: WordItem,
    underway: UnderwayItem?,
    session: WillSession,
    beginning: Boolean,
    finishing: Boolean,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val doing = underway?.takeIf { it.behestId == training.id }
    // Выбирают упражнение: выбор держится его, пока подходы не кончатся (см. TrainingCourse).
    // Выбор и открытые окна переживают поворот экрана.
    var chosen by rememberSaveable { mutableStateOf<Int?>(null) }
    var extraFor by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(doing) {
        // Начатый подход — где бы его ни начали — ведёт фокус; «сверх заданного» не залипает.
        if (doing != null) {
            chosen = doing.exercise
            extraFor = null
        }
    }
    val focus = TrainingCourse.focus(training, doing, chosen)
    val selected = if (doing == null) TrainingCourse.next(training, focus, extraFor) else null
    var confirming by rememberSaveable { mutableStateOf(false) }
    var closing by rememberSaveable { mutableStateOf(false) }

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

    // Отдых — перед подходом: от конца последнего сделанного до того, к какому приступаешь;
    // заданный — у выбранного следующего. До первого подхода отдыхать не от чего.
    val last = training.efforts.maxByOrNull { it.finishedAtNs }
    val resting = doing == null && last != null && selected != null
    val restWilled = selected?.let { (e, a) -> TrainingCourse.restWilled(training, e, a) } ?: 0
    val rested = last?.let { (nowNs - it.finishedAtNs) / NANOS } ?: 0
    val clock = TrainingCourse.restClock(restWilled, rested)
    val context = LocalContext.current
    LaunchedEffect(last?.finishedAtNs, restWilled, clock.over) {
        if (resting && restWilled > 0 && clock.over && rested < restWilled + 3) {
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
                    val next = TrainingCourse.nextIn(training, e)
                    for (a in 0 until TrainingCourse.count(training, e, doing)) {
                        val effort = training.efforts.firstOrNull { it.exercise == e && it.approach == a }
                        val begun = effort?.begunAtNs ?: doing?.takeIf { it.exercise == e && it.approach == a }?.begunAtNs
                        val willedRest = TrainingCourse.restWilled(training, e, a)
                        when {
                            begun != null -> RestRow(restTaken(TrainingCourse.restUntil(training.efforts, begun), willedRest), WillColors.Muted)
                            resting && selected == e to a -> RestRow(restTaken(rested, willedRest), WillColors.Accent)
                            willedRest > 0 -> RestRow("отдых ${restText(willedRest)}", WillColors.Muted)
                        }
                        RunRow(
                            number = a + 1,
                            willed = exercise.approaches.getOrNull(a),
                            effort = effort,
                            underwayFor = doing?.takeIf { it.exercise == e && it.approach == a }?.let { (nowNs - it.begunAtNs) / NANOS },
                            selected = doing == null && selected == e to a,
                            onSelect = if (doing == null && a == next) ({ chosen = e; extraFor = null }) else null,
                        )
                    }
                    // Сверх заданного — когда заданные подходы упражнения сделаны.
                    val extra = e to TrainingCourse.count(training, e)
                    if (doing == null && next == null) {
                        if (selected == extra) {
                            if (resting) RestRow(restTaken(rested, TrainingCourse.restWilled(training, e, extra.second)), WillColors.Accent)
                            RunRow(
                                number = extra.second + 1,
                                willed = null,
                                effort = null,
                                underwayFor = null,
                                selected = true,
                                onSelect = null,
                            )
                        } else {
                            TextButton(onClick = { chosen = e; extraFor = e }) { Text("+ подход сверх заданного") }
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
                resting -> {
                    // Сначала обратный отсчёт до заданного, потом секундомер дальше от отметки.
                    Text(
                        if (clock.over && restWilled > 0) "Отдых · задано ${restText(restWilled)}" else "Отдых",
                        fontSize = 13.sp,
                        color = WillColors.Muted,
                    )
                    Text(
                        spanText(clock.seconds),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (clock.over) WillColors.Ink else WillColors.Accent,
                    )
                    selected?.let { (e, a) -> Text("Далее: " + captionOf(training, e, a), fontSize = 14.sp, color = WillColors.Muted) }
                }
                else -> {
                    selected?.let { (e, a) -> Text("Далее: " + captionOf(training, e, a), fontSize = 15.sp) }
                        ?: Text("Все заданные подходы сделаны", fontSize = 15.sp, color = WillColors.Muted)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (doing != null) {
                BottomButton(text = "Завершить подход", trouble = null) { confirming = true }
            } else {
                BottomButton(
                    text = if (beginning) "Начинается…" else "Приступить",
                    trouble = if (selected == null) "Заданные подходы сделаны: завершите тренировку или добавьте подход" else null,
                    busy = beginning,
                ) { selected?.let { (e, a) -> session.beginApproach(training.id, e, a) } }
                TextButton(onClick = { closing = true }) { Text("Завершить тренировку") }
            }
        }
    }

    if (confirming && doing != null) {
        // Сделанное — как задано, если не поправить; сверх заданного — как последний подход.
        val like = TrainingCourse.proposal(training, doing.exercise, doing.approach)
        val draft = rememberSaveable(doing, saver = ApproachDraft.Saver) {
            ApproachDraft(like?.weightGrams ?: 0, like?.repetitions ?: 10)
        }
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
        var remark by rememberSaveable { mutableStateOf("") }
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
                TextButton(onClick = { session.fulfil(training.id, remark) }, enabled = !finishing) {
                    Text(if (finishing) "Отправляется…" else "Завершить")
                }
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

/**
 * Отдых, взятый перед подходом: «отдых 2:10 (задано 1:30)»; до первого усилия тренировки
 * отдыхать было не от чего — «отдохнувшим».
 */
internal fun restTaken(seconds: Long?, willed: Int): String =
    if (seconds == null) "отдохнувшим"
    else "отдых ${spanText(seconds)}" + if (willed > 0) " (задано ${restText(willed)})" else ""

/** Отдых — своей строкой над подходом: он перед подходом, а не после. */
@Composable
private fun RestRow(text: String, colour: Color) {
    Text(text, fontSize = 12.sp, color = colour, modifier = Modifier.padding(start = 48.dp, top = 4.dp))
}

/** Подход в выполнении: сделан, идёт или ещё впереди (и, может быть, выбран). */
@Composable
private fun RunRow(
    number: Int,
    willed: ApproachItem?,
    effort: EffortItem?,
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
                "${weightText(effort.weightGrams)} × ${effort.repetitions} · ${spanText(took)}" to
                    (if (changed || willed == null) WillColors.Accent else WillColors.Ink)
            }
            underwayFor != null -> "выполняется ${spanText(underwayFor)}" to WillColors.Accent
            willed != null -> "${weightText(willed.weightGrams)} × ${willed.repetitions}" to
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
            for (a in 0 until TrainingCourse.count(training, e, underway)) {
                val asked = exercise.approaches.getOrNull(a)
                val effort = training.efforts.firstOrNull { it.exercise == e && it.approach == a }
                val begun = effort?.begunAtNs ?: underway?.takeIf { it.exercise == e && it.approach == a }?.begunAtNs
                val willedRest = TrainingCourse.restWilled(training, e, a)
                when {
                    begun != null -> FeedRestLine(restTaken(TrainingCourse.restUntil(training.efforts, begun), willedRest))
                    asked != null && willedRest > 0 && !finished -> FeedRestLine("отдых ${restText(willedRest)}")
                }
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
                        "$head · ${spanText(took)}" to
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

@Composable
private fun FeedRestLine(text: String) {
    Text(text, fontSize = 12.sp, color = WillColors.Muted, modifier = Modifier.padding(start = 28.dp))
}
