package com.will.app

/**
 * Ход тренировки: что сделано, что идёт, что дальше. Чистые правила без экрана, чтобы их
 * можно было проверить. Номера упражнений и подходов — с нуля.
 *
 * Подходы упражнения идут по порядку, упражнения — в каком угодно. Выбор держится
 * упражнения, на котором Послушник сейчас (его «фокус»), пока подходы его не кончатся;
 * затем переходит к следующему упражнению по кругу.
 */
object TrainingCourse {

    /** Сколько подходов у упражнения: заданные и сделанные сверх них. */
    fun count(training: WordItem, exercise: Int): Int =
        maxOf(
            training.exercises[exercise].approaches.size,
            (training.efforts.filter { it.exercise == exercise }.maxOfOrNull { it.approach } ?: -1) + 1,
        )

    fun done(training: WordItem, exercise: Int, approach: Int): Boolean =
        training.efforts.any { it.exercise == exercise && it.approach == approach }

    /** Следующий заданный подход упражнения по порядку; null — заданные сделаны. */
    fun nextIn(training: WordItem, exercise: Int): Int? =
        training.exercises[exercise].approaches.indices.firstOrNull { !done(training, exercise, it) }

    /**
     * Упражнение в фокусе: где идёт подход; иначе выбранное; иначе — где был последний
     * сделанный подход (продолжать, где остановился); иначе ничего.
     */
    fun focus(training: WordItem, underway: UnderwayItem?, chosen: Int?): Int? =
        underway?.takeIf { it.behestId == training.id }?.exercise
            ?: chosen
            ?: training.efforts.maxByOrNull { it.finishedAtNs }?.exercise

    /**
     * Подход, к которому приступают: следующий в упражнении фокуса; если там заданные
     * кончились — следующий в ближайшем после него упражнении (по кругу); если всё
     * заданное сделано — null. [extra] — выбран подход сверх заданного в этом упражнении.
     */
    fun next(training: WordItem, focus: Int?, extra: Int? = null): Pair<Int, Int>? {
        if (extra != null && nextIn(training, extra) == null) return extra to count(training, extra)
        val n = training.exercises.size
        if (n == 0) return null
        val start = focus?.coerceIn(0, n - 1) ?: 0
        for (step in 0 until n) {
            val e = (start + step) % n
            nextIn(training, e)?.let { return e to it }
        }
        return null
    }

    /**
     * Отдых после сделанного подхода, в секундах: заданный после него; у подхода сверх
     * заданного — как у последнего заданного в этом упражнении.
     */
    fun restAfter(training: WordItem, effort: EffortItem): Int {
        val approaches = training.exercises.getOrNull(effort.exercise)?.approaches ?: return 0
        return (approaches.getOrNull(effort.approach) ?: approaches.lastOrNull())?.restSeconds ?: 0
    }

    /** Отдых перед подходом, в секундах: от конца предыдущего по времени до его начала. */
    fun restBefore(efforts: List<EffortItem>, effort: EffortItem): Long? {
        val before = efforts.filter { it.finishedAtNs <= effort.begunAtNs && it != effort }.maxByOrNull { it.finishedAtNs }
            ?: return null
        return (effort.begunAtNs - before.finishedAtNs) / 1_000_000_000L
    }

    /** Что предложить в подтверждении: заданное; сверх заданного — как последний сделанный в упражнении. */
    fun proposal(training: WordItem, exercise: Int, approach: Int): ApproachItem? =
        training.exercises[exercise].approaches.getOrNull(approach)
            ?: training.efforts.filter { it.exercise == exercise }.maxByOrNull { it.approach }
                ?.let { ApproachItem(it.weightGrams, it.repetitions) }
            ?: training.exercises[exercise].approaches.lastOrNull()
}
