package com.will.app

/**
 * Выбор Послушника в тренировке [behestId]: упражнение, которого держаться, и, может быть,
 * подход «сверх заданного» в упражнении [extra]. Пустой — продолжать, где остановился.
 */
data class Choice(val behestId: Long = 0, val exercise: Int? = null, val extra: Int? = null)

/**
 * Ход тренировки: что сделано, что идёт, что дальше. Чистые правила без экрана, чтобы их
 * можно было проверить. Номера упражнений и подходов — с нуля.
 *
 * Подходы упражнения идут по порядку, упражнения — в каком угодно; у упражнения есть
 * заданные подходы, сделанные сверх них и идущий. Фокус — упражнение, где идёт подход;
 * иначе выбранное; иначе — где был последний сделанный (продолжать, где остановился).
 * Выбор держится фокуса, пока заданные подходы его не кончатся, затем переходит к
 * следующему упражнению по кругу; когда всё заданное сделано, приступать не к чему, разве
 * что выбран подход сверх заданного. Выбор другой тренировки не в счёт.
 *
 * Отдых — перед подходом: от конца последнего по времени усилия; до первого усилия
 * отдыхать не от чего. Заданный отдых — у подхода, к которому приступают; перед подходом
 * сверх заданного — как перед последним заданным в этом упражнении. Таймер отдыха, пока
 * заданный не вышел, — обратный отсчёт, потом секундомер дальше от отметки; без заданного —
 * секундомер с нуля. Подтверждение предлагает заданное, а сверх заданного — как последний
 * сделанный подход.
 */
object TrainingCourse {

    fun count(training: WordItem, exercise: Int, underway: UnderwayItem? = null): Int =
        maxOf(
            training.exercises[exercise].approaches.size,
            (training.efforts.filter { it.exercise == exercise }.maxOfOrNull { it.approach } ?: -1) + 1,
            underway?.takeIf { it.behestId == training.id && it.exercise == exercise }?.let { it.approach + 1 } ?: 0,
        )

    fun done(training: WordItem, exercise: Int, approach: Int): Boolean =
        training.efforts.any { it.exercise == exercise && it.approach == approach }

    fun nextIn(training: WordItem, exercise: Int): Int? =
        training.exercises[exercise].approaches.indices.firstOrNull { !done(training, exercise, it) }

    fun focus(training: WordItem, underway: UnderwayItem?, chosen: Int?): Int? =
        underway?.takeIf { it.behestId == training.id }?.exercise
            ?: chosen
            ?: training.efforts.maxByOrNull { it.finishedAtNs }?.exercise

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

    fun selected(training: WordItem, underway: UnderwayItem?, choice: Choice): Pair<Int, Int>? {
        if (underway?.behestId == training.id) return null
        val mine = choice.takeIf { it.behestId == training.id } ?: Choice()
        return next(training, focus(training, null, mine.exercise), mine.extra)
    }

    /** Отдых перед подходом: идёт с [sinceNs] — конца последнего усилия; задано [willed] секунд. */
    data class Rest(val sinceNs: Long, val willed: Int) {
        val endNs: Long get() = sinceNs + willed * 1_000_000_000L
    }

    fun rest(training: WordItem, selected: Pair<Int, Int>?): Rest? {
        val (e, a) = selected ?: return null
        val last = training.efforts.maxByOrNull { it.finishedAtNs } ?: return null
        return Rest(last.finishedAtNs, restWilled(training, e, a))
    }

    fun restWilled(training: WordItem, exercise: Int, approach: Int): Int {
        val approaches = training.exercises.getOrNull(exercise)?.approaches ?: return 0
        return (approaches.getOrNull(approach) ?: approaches.lastOrNull())?.restSeconds ?: 0
    }

    /** Таймер отдыха: [seconds] — сколько осталось или, когда [over], сколько отдыхает всего. */
    data class RestClock(val seconds: Long, val over: Boolean)

    fun restClock(willed: Int, rested: Long): RestClock =
        if (rested < willed) RestClock(willed - rested, over = false) else RestClock(rested, over = true)

    fun restUntil(efforts: List<EffortItem>, begunAtNs: Long): Long? {
        val before = efforts.filter { it.begunAtNs < begunAtNs && it.finishedAtNs <= begunAtNs }.maxByOrNull { it.finishedAtNs }
            ?: return null
        return (begunAtNs - before.finishedAtNs) / 1_000_000_000L
    }

    fun proposal(training: WordItem, exercise: Int, approach: Int): ApproachItem? =
        training.exercises[exercise].approaches.getOrNull(approach)
            ?: training.efforts.filter { it.exercise == exercise }.maxByOrNull { it.approach }
                ?.let { ApproachItem(it.weightGrams, it.repetitions) }
            ?: training.exercises[exercise].approaches.lastOrNull()
}
