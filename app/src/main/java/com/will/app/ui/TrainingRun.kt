package com.will.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.will.app.ApproachItem
import com.will.app.Choice
import com.will.app.TrainingCourse
import com.will.app.EffortItem
import com.will.app.UnderwayItem
import com.will.app.WillSession
import com.will.app.WordItem
import kotlinx.coroutines.delay

fun spanText(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private const val NANOS = 1_000_000_000L

/**
 * Выполнение тренировки. Подходы упражнения идут по порядку, упражнения — в каком угодно:
 * выбираешь упражнение (первое с несделанными подходами выбрано само) и «Приступить» — идёт
 * таймер подхода; «Завершить подход» — подтверждаешь сделанное, и идёт отдых: обратный
 * отсчёт до заданного. Каждый подход сразу уходит Тренеру. Экран не гаснет, пока открыт;
 * таймеры считаются от меток времени. Погашенный экран ведёт [com.will.app.TrainingService]:
 * уведомление с таймером и вибрация в конце отдыха. Выбор [choice] — в сессии, его видит и оно. [beginning] — к подходу приступили,
 * сервер ещё не сказал, что он идёт. [finishing] — тренировка отправлена на завершение и ждёт
 * ответа сервера; закрывает экран сам ответ.
 */
@Composable
fun TrainingRun(
    training: WordItem,
    underway: UnderwayItem?,
    choice: Choice,
    session: WillSession,
    beginning: Boolean,
    finishing: Boolean,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val doing = underway?.takeIf { it.behestId == training.id }
    // Выбирают упражнение: выбор держится его, пока подходы не кончатся (см. TrainingCourse).
    val selected = TrainingCourse.selected(training, doing, choice)
    var confirming by rememberSaveable { mutableStateOf(false) }
    var closing by rememberSaveable { mutableStateOf(false) }
    var remarkingOn by rememberSaveable { mutableStateOf<Int?>(null) }

    // Часы сервера — метки подходов ставит он: раз в четверть секунды.
    var nowNs by remember { mutableLongStateOf(session.clock.nowNs()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowNs = session.clock.nowNs()
            delay(250)
        }
    }
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
    // Без уведомлений погашенный экран не покажет таймер и не даст «Приступить»; вибрация будет и так.
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(Modifier.fillMaxSize()) {
        val span = TrainingCourse.span(training, doing)
        Header(
            title = "Выполнение",
            subtitle = training.body + (span?.let { " · " + trainingSpanText(it, nowNs) } ?: ""),
            onBack = onClose,
        )
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(exercise.name, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { remarkingOn = if (remarkingOn == e) null else e }) {
                            Text("заметка", fontSize = 13.sp, color = WillColors.Muted)
                        }
                    }
                    InstructionLine(exercise.instruction)
                    if (remarkingOn == e) {
                        RemarkField(onSay = { session.sayRemark(training.id, e, it); remarkingOn = null }, key = "${training.id}:$e")
                    }
                    val remarks = TrainingCourse.remarksByApproachesDone(training, e)
                    // Нажимается только следующий подход упражнения: подходы идут по порядку.
                    val next = TrainingCourse.nextWilledIn(training, e)
                    val count = TrainingCourse.approachCount(training, e, doing)
                    for (a in 0 until count) {
                        remarks[a]?.forEach { RemarkRow(it, start = 28.dp) }
                        val effort = training.efforts.firstOrNull { it.exercise == e && it.approach == a }
                        val begun = effort?.begunAtNs ?: doing?.takeIf { it.exercise == e && it.approach == a }?.begunAtNs
                        val willedRest = TrainingCourse.restWilled(training, e, a)
                        when {
                            // Перед первым сделанным подходом отдыхать было не после чего: строки нет.
                            begun != null -> TrainingCourse.restTakenBefore(training.efforts, begun)?.let {
                                RestRow(restTaken(it, willedRest), WillColors.Muted)
                            }
                            resting && selected == e to a -> RestRow(restTaken(rested, willedRest), WillColors.Accent)
                            willedRest > 0 -> RestRow("отдых ${restText(willedRest)}", WillColors.Muted)
                        }
                        RunRow(
                            number = a + 1,
                            willed = exercise.approaches.getOrNull(a),
                            effort = effort,
                            underwayFor = doing?.takeIf { it.exercise == e && it.approach == a }?.let { (nowNs - it.begunAtNs) / NANOS },
                            selected = doing == null && selected == e to a,
                            onSelect = if (doing == null && a == next) ({ session.choose(training.id, e) }) else null,
                        )
                    }
                    // Сверх заданного — когда заданные подходы упражнения сделаны.
                    val extra = e to TrainingCourse.approachCount(training, e)
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
                            TextButton(onClick = { session.choose(training.id, e, extra = e) }) { Text("+ подход сверх заданного") }
                        }
                    }
                    remarks.filterKeys { it >= count }.values.flatten().forEach { RemarkRow(it, start = 28.dp) }
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
                    trouble = if (selected == null) "Завершите тренировку или добавьте подход" else null,
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

internal fun captionOf(training: WordItem, exercise: Int, approach: Int): String {
    val name = training.exercises[exercise].name
    val asked = training.exercises[exercise].approaches.getOrNull(approach)
    return "$name · подход ${approach + 1}" +
        (asked?.let { " · ${weightText(it.weightGrams)} × ${it.repetitions}" } ?: " · сверх заданного")
}

internal fun restTaken(seconds: Long, willed: Int): String =
    "отдых ${spanText(seconds)}" + if (willed > 0) " (задано ${restText(willed)})" else ""

@Composable
private fun RestRow(text: String, colour: Color) {
    // Своей строкой над подходом: отдых перед подходом, а не после.
    Text(text, fontSize = 12.sp, color = colour, modifier = Modifier.padding(start = 48.dp, top = 4.dp))
}

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

@Composable
private fun RemarkField(onSay: (String) -> Unit, key: String) {
    var text by rememberSaveable(key) { mutableStateOf("") }
    // Сервер меряет речение байтами UTF-8, а не знаками.
    val fits = text.isNotBlank() && text.trim().toByteArray().size <= MAX_REMARK_BYTES
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val say = { if (fits) { onSay(text.trim()); text = "" } }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 28.dp, top = 2.dp)) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(fontSize = 14.sp, color = WillColors.Ink),
            cursorBrush = SolidColor(WillColors.Accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { say() }),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .drawBehind { drawLine(WillColors.Divider, Offset(0f, size.height + 4.dp.toPx()), Offset(size.width, size.height + 4.dp.toPx()), 1.dp.toPx()) },
            decorationBox = { inner ->
                if (text.isEmpty()) Text("Что сказать о подходах…", fontSize = 14.sp, color = WillColors.Muted)
                inner()
            },
        )
        TextButton(onClick = say, enabled = fits) { Text("↑", fontSize = 18.sp) }
    }
}

private const val MAX_REMARK_BYTES = 4096

@Composable
fun EffortsView(training: WordItem, underway: UnderwayItem?, finished: Boolean) {
    Column(Modifier.padding(top = 4.dp)) {
        // До завершения у тренировки нет конца, сколько бы подходов ни было закончено.
        TrainingCourse.span(training, underway)?.let { span ->
            Text(trainingSpanText(if (finished) span else span.copy(endNs = null), null), fontSize = 12.sp, color = WillColors.Muted)
        }
        training.exercises.forEachIndexed { e, exercise ->
            Text(exercise.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            InstructionLine(exercise.instruction)
            val remarks = TrainingCourse.remarksByApproachesDone(training, e)
            val count = TrainingCourse.approachCount(training, e, underway)
            for (a in 0 until count) {
                remarks[a]?.forEach { RemarkRow(it, start = 12.dp) }
                val asked = exercise.approaches.getOrNull(a)
                val effort = training.efforts.firstOrNull { it.exercise == e && it.approach == a }
                val begun = effort?.begunAtNs ?: underway?.takeIf { it.exercise == e && it.approach == a }?.begunAtNs
                val willedRest = TrainingCourse.restWilled(training, e, a)
                when {
                    begun != null -> TrainingCourse.restTakenBefore(training.efforts, begun)?.let { FeedRestLine(restTaken(it, willedRest)) }
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
            remarks.filterKeys { it >= count }.values.flatten().forEach { RemarkRow(it, start = 12.dp) }
        }
    }
}

@Composable
private fun FeedRestLine(text: String) {
    Text(text, fontSize = 12.sp, color = WillColors.Muted, modifier = Modifier.padding(start = 28.dp))
}
