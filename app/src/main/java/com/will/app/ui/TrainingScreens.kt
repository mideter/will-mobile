package com.will.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.ApproachItem
import com.will.app.ExerciseItem
import com.will.app.RemarkItem
import com.will.app.TrainingCourse
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun weightText(grams: Int): String {
    if (grams == 0) return "свой вес"
    val kg = grams / 1000
    val rest = grams % 1000
    if (rest == 0) return "$kg кг"
    return "$kg," + (1000 + rest).toString().substring(1).trimEnd('0') + " кг"
}

@Composable
internal fun InstructionLine(instruction: String) {
    if (instruction.isNotEmpty()) Text("«$instruction»", fontSize = 13.sp, color = WillColors.Muted, fontStyle = FontStyle.Italic)
}

@Composable
internal fun RemarkRow(remark: RemarkItem, start: androidx.compose.ui.unit.Dp) {
    Text("«${remark.body}»", fontSize = 13.sp, color = WillColors.Muted, modifier = Modifier.padding(start = start, top = 2.dp, bottom = 2.dp))
}

fun trainingSpanText(span: TrainingCourse.Span, nowNs: Long?): String {
    val start = clockText(span.startNs)
    val end = span.endNs ?: return if (nowNs == null) "начата $start" else "начата $start · идёт ${minutesText(nowNs - span.startNs)}"
    return "$start–${clockText(end)} · ${minutesText(end - span.startNs)}"
}

private fun minutesText(ns: Long): String {
    val minutes = ns / 60_000_000_000L
    return if (minutes == 0L) "меньше минуты" else if (minutes < 60) "$minutes мин" else "${minutes / 60} ч ${(minutes % 60).toString().padStart(2, '0')} мин"
}

fun clockText(serverNs: Long): String =
    Instant.ofEpochMilli(serverNs / 1_000_000).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
fun ExercisesView(exercises: List<ExerciseItem>, willed: List<ExerciseItem>? = null) {
    Column(Modifier.padding(top = 4.dp)) {
        exercises.forEachIndexed { e, exercise ->
            ExerciseName(exercise.name)
            InstructionLine(exercise.instruction)
            exercise.approaches.forEachIndexed { a, approach ->
                val asked = willed?.getOrNull(e)?.approaches?.getOrNull(a)
                val differs = willed != null && asked != approach
                Text(
                    "${a + 1}. ${weightText(approach.weightGrams)} × ${approach.repetitions}" +
                        if (differs && asked != null) "   (задано ${weightText(asked.weightGrams)} × ${asked.repetitions})" else "",
                    fontSize = 14.sp,
                    color = if (differs) WillColors.Accent else WillColors.Ink,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
fun ExerciseName(name: String) {
    Text(name, fontSize = 14.sp)
}

@Composable
fun ComparedExercisesView(willed: List<ExerciseItem>, done: List<ExerciseItem>) {
    // Упражнения сопоставляются по названию, подходы — по порядку: отчёт несёт только сделанные, без номеров.
    val unmatched = done.toMutableList()
    Column(Modifier.padding(top = 4.dp)) {
        willed.forEach { exercise ->
            val did = unmatched.firstOrNull { it.name == exercise.name }?.also { unmatched.remove(it) }
            Text(exercise.name, fontSize = 14.sp)
            InstructionLine(exercise.instruction)
            val count = maxOf(exercise.approaches.size, did?.approaches?.size ?: 0)
            for (a in 0 until count) {
                ComparedApproach(a + 1, exercise.approaches.getOrNull(a), did?.approaches?.getOrNull(a))
            }
        }
        // Упражнения, которых не задавали.
        unmatched.forEach { exercise ->
            Text(exercise.name + " — сверх заданного", fontSize = 14.sp, color = WillColors.Accent)
            exercise.approaches.forEachIndexed { a, approach -> ComparedApproach(a + 1, null, approach) }
        }
    }
}

@Composable
private fun ComparedApproach(number: Int, asked: ApproachItem?, did: ApproachItem?) {
    val (line, colour) = when {
        asked == null && did != null ->
            "+ ${weightText(did.weightGrams)} × ${did.repetitions}" to WillColors.Accent
        did == null && asked != null ->
            "${weightText(asked.weightGrams)} × ${asked.repetitions} — не сделан" to WillColors.Muted
        asked == did ->
            "${weightText(asked!!.weightGrams)} × ${asked.repetitions}  ✓" to WillColors.Ink
        asked!!.weightGrams == did!!.weightGrams ->
            "${weightText(asked.weightGrams)} × ${asked.repetitions} → × ${did.repetitions}" to WillColors.Accent
        else ->
            "${weightText(asked.weightGrams)} × ${asked.repetitions} → ${weightText(did.weightGrams)} × ${did.repetitions}" to WillColors.Accent
    }
    Text("$number. $line", fontSize = 14.sp, color = colour, modifier = Modifier.padding(start = 12.dp))
}


private const val MAX_INSTRUCTION_BYTES = 1000
private const val WEIGHT_STEP = 1_000
private const val MAX_GRAMS = 1_000_000
private const val MAX_REPETITIONS = 10_000
private const val DEFAULT_REST = 90
private const val REST_STEP = 15
private const val MAX_REST = 3_600

fun restText(seconds: Int): String =
    if (seconds == 0) "без отдыха" else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private fun secondsOf(text: String): Int? {
    val t = text.trim()
    if (t.isEmpty()) return 0
    val parts = t.split(':')
    val seconds = when (parts.size) {
        1 -> parts[0].toIntOrNull()
        2 -> parts[0].toIntOrNull()?.let { m -> parts[1].toIntOrNull()?.takeIf { it in 0..59 }?.let { m * 60 + it } }
        else -> null
    } ?: return null
    return seconds.takeIf { it in 0..MAX_REST }
}

/** Черновик подхода: вес и повторы. */
internal class ApproachDraft(grams: Int, repetitions: Int) {
    var grams by mutableStateOf(grams)
    var repetitions by mutableStateOf(repetitions)

    companion object {
        val Saver = Saver<ApproachDraft, IntArray>(
            save = { intArrayOf(it.grams, it.repetitions) },
            restore = { ApproachDraft(it[0], it[1]) },
        )
    }
}

/** Черновик упражнения: [rest] — один отдых перед каждым его подходом, как его и читают. */
private class ExerciseDraft(name: String, approaches: List<ApproachDraft>, instruction: String = "", rest: Int = DEFAULT_REST) {
    var name by mutableStateOf(name)
    var instruction by mutableStateOf(instruction)
    var rest by mutableStateOf(rest)
    val approaches: SnapshotStateList<ApproachDraft> = mutableStateListOf<ApproachDraft>().apply { addAll(approaches) }
}

private val ExerciseDraftsSaver = Saver<SnapshotStateList<ExerciseDraft>, ArrayList<Any>>(
    save = { drafts ->
        drafts.flatMapTo(ArrayList()) { draft ->
            listOf(draft.name, draft.instruction, draft.rest, draft.approaches.flatMap { listOf(it.grams, it.repetitions) }.toIntArray())
        }
    },
    restore = { saved ->
        saved.chunked(4).map { (name, instruction, rest, numbers) ->
            val approaches = (numbers as IntArray).toList().chunked(2).map { (g, r) -> ApproachDraft(g, r) }
            ExerciseDraft(name as String, approaches, instruction as String, rest as Int)
        }.toMutableStateList()
    },
)

private fun gramsOf(text: String): Int? {
    val t = text.trim().replace(',', '.')
    if (t.isEmpty()) return 0
    val kg = t.toDoubleOrNull() ?: return null
    if (kg < 0 || kg * 1000 > MAX_GRAMS) return null
    return Math.round(kg * 1000).toInt()
}

private fun kilogramsText(grams: Int) = if (grams == 0) "" else weightText(grams).removeSuffix(" кг")

private fun troubleOf(drafts: List<ExerciseDraft>): String? {
    if (drafts.isEmpty()) return "Добавьте упражнение"
    if (drafts.any { it.name.isBlank() }) return "У каждого упражнения должно быть название"
    if (drafts.any { it.approaches.isEmpty() }) return "У каждого упражнения — хотя бы один подход"
    // Сервер меряет указание байтами UTF-8, а не знаками.
    if (drafts.any { it.instruction.trim().toByteArray().size > MAX_INSTRUCTION_BYTES }) return "Указание слишком длинное"
    return null
}

private fun exercisesOf(drafts: List<ExerciseDraft>): List<ExerciseItem> =
    drafts.map { draft ->
        ExerciseItem(draft.name.trim(), draft.approaches.map { ApproachItem(it.grams, it.repetitions, draft.rest) }, draft.instruction.trim())
    }

/**
 * Редактор тренировки: Тренер составляет её — название, упражнения карточками: отдых — один
 * на упражнение, подходы — вес и повторы шагами. [names] — прежние названия упражнений для подсказки.
 * [sending] — тренировка отправлена и ждёт ответа сервера: второй раз её не задать.
 */
@Composable
fun TrainingEditor(
    heading: String,
    names: List<String>,
    sending: Boolean,
    onDone: (title: String, exercises: List<ExerciseItem>) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    var title by rememberSaveable { mutableStateOf("") }
    val drafts = rememberSaveable(saver = ExerciseDraftsSaver) {
        mutableStateListOf(ExerciseDraft("", listOf(ApproachDraft(0, 10))))
    }
    val trouble = troubleOf(drafts) ?: if (sending) "Отправляется…" else null

    Column(Modifier.fillMaxSize()) {
        Header(title = "Новая тренировка", subtitle = heading, onBack = onCancel)
        val listState = rememberLazyListState()
        LazyColumn(Modifier.weight(1f).goneUnder(listState).hiddenBeneath(listState), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)) {
            item {
                PlainField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "Название тренировки",
                    fontSize = 17.sp,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .plaque()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
            itemsIndexed(drafts) { index, draft ->
                ExerciseCard(
                    draft = draft,
                    names = names,
                    onRemove = if (drafts.size > 1) ({ drafts.removeAt(index) }) else null,
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .yetToBe()
                        .clickable { drafts.add(ExerciseDraft("", listOf(ApproachDraft(0, 10)))) }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("+ упражнение", fontSize = 16.sp, color = WillColors.Accent)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            BottomButton(text = "Задать", trouble = trouble) { onDone(title, exercisesOf(drafts)) }
        }
    }
}

@Composable
internal fun BottomButton(text: String, trouble: String?, busy: Boolean = false, onClick: () -> Unit) {
    // Всегда видна: пока что-то не так, неактивна и говорит почему; нажатое ещё в пути — говорит [text].
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (trouble != null) {
            Text(trouble, fontSize = 13.sp, color = WillColors.Muted, modifier = Modifier.padding(bottom = 6.dp))
        }
        Button(
            onClick = onClick,
            enabled = trouble == null && !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) { Text(text, fontSize = 17.sp) }
    }
}

@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Medium,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = fontSize, fontWeight = fontWeight, color = WillColors.Ink),
        cursorBrush = SolidColor(WillColors.Accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { field ->
            Box {
                if (value.isEmpty()) Text(placeholder, fontSize = fontSize, color = WillColors.Muted)
                field()
            }
        },
    )
}

@Composable
private fun ExerciseCard(draft: ExerciseDraft, names: List<String>, onRemove: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(WillColors.Row)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlainField(
                value = draft.name,
                onValueChange = { draft.name = it },
                placeholder = "Упражнение",
                fontSize = 18.sp,
                modifier = Modifier.weight(1f),
            )
            if (onRemove != null) TextButton(onClick = onRemove) { Text("✕", color = WillColors.Muted) }
        }
        // Подсказка: прежние названия, в которых есть набранное.
        val typed = draft.name.trim()
        if (typed.isNotEmpty()) {
            val like = names.filter { it != typed && it.contains(typed, ignoreCase = true) }.take(3)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                like.forEach { SuggestionChip(onClick = { draft.name = it }, label = { Text(it, fontSize = 13.sp) }) }
            }
        }
        PlainField(
            value = draft.instruction,
            onValueChange = { draft.instruction = it },
            placeholder = "Указание — как выполнять",
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 2.dp),
            fontWeight = FontWeight.Normal,
        )
        // Отдых — один на упражнение: перед каждым его подходом, как его и читают.
        Row(Modifier.padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("отдых", fontSize = 13.sp, color = WillColors.Muted, modifier = Modifier.width(52.dp))
            RestStepper(draft, Modifier.width(170.dp))
        }
        draft.approaches.forEachIndexed { index, approach ->
            ApproachRow(index + 1, approach, onRemove = if (draft.approaches.size > 1) ({ draft.approaches.removeAt(index) }) else null)
        }
        // Новый подход — как последний: обычно меняют одно число.
        TextButton(onClick = {
            val last = draft.approaches.lastOrNull()
            draft.approaches.add(ApproachDraft(last?.grams ?: 0, last?.repetitions ?: 10))
        }) { Text("+ подход") }
    }
}

@Composable
private fun ApproachRow(number: Int, approach: ApproachDraft, onRemove: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$number", fontSize = 14.sp, color = WillColors.Muted, modifier = Modifier.width(24.dp))
            WeightStepper(approach, Modifier.weight(1.3f))
            Text("×", fontSize = 16.sp, color = WillColors.Muted, modifier = Modifier.padding(horizontal = 4.dp))
            RepetitionsStepper(approach, Modifier.weight(1f))
            if (onRemove != null) {
                Text("✕", color = WillColors.Muted, modifier = Modifier.clickable(onClick = onRemove).padding(8.dp))
            } else {
                Spacer(Modifier.width(30.dp))
            }
        }
    }
}

@Composable
private fun RestStepper(draft: ExerciseDraft, modifier: Modifier) {
    Stepper(
        text = restText(draft.rest),
        onMinus = { draft.rest = (draft.rest - REST_STEP).coerceAtLeast(0) },
        onPlus = { draft.rest = (draft.rest + REST_STEP).coerceAtMost(MAX_REST) },
        exact = ExactInput("мм:сс", if (draft.rest == 0) "" else restText(draft.rest), KeyboardType.Text) {
            secondsOf(it)?.let { s -> draft.rest = s; true } ?: false
        },
        modifier = modifier,
    )
}

@Composable
internal fun WeightStepper(approach: ApproachDraft, modifier: Modifier) {
    Stepper(
        text = weightText(approach.grams),
        onMinus = { approach.grams = (approach.grams - WEIGHT_STEP).coerceAtLeast(0) },
        onPlus = { approach.grams = (approach.grams + WEIGHT_STEP).coerceAtMost(MAX_GRAMS) },
        exact = ExactInput("свой вес", kilogramsText(approach.grams), KeyboardType.Decimal) {
            gramsOf(it)?.let { grams -> approach.grams = grams; true } ?: false
        },
        modifier = modifier,
    )
}

@Composable
internal fun RepetitionsStepper(approach: ApproachDraft, modifier: Modifier) {
    Stepper(
        text = "${approach.repetitions}",
        onMinus = { approach.repetitions = (approach.repetitions - 1).coerceAtLeast(1) },
        onPlus = { approach.repetitions = (approach.repetitions + 1).coerceAtMost(MAX_REPETITIONS) },
        exact = ExactInput("1", "${approach.repetitions}", KeyboardType.Number) {
            it.trim().toIntOrNull()?.takeIf { r -> r in 1..MAX_REPETITIONS }?.let { r -> approach.repetitions = r; true } ?: false
        },
        modifier = modifier,
    )
}

/** Точный ввод числа на месте: [hint] — что видно в пустом поле; [accept] — принять, если верно. */
private class ExactInput(
    val hint: String,
    val initial: String,
    val keyboard: KeyboardType,
    val accept: (String) -> Boolean,
)

@Composable
private fun Stepper(text: String, onMinus: () -> Unit, onPlus: () -> Unit, exact: ExactInput, modifier: Modifier) {
    var typing by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(WillColors.Background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", onMinus)
        if (typing) {
            ExactField(exact) { typing = false }
        } else {
            Text(
                text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f).clickable { typing = true }.padding(vertical = 10.dp),
            )
        }
        StepButton("+", onPlus)
    }
}

@Composable
private fun RowScope.ExactField(exact: ExactInput, onDone: () -> Unit) {
    // Число само становится полем: прежнее выделено — его сразу перебивают; неверное не
    // принимается, и остаётся прежнее.
    var value by remember { mutableStateOf(TextFieldValue(exact.initial, selection = TextRange(0, exact.initial.length))) }
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val done = {
        exact.accept(value.text)
        onDone()
    }
    BasicTextField(
        value = value,
        onValueChange = { value = it },
        textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = WillColors.Ink, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(WillColors.Accent),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = exact.keyboard, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { done() }),
        modifier = Modifier
            .weight(1f)
            .padding(vertical = 10.dp)
            .focusRequester(focus)
            .onFocusChanged {
                if (focused && !it.isFocused) done()
                focused = it.isFocused
            },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (value.text.isEmpty()) Text(exact.hint, fontSize = 16.sp, color = WillColors.Muted)
                inner()
            }
        },
    )
}

@Composable
private fun StepButton(sign: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(sign, fontSize = 20.sp, color = WillColors.Accent) }
}

private fun Modifier.yetToBe(): Modifier =
    // Пунктир — то, чего ещё нет, но может быть.
    drawBehind {
        drawRoundRect(
            color = WillColors.Accent,
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
            cornerRadius = CornerRadius(12.dp.toPx()),
        )
    }.clip(RoundedCornerShape(12.dp))
