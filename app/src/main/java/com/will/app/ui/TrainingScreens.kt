package com.will.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
                        if (differs && asked != null) "   (задано ${weightText(asked.weightGrams)} × ${asked.repetitions})" else "",
                    fontSize = 14.sp,
                    color = if (differs) WillColors.Accent else WillColors.Ink,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/** Черновик подхода: вес и повторы как их набирают. */
private class ApproachDraft(weight: String, repetitions: String) {
    var weight by mutableStateOf(weight)
    var repetitions by mutableStateOf(repetitions)
}

/** Черновик упражнения. */
private class ExerciseDraft(name: String, approaches: List<ApproachDraft>) {
    var name by mutableStateOf(name)
    val approaches: SnapshotStateList<ApproachDraft> = mutableStateListOf<ApproachDraft>().apply { addAll(approaches) }
}

private fun draftOf(exercise: ExerciseItem) = ExerciseDraft(
    exercise.name,
    exercise.approaches.map {
        ApproachDraft(if (it.weightGrams == 0) "" else weightText(it.weightGrams).removeSuffix(" кг"), it.repetitions.toString())
    },
)

/** Граммы из набранного: «62,5» → 62500; пусто — свой вес. Null, если это не вес. */
private fun gramsOf(text: String): Int? {
    val t = text.trim().replace(',', '.')
    if (t.isEmpty()) return 0
    val kg = t.toDoubleOrNull() ?: return null
    if (kg < 0 || kg > 1000) return null
    return Math.round(kg * 1000).toInt()
}

/** Упражнения из черновиков; null, если что-то набрано не так. */
private fun exercisesOf(drafts: List<ExerciseDraft>): List<ExerciseItem>? {
    if (drafts.isEmpty()) return null
    return drafts.map { draft ->
        if (draft.name.isBlank() || draft.approaches.isEmpty()) return null
        ExerciseItem(
            draft.name.trim(),
            draft.approaches.map { a ->
                val grams = gramsOf(a.weight) ?: return null
                val reps = a.repetitions.trim().toIntOrNull()?.takeIf { it >= 1 } ?: return null
                ApproachItem(grams, reps)
            },
        )
    }
}

/**
 * Редактор тренировки. Тренер составляет её ([report] = false): заголовок, упражнения,
 * подходы. Послушник отчитывается ([report] = true): упражнения заполнены велённым, он правит
 * сделанное и пишет отчёт. [names] — прежние названия упражнений для подсказки.
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
            if (initial.isEmpty()) add(ExerciseDraft("", listOf(ApproachDraft("", "10"))))
            else addAll(initial.map(::draftOf))
        }
    }
    val ready = exercisesOf(drafts)

    Column(Modifier.fillMaxSize()) {
        Header(
            title = if (report) "Отчёт" else "Тренировка",
            subtitle = heading,
            onBack = onCancel,
            actions = {
                TextButton(onClick = { ready?.let { onDone(text, it) } }, enabled = ready != null) {
                    Text(if (report) "Отчитаться" else "Задать")
                }
            },
        )
        LazyColumn(Modifier.weight(1f)) {
            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    placeholder = { Text(if (report) "Отчёт (необязательно)" else "Заголовок — «Тренировка»") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            itemsIndexed(drafts) { index, draft ->
                ExerciseEditor(
                    draft = draft,
                    names = names,
                    onRemove = if (drafts.size > 1) ({ drafts.removeAt(index) }) else null,
                )
                HorizontalDivider(color = WillColors.Divider)
            }
            item {
                TextButton(
                    onClick = { drafts.add(ExerciseDraft("", listOf(ApproachDraft("", "10")))) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text("+ упражнение") }
            }
        }
        if (ready == null) Hint("У каждого упражнения — название и хотя бы один подход; повторов не меньше одного.")
    }
}

@Composable
private fun ExerciseEditor(draft: ExerciseDraft, names: List<String>, onRemove: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft.name = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Упражнение") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            if (onRemove != null) TextButton(onClick = onRemove) { Text("✕", color = WillColors.Muted) }
        }
        // Подсказка: прежние названия, в которых есть набранное.
        val typed = draft.name.trim()
        if (typed.isNotEmpty()) {
            val like = names.filter { it != typed && it.contains(typed, ignoreCase = true) }.take(3)
            Row { like.forEach { TextButton(onClick = { draft.name = it }) { Text(it, fontSize = 13.sp) } } }
        }
        draft.approaches.forEachIndexed { index, approach ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}.", modifier = Modifier.width(24.dp), color = WillColors.Muted)
                OutlinedTextField(
                    value = approach.weight,
                    onValueChange = { approach.weight = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("свой вес") },
                    suffix = { Text("кг") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text(" × ", fontSize = 16.sp)
                OutlinedTextField(
                    value = approach.repetitions,
                    onValueChange = { approach.repetitions = it },
                    modifier = Modifier.width(88.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (draft.approaches.size > 1) {
                    TextButton(onClick = { draft.approaches.removeAt(index) }) { Text("✕", color = WillColors.Muted) }
                }
            }
        }
        // Новый подход — как последний: обычно меняют одно число.
        TextButton(onClick = {
            val last = draft.approaches.lastOrNull()
            draft.approaches.add(ApproachDraft(last?.weight ?: "", last?.repetitions ?: "10"))
        }) { Text("+ подход") }
    }
}
