package com.will.app

data class Choice(val behestId: Long = 0, val exercise: Int? = null, val extra: Int? = null)

object TrainingCourse {

    const val ABANDONED_AFTER_MS = 15 * 60_000L

    fun approachCount(training: WordItem, exercise: Int, underway: UnderwayItem? = null): Int =
        maxOf(
            training.exercises[exercise].approaches.size,
            (training.efforts.filter { it.exercise == exercise }.maxOfOrNull { it.approach } ?: -1) + 1,
            underway?.takeIf { it.behestId == training.id && it.exercise == exercise }?.let { it.approach + 1 } ?: 0,
        )

    fun done(training: WordItem, exercise: Int, approach: Int): Boolean =
        training.efforts.any { it.exercise == exercise && it.approach == approach }

    fun nextWilledIn(training: WordItem, exercise: Int): Int? =
        training.exercises[exercise].approaches.indices.firstOrNull { !done(training, exercise, it) }

    fun focus(training: WordItem, underway: UnderwayItem?, chosen: Int?): Int? =
        underway?.takeIf { it.behestId == training.id }?.exercise
            ?: chosen
            ?: training.efforts.maxByOrNull { it.finishedAtNs }?.exercise

    fun nextApproach(training: WordItem, focus: Int?, extra: Int? = null): Pair<Int, Int>? {
        if (extra != null && nextWilledIn(training, extra) == null) return extra to approachCount(training, extra)
        val n = training.exercises.size
        if (n == 0) return null
        val start = focus?.coerceIn(0, n - 1) ?: 0
        for (step in 0 until n) {
            val e = (start + step) % n
            nextWilledIn(training, e)?.let { return e to it }
        }
        return null
    }

    fun selected(training: WordItem, underway: UnderwayItem?, choice: Choice): Pair<Int, Int>? {
        if (underway?.behestId == training.id) return null
        val mine = choice.takeIf { it.behestId == training.id } ?: Choice()
        return nextApproach(training, focus(training, null, mine.exercise), mine.extra)
    }

    data class Rest(val sinceNs: Long, val willed: Int) {
        val endNs: Long get() = sinceNs + willed * 1_000_000_000L
    }

    fun rest(training: WordItem, selected: Pair<Int, Int>?): Rest? {
        val (e, a) = selected ?: return null
        val last = training.efforts.maxByOrNull { it.finishedAtNs } ?: return null
        return Rest(last.finishedAtNs, restWilled(training, e, a))
    }

    fun idleSinceNs(training: WordItem, choice: Choice): Long? =
        rest(training, selected(training, null, choice))?.endNs ?: training.efforts.maxOfOrNull { it.finishedAtNs }

    fun resting(training: WordItem, choice: Choice, nowNs: Long): Boolean {
        val since = idleSinceNs(training, choice) ?: return false
        return nowNs - since < ABANDONED_AFTER_MS * 1_000_000
    }

    fun sameRest(exercise: ExerciseItem): Int? {
        // Отдых говорят один раз на упражнение, когда он везде один; перед первым подходом
        // упражнения он бывает пуст — отдыхать было не после чего, — и тогда в счёт не идёт.
        val rests = exercise.approaches.mapIndexedNotNull { i, a -> a.restSeconds.takeIf { i > 0 || it != 0 } }
        return rests.firstOrNull()?.takeIf { first -> first > 0 && rests.all { it == first } }
    }

    fun restWilled(training: WordItem, exercise: Int, approach: Int): Int {
        val approaches = training.exercises.getOrNull(exercise)?.approaches ?: return 0
        return (approaches.getOrNull(approach) ?: approaches.lastOrNull())?.restSeconds ?: 0
    }

    fun restWilledAfter(training: WordItem, doing: UnderwayItem, choice: Choice): Int {
        // Отдых уже идёт, пока о подходе рассказывают: он перед тем, что выберется, когда рассказ дойдёт.
        val told = training.copy(
            efforts = training.efforts + EffortItem(doing.exercise, doing.approach, 0, 0, doing.begunAtNs, doing.endedAtNs ?: doing.begunAtNs),
        )
        val (exercise, approach) = selected(told, null, choice) ?: return 0
        return restWilled(told, exercise, approach)
    }

    data class RestClock(val seconds: Long, val over: Boolean)

    fun restClock(willed: Int, rested: Long): RestClock =
        if (rested < willed) RestClock(willed - rested, over = false) else RestClock(rested, over = true)

    fun restTakenBefore(efforts: List<EffortItem>, begunAtNs: Long): Long? {
        val before = efforts.filter { it.begunAtNs < begunAtNs && it.finishedAtNs <= begunAtNs }.maxByOrNull { it.finishedAtNs }
            ?: return null
        return (begunAtNs - before.finishedAtNs) / 1_000_000_000L
    }

    fun remarksByApproachesDone(training: WordItem, exercise: Int): Map<Int, List<RemarkItem>> {
        val finished = training.efforts.filter { it.exercise == exercise }.map { it.finishedAtNs }
        return training.remarks.filter { it.exercise == exercise }
            .groupBy { remark -> finished.count { it <= remark.saidAtNs } }
    }

    data class Span(val startNs: Long, val endNs: Long?)

    fun span(training: WordItem, underway: UnderwayItem?): Span? {
        val doing = underway?.takeIf { it.behestId == training.id }
        val start = (training.efforts.map { it.begunAtNs } + listOfNotNull(doing?.begunAtNs)).minOrNull() ?: return null
        val end = if (doing != null) null else training.efforts.maxOf { it.finishedAtNs }
        return Span(start, end)
    }

    fun proposal(training: WordItem, exercise: Int, approach: Int): ApproachItem? =
        training.exercises[exercise].approaches.getOrNull(approach)
            ?: training.efforts.filter { it.exercise == exercise }.maxByOrNull { it.approach }
                ?.let { ApproachItem(it.weightGrams, it.repetitions) }
            ?: training.exercises[exercise].approaches.lastOrNull()
}
