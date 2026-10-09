package com.will.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import will.v1.MessengerOuterClass.Word

class TrainingCourseTest {

    private val press = ExerciseItem("Жим", listOf(ApproachItem(60_000, 10, 90), ApproachItem(70_000, 8, 120)))
    private val squat = ExerciseItem("Присед", listOf(ApproachItem(100_000, 5, 60), ApproachItem(100_000, 5, 60)))
    private val pull = ExerciseItem("Подтягивания", listOf(ApproachItem(0, 8, 30)))

    private fun training(vararg efforts: EffortItem) =
        WordItem(1, "trainer", "Вторник", false, Word.Kind.BEHEST, 0, listOf(press, squat, pull), efforts.toList())

    /** Подход сделан: секунда начала — [at], длится 30 с. */
    private fun done(exercise: Int, approach: Int, at: Long, reps: Int = 5) =
        EffortItem(exercise, approach, 100_000, reps, at * NS, (at + 30) * NS)

    @Test
    fun `at the start the first approach of the first exercise is proposed`() {
        val t = training()
        assertEquals(0 to 0, TrainingCourse.next(t, TrainingCourse.focus(t, null, null)))
    }

    @Test
    fun `having chosen the second exercise one stays with it after an approach`() {
        // Выбрал присед, сделал первый подход: дальше — второй подход приседа, не жим.
        val t = training(done(1, 0, at = 0))
        val focus = TrainingCourse.focus(t, null, chosen = 1)
        assertEquals(1 to 1, TrainingCourse.next(t, focus))
    }

    @Test
    fun `reopened, one goes on with the exercise of the last approach done`() {
        // Ничего не выбрано (экран открыт заново): фокус — где был последний подход.
        val t = training(done(1, 0, at = 0))
        val focus = TrainingCourse.focus(t, null, chosen = null)
        assertEquals(1, focus)
        assertEquals(1 to 1, TrainingCourse.next(t, focus))
    }

    @Test
    fun `when an exercise is done one goes on to the next one round the circle, not to the first`() {
        // Сделаны все подходы приседа (второго): дальше — подтягивания (третье), а не жим.
        val t = training(done(1, 0, at = 0), done(1, 1, at = 100))
        assertEquals(2 to 0, TrainingCourse.next(t, TrainingCourse.focus(t, null, chosen = 1)))
        // Сделано и третье: по кругу — к жиму.
        val u = training(done(1, 0, at = 0), done(1, 1, at = 100), done(2, 0, at = 200))
        assertEquals(0 to 0, TrainingCourse.next(u, TrainingCourse.focus(u, null, chosen = 2)))
    }

    @Test
    fun `when all willed is done nothing is proposed`() {
        val t = training(done(0, 0, 0), done(0, 1, 100), done(1, 0, 200), done(1, 1, 300), done(2, 0, 400))
        assertNull(TrainingCourse.next(t, TrainingCourse.focus(t, null, null)))
    }

    @Test
    fun `the approach underway sets the focus, wherever it was begun`() {
        val t = training(done(0, 0, at = 0))
        val underway = UnderwayItem(behestId = 1, exercise = 2, approach = 0, begunAtNs = 50 * NS)
        assertEquals(2, TrainingCourse.focus(t, underway, chosen = 0))
        // Подход другой тренировки фокуса не задаёт.
        assertEquals(0, TrainingCourse.focus(t, underway.copy(behestId = 9), chosen = 0))
    }

    @Test
    fun `an approach beyond the willed is proposed only once they are done, and does not stick`() {
        // Выбрать сверх заданного можно, когда заданные сделаны.
        val t = training(done(2, 0, at = 0))
        assertEquals(2 to 1, TrainingCourse.next(t, focus = 2, extra = 2))
        // Пока заданные не сделаны — «сверх» не предлагается, а предлагается следующий.
        assertEquals(0 to 0, TrainingCourse.next(training(), focus = 0, extra = 0))
        // Сделав сверх заданного, без нового выбора дальше — следующее упражнение, а не ещё один сверх.
        val u = training(done(2, 0, at = 0), done(2, 1, at = 100))
        assertEquals(0 to 0, TrainingCourse.next(u, TrainingCourse.focus(u, null, chosen = 2)))
    }

    @Test
    fun `the rest before an approach is the willed one, beyond the willed as the last willed`() {
        val t = training()
        assertEquals(90, TrainingCourse.restWilled(t, 0, 0))
        assertEquals(120, TrainingCourse.restWilled(t, 0, 1))
        assertEquals(120, TrainingCourse.restWilled(t, 0, 2))
    }

    @Test
    fun `the rest clock counts down, then goes on from the mark passed`() {
        assertEquals(TrainingCourse.RestClock(90, over = false), TrainingCourse.restClock(90, 0))
        assertEquals(TrainingCourse.RestClock(1, over = false), TrainingCourse.restClock(90, 89))
        assertEquals(TrainingCourse.RestClock(90, over = true), TrainingCourse.restClock(90, 90))
        assertEquals(TrainingCourse.RestClock(102, over = true), TrainingCourse.restClock(90, 102))
    }

    @Test
    fun `without a willed rest the clock is a stopwatch from zero`() {
        assertEquals(TrainingCourse.RestClock(0, over = true), TrainingCourse.restClock(0, 0))
        assertEquals(TrainingCourse.RestClock(15, over = true), TrainingCourse.restClock(0, 15))
    }

    @Test
    fun `the rest taken before an approach is the time since the one before it finished, none before the first`() {
        val first = done(0, 0, at = 0)
        val second = done(0, 1, at = 75)
        val t = training(first, second)
        assertNull(TrainingCourse.restBefore(t.efforts, first))
        assertEquals(45L, TrainingCourse.restBefore(t.efforts, second))
    }

    @Test
    fun `the rest before an approach underway is the time since the last effort finished`() {
        val t = training(done(0, 0, at = 0), done(1, 0, at = 50))
        assertEquals(40L, TrainingCourse.restUntil(t.efforts, 120 * NS))
        assertNull(TrainingCourse.restUntil(emptyList(), 120 * NS))
    }

    @Test
    fun `the confirmation proposes the willed, beyond the willed the last approach done`() {
        val t = training(done(1, 0, at = 0, reps = 4), done(1, 1, at = 100, reps = 3))
        assertEquals(squat.approaches[0], TrainingCourse.proposal(t, 1, 0))
        assertEquals(ApproachItem(100_000, 3), TrainingCourse.proposal(t, 1, 2))
    }

    @Test
    fun `the approaches of an exercise go in order`() {
        val t = training(done(0, 0, at = 0))
        assertEquals(1, TrainingCourse.nextIn(t, 0))
        assertEquals(0, TrainingCourse.nextIn(t, 1))
        assertEquals(2, TrainingCourse.count(training(done(0, 0, 0), done(0, 1, 100)), 0))
        assertEquals(3, TrainingCourse.count(training(done(0, 0, 0), done(0, 1, 100), done(0, 2, 200)), 0))
    }

    @Test
    fun `an approach beyond the willed is counted while it is underway`() {
        // Подтягивания: задан один подход, он сделан, идёт второй — сверх заданного.
        val t = training(done(2, 0, at = 0))
        val underway = UnderwayItem(behestId = 1, exercise = 2, approach = 1, begunAtNs = 100 * NS)
        assertEquals(2, TrainingCourse.count(t, 2, underway))
        // Идущий подход другого упражнения или другой тренировки не в счёт.
        assertEquals(1, TrainingCourse.count(t, 2, underway.copy(behestId = 9)))
        assertEquals(2, TrainingCourse.count(t, 1, underway))
    }

    private companion object {
        const val NS = 1_000_000_000L
    }
}
