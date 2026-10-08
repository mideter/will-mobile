package com.will.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.ApproachItem
import com.will.app.ExerciseItem

/** Вес как его читают: «60 кг», «62,5 кг» или «свой вес». */
fun weightText(grams: Int): String {
    if (grams == 0) return "свой вес"
    val kg = grams / 1000
    val rest = grams % 1000
    if (rest == 0) return "$kg кг"
    return "$kg," + (1000 + rest).toString().substring(1).trimEnd('0') + " кг"
}

/**
 * Упражнения строками: название, под ним подходы. Если дано [willed], подходы, сделанные
 * не так, как велено, выделяются.
 */
@Composable
fun ExercisesView(exercises: List<ExerciseItem>, willed: List<ExerciseItem>? = null) {
    Column(Modifier.padding(top = 4.dp)) {
        exercises.forEachIndexed { e, exercise ->
            Text(exercise.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            exercise.approaches.forEachIndexed { a, approach ->
                val asked = willed?.getOrNull(e)?.approaches?.getOrNull(a)
                val differs = willed != null && asked != approach
                Text(
                    "${a + 1}. ${weightText(approach.weightGrams)} × ${approach.repetitions}" +
                        (if (approach.restSeconds != 0) " · отдых ${restText(approach.restSeconds)}" else "") +
                        if (differs && asked != null) "   (задано ${weightText(asked.weightGrams)} × ${asked.repetitions})" else "",
                    fontSize = 14.sp,
                    color = if (differs) WillColors.Accent else WillColors.Ink,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/**
 * Заданное и сделанное в одной строке на подход: «100 кг × 5 ✓», «100 кг × 5 → × 3»,
 * «свой вес × 8 — не сделан», «+ 100 кг × 3» сверх заданного. Упражнения сопоставляются
 * по названию, подходы — по порядку: отчёт несёт только сделанные, без номеров.
 */
@Composable
fun ComparedExercisesView(willed: List<ExerciseItem>, done: List<ExerciseItem>) {
    val unmatched = done.toMutableList()
    Column(Modifier.padding(top = 4.dp)) {
        willed.forEach { exercise ->
            val did = unmatched.firstOrNull { it.name == exercise.name }?.also { unmatched.remove(it) }
            Text(exercise.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            val count = maxOf(exercise.approaches.size, did?.approaches?.size ?: 0)
            for (a in 0 until count) {
                ComparedApproach(a + 1, exercise.approaches.getOrNull(a), did?.approaches?.getOrNull(a))
            }
        }
        // Упражнения, которых не задавали.
        unmatched.forEach { exercise ->
            Text(exercise.name + " — сверх заданного", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = WillColors.Accent)
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


/** Шаг веса — 2,5 кг; шаг повторов — 1. */
private const val WEIGHT_STEP = 2_500
private const val MAX_GRAMS = 1_000_000
private const val MAX_REPETITIONS = 10_000
/** Отдых по умолчанию — полторы минуты; шаг — 15 секунд; не больше часа. */
private const val DEFAULT_REST = 90
private const val REST_STEP = 15
private const val MAX_REST = 3_600

/** Отдых как его читают: «1:30»; без отдыха — «без отдыха». */
fun restText(seconds: Int): String =
    if (seconds == 0) "без отдыха" else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/** Секунды из набранного: «90», «1:30». Null, если это не отдых. */
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

/**
 * Черновик подхода. [willed] — заданное (в отчёте); [marked] — отмечен сделанным;
 * [editing] — числа открыты для правки шагами.
 */
private class ApproachDraft(
    grams: Int,
    repetitions: Int,
    val willed: ApproachItem?,
    marked: Boolean,
    editing: Boolean,
    rest: Int = willed?.restSeconds ?: DEFAULT_REST,
) {
    var grams by mutableStateOf(grams)
    var repetitions by mutableStateOf(repetitions)
    var rest by mutableStateOf(rest)
    var marked by mutableStateOf(marked)
    var editing by mutableStateOf(editing)

    val item get() = ApproachItem(grams, repetitions, rest)
    val changed get() = willed != null && willed != item
}

/** Черновик упражнения. */
private class ExerciseDraft(name: String, approaches: List<ApproachDraft>) {
    var name by mutableStateOf(name)
    val approaches: SnapshotStateList<ApproachDraft> = mutableStateListOf<ApproachDraft>().apply { addAll(approaches) }
}

/** Граммы из набранного: «62,5» → 62500; пусто или 0 — свой вес. Null, если это не вес. */
private fun gramsOf(text: String): Int? {
    val t = text.trim().replace(',', '.')
    if (t.isEmpty()) return 0
    val kg = t.toDoubleOrNull() ?: return null
    if (kg < 0 || kg * 1000 > MAX_GRAMS) return null
    return Math.round(kg * 1000).toInt()
}

/** Вес, как его набирают: «62,5»; свой вес — пусто. */
private fun kilogramsText(grams: Int) = if (grams == 0) "" else weightText(grams).removeSuffix(" кг")

/** Что не так в черновиках; null, если всё можно отправить. */
private fun troubleOf(drafts: List<ExerciseDraft>, report: Boolean): String? {
    if (report) {
        if (drafts.none { d -> d.approaches.any { it.marked } }) return "Отметьте сделанные подходы"
        return null
    }
    if (drafts.isEmpty()) return "Добавьте упражнение"
    if (drafts.any { it.name.isBlank() }) return "У каждого упражнения должно быть название"
    if (drafts.any { it.approaches.isEmpty() }) return "У каждого упражнения — хотя бы один подход"
    return null
}

/** Упражнения из черновиков: в отчёте — только отмеченные подходы. */
private fun exercisesOf(drafts: List<ExerciseDraft>, report: Boolean): List<ExerciseItem> =
    drafts.mapNotNull { draft ->
        val approaches = draft.approaches.filter { !report || it.marked }.map { it.item }
        if (approaches.isEmpty()) null else ExerciseItem(draft.name.trim(), approaches)
    }

/**
 * Редактор тренировки. Тренер составляет её ([report] = false): заголовок, упражнения
 * карточками, подходы — вес и повторы шагами. Послушник отчитывается ([report] = true):
 * отмечает подходы, сделанные как задано, правит сделанное иначе и пишет отчёт.
 * [names] — прежние названия упражнений для подсказки.
 */
@Composable
fun TrainingEditor(
    report: Boolean,
    heading: String,
    initial: List<ExerciseItem>,
    names: List<String>,
    onDone: (text: String, exercises: List<ExerciseItem>) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    var text by remember { mutableStateOf("") }
    val drafts = remember {
        mutableStateListOf<ExerciseDraft>().apply {
            if (initial.isEmpty()) {
                add(ExerciseDraft("", listOf(ApproachDraft(0, 10, null, marked = true, editing = true))))
            } else {
                addAll(initial.map { e ->
                    ExerciseDraft(e.name, e.approaches.map { ApproachDraft(it.weightGrams, it.repetitions, it, marked = false, editing = false) })
                })
            }
        }
    }
    val trouble = troubleOf(drafts, report)

    Column(Modifier.fillMaxSize()) {
        Header(title = if (report) "Отчёт" else "Новая тренировка", subtitle = heading, onBack = onCancel)
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)) {
            item {
                if (report) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            drafts.forEach { d -> d.approaches.forEach { if (!it.marked) { it.marked = true } } }
                        }) { Text("✓ Всё как задано") }
                    }
                } else {
                    PlainField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = "Тренировка",
                        fontSize = 22.sp,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp),
                    )
                }
            }
            itemsIndexed(drafts) { index, draft ->
                ExerciseCard(
                    number = index + 1,
                    draft = draft,
                    report = report,
                    names = names,
                    onRemove = if (!report && drafts.size > 1) ({ drafts.removeAt(index) }) else null,
                )
            }
            if (!report) {
                item {
                    DashedCard(onClick = {
                        drafts.add(ExerciseDraft("", listOf(ApproachDraft(0, 10, null, marked = true, editing = true))))
                    }) { Text("+ упражнение", color = WillColors.Accent, fontSize = 16.sp) }
                }
            }
            if (report) {
                item {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp),
                        placeholder = { Text("Отчёт (необязательно)") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
            }
        }
        // Кнопка всегда видна внизу; пока что-то не так — неактивна, и сказано почему.
        Column(Modifier.fillMaxWidth().background(WillColors.Composer).padding(16.dp, 8.dp, 16.dp, 12.dp)) {
            if (trouble != null) {
                Text(trouble, fontSize = 13.sp, color = WillColors.Muted, modifier = Modifier.padding(bottom = 6.dp))
            }
            Button(
                onClick = { onDone(text, exercisesOf(drafts, report)) },
                enabled = trouble == null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text(if (report) "Отчитаться" else "Задать", fontSize = 17.sp) }
        }
    }
}

/** Поле без рамки: только текст и, пока пусто, подсказка. */
@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Medium, color = WillColors.Ink),
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

/** Карточка упражнения: номер, название, подходы. */
@Composable
private fun ExerciseCard(number: Int, draft: ExerciseDraft, report: Boolean, names: List<String>, onRemove: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(WillColors.Row)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$number", fontSize = 15.sp, color = WillColors.Muted, modifier = Modifier.width(24.dp))
            if (report) {
                Text(draft.name, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            } else {
                PlainField(
                    value = draft.name,
                    onValueChange = { draft.name = it },
                    placeholder = "Упражнение",
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            if (onRemove != null) TextButton(onClick = onRemove) { Text("✕", color = WillColors.Muted) }
        }
        // Подсказка: прежние названия, в которых есть набранное.
        val typed = draft.name.trim()
        if (!report && typed.isNotEmpty()) {
            val like = names.filter { it != typed && it.contains(typed, ignoreCase = true) }.take(3)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                like.forEach { SuggestionChip(onClick = { draft.name = it }, label = { Text(it, fontSize = 13.sp) }) }
            }
        }
        Spacer(Modifier.height(4.dp))
        draft.approaches.forEachIndexed { index, approach ->
            if (report) {
                ReportRow(index + 1, approach, onRemove = if (approach.willed == null) ({ draft.approaches.removeAt(index) }) else null)
            } else {
                ApproachRow(index + 1, approach, onRemove = if (draft.approaches.size > 1) ({ draft.approaches.removeAt(index) }) else null)
            }
        }
        // Новый подход — как последний: обычно меняют одно число.
        TextButton(onClick = {
            val last = draft.approaches.lastOrNull()
            draft.approaches.add(
                ApproachDraft(last?.grams ?: 0, last?.repetitions ?: 10, null, marked = true, editing = true, rest = last?.rest ?: DEFAULT_REST),
            )
        }) { Text(if (report) "+ подход сверх заданного" else "+ подход") }
    }
}

/** Подход в редакторе Тренера: номер, вес и повторы шагами. */
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
        // Отдых после подхода — второй строкой.
        Row(Modifier.padding(start = 24.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("отдых", fontSize = 13.sp, color = WillColors.Muted, modifier = Modifier.width(52.dp))
            RestStepper(approach, Modifier.width(170.dp))
        }
    }
}

@Composable
private fun RestStepper(approach: ApproachDraft, modifier: Modifier) {
    Stepper(
        text = restText(approach.rest),
        onMinus = { approach.rest = (approach.rest - REST_STEP).coerceAtLeast(0) },
        onPlus = { approach.rest = (approach.rest + REST_STEP).coerceAtMost(MAX_REST) },
        exact = ExactInput("Отдых", "секунды или мм:сс; пусто — без отдыха", if (approach.rest == 0) "" else restText(approach.rest), KeyboardType.Text) {
            secondsOf(it)?.let { s -> approach.rest = s; true } ?: false
        },
        modifier = modifier,
    )
}

/**
 * Подход в отчёте: отметка ○/✓; заданное — строкой, по нажатию — шаги для сделанного иначе.
 * Изменённое выделено, рядом — что было задано.
 */
@Composable
private fun ReportRow(number: Int, approach: ApproachDraft, onRemove: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$number", fontSize = 14.sp, color = WillColors.Muted, modifier = Modifier.width(24.dp))
            Mark(approach.marked) {
                if (approach.marked) {
                    approach.marked = false
                } else {
                    // ✓ — «как задано».
                    approach.willed?.let { approach.grams = it.weightGrams; approach.repetitions = it.repetitions }
                    approach.marked = true
                    approach.editing = false
                }
            }
            Spacer(Modifier.width(10.dp))
            if (approach.editing) {
                WeightStepper(approach, Modifier.weight(1.3f))
                Text("×", fontSize = 16.sp, color = WillColors.Muted, modifier = Modifier.padding(horizontal = 4.dp))
                RepetitionsStepper(approach, Modifier.weight(1f))
            } else {
                Text(
                    "${weightText(approach.grams)} × ${approach.repetitions}",
                    fontSize = 16.sp,
                    color = when {
                        approach.changed -> WillColors.Accent
                        approach.marked -> WillColors.Ink
                        else -> WillColors.Muted
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { approach.editing = true; approach.marked = true }
                        .padding(vertical = 10.dp),
                )
            }
            if (onRemove != null) {
                Text("✕", color = WillColors.Muted, modifier = Modifier.clickable(onClick = onRemove).padding(8.dp))
            }
        }
        val willed = approach.willed
        if (approach.changed && willed != null) {
            Text(
                "задано ${weightText(willed.weightGrams)} × ${willed.repetitions}",
                fontSize = 12.sp,
                color = WillColors.Muted,
                modifier = Modifier.padding(start = 58.dp),
            )
        }
    }
}

/** Отметка подхода: пустой кружок или ✓. */
@Composable
private fun Mark(marked: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (marked) WillColors.Accent else WillColors.Divider)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (marked) Text("✓", color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp)
    }
}

@Composable
private fun WeightStepper(approach: ApproachDraft, modifier: Modifier) {
    Stepper(
        text = weightText(approach.grams),
        onMinus = { approach.grams = (approach.grams - WEIGHT_STEP).coerceAtLeast(0) },
        onPlus = { approach.grams = (approach.grams + WEIGHT_STEP).coerceAtMost(MAX_GRAMS) },
        exact = ExactInput("Вес, кг", "пусто — свой вес", kilogramsText(approach.grams), KeyboardType.Decimal) {
            gramsOf(it)?.let { grams -> approach.grams = grams; true } ?: false
        },
        modifier = modifier,
    )
}

@Composable
private fun RepetitionsStepper(approach: ApproachDraft, modifier: Modifier) {
    Stepper(
        text = "${approach.repetitions}",
        onMinus = { approach.repetitions = (approach.repetitions - 1).coerceAtLeast(1) },
        onPlus = { approach.repetitions = (approach.repetitions + 1).coerceAtMost(MAX_REPETITIONS) },
        exact = ExactInput("Повторы", "не меньше одного", "${approach.repetitions}", KeyboardType.Number) {
            it.trim().toIntOrNull()?.takeIf { r -> r in 1..MAX_REPETITIONS }?.let { r -> approach.repetitions = r; true } ?: false
        },
        modifier = modifier,
    )
}

/** Точный ввод числа: заголовок, подсказка, начальное значение; [accept] — принять, если верно. */
private class ExactInput(
    val title: String,
    val hint: String,
    val initial: String,
    val keyboard: KeyboardType,
    val accept: (String) -> Boolean,
)

/** Шаговое число: − значение +; нажатие на значение — точный ввод. */
@Composable
private fun Stepper(text: String, onMinus: () -> Unit, onPlus: () -> Unit, exact: ExactInput, modifier: Modifier) {
    var typing by remember { mutableStateOf(false) }
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(WillColors.Background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", onMinus)
        Text(
            text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f).clickable { typing = true }.padding(vertical = 10.dp),
        )
        StepButton("+", onPlus)
    }
    if (typing) {
        // Поле сразу в фокусе, прежнее значение выделено — его можно сразу перебить.
        var value by remember {
            mutableStateOf(TextFieldValue(exact.initial, selection = TextRange(0, exact.initial.length)))
        }
        var wrong by remember { mutableStateOf(false) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        AlertDialog(
            onDismissRequest = { typing = false },
            title = { Text(exact.title) },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; wrong = false },
                    modifier = Modifier.focusRequester(focus),
                    singleLine = true,
                    isError = wrong,
                    supportingText = { Text(if (wrong) "Не похоже на число" else exact.hint) },
                    keyboardOptions = KeyboardOptions(keyboardType = exact.keyboard),
                )
            },
            confirmButton = {
                TextButton(onClick = { if (exact.accept(value.text)) typing = false else wrong = true }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { typing = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun StepButton(sign: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(sign, fontSize = 20.sp, color = WillColors.Accent) }
}

/** Пунктирная карточка — «добавить ещё». */
@Composable
private fun DashedCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(56.dp)
            .drawBehind {
                drawRoundRect(
                    color = WillColors.Muted,
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                )
            }
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
