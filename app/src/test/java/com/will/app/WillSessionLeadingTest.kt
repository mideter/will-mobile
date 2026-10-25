package com.will.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import will.v1.MessengerOuterClass.Word
import will.v1.approach
import will.v1.effort
import will.v1.exercise
import will.v1.historyEnd
import will.v1.outstanding
import will.v1.outstandingBehest
import will.v1.serverEvent
import will.v1.turned
import will.v1.underway
import will.v1.word

class WillSessionLeadingTest {

    private val phone = FakePhone()
    private val transport = FakeTransport()
    private val session = session(phone, transport).apply { start() }

    init {
        transport.welcome()
    }

    private fun training(id: Long, mine: Boolean = false) = word {
        this.id = id
        kind = Word.Kind.BEHEST
        isMine = mine
        body = "Тренировка $id"
        exercises += exercise { name = "Присед"; approaches += approach { weightGrams = 40_000; repetitions = 5 } }
    }

    private fun going(id: Long) = underway { behestId = id; doing = true; begunAtNs = 1 }

    private fun rested(id: Long, minutesAgo: Long) = training(id).toBuilder().addEfforts(effort {
        val finished = session.clock.nowNs() - minutesAgo * 60_000_000_000L
        weightGrams = 40_000; repetitions = 5; begunAtNs = finished - 30_000_000_000L; finishedAtNs = finished
    }).build()

    private fun room(host: String, room: String, vararg trainings: Pair<Long, Boolean>, underways: List<Long>) {
        transport.tell(serverEvent { turned = turned { abodeOf = host; this.room = room } })
        for ((id, mine) in trainings) transport.tell(serverEvent { word = training(id, mine) })
        for (id in underways) transport.tell(serverEvent { underway = going(id) })
        transport.tell(serverEvent { historyEnd = historyEnd {} })
    }

    private fun workout() = session.state.value.workout

    @Test
    fun `an approach underway in one's own training, seen in one's room, is led`() {
        room("", "Послушание — boris", 7L to false, underways = listOf(7L))
        assertEquals(Workout("", "Послушание — boris", 7), workout())
        assertEquals(1, phone.trainingsLed)
    }

    @Test
    fun `an approach begun while one looks is led as soon as it is told`() {
        room("", "Послушание — boris", 7L to false, underways = emptyList())
        assertNull(workout())
        transport.tell(serverEvent { underway = going(7) })
        assertEquals(7L, workout()?.behestId)
    }

    @Test
    fun `the novice's approach seen by his trainer is not led`() {
        room("", "Ведение — boris", 7L to true, underways = listOf(7L))
        assertNull(workout())
        assertEquals(0, phone.trainingsLed)
    }

    @Test
    fun `an approach seen in another's abode is not led`() {
        room("boris", "Послушание — anna", 7L to false, underways = listOf(7L))
        assertNull(workout())
    }

    @Test
    fun `one led, another seen does not take its place`() {
        room("", "Послушание — boris", 7L to false, 8L to false, underways = listOf(7L, 8L))
        val led = workout()
        transport.tell(serverEvent { underway = going(8) })
        assertEquals(led, workout())
    }

    @Test
    fun `a training ended, the one still going is led next, and none when none goes`() {
        room("", "Послушание — boris", 7L to false, 8L to false, underways = listOf(7L, 8L))
        val first = workout()!!.behestId
        session.stopWorkout()
        val next = workout()!!.behestId
        assertEquals(setOf(7L, 8L), setOf(first, next))
        session.stopWorkout()
        assertEquals(first, workout()?.behestId)
        transport.tell(serverEvent { underway = underway { behestId = first } })
        session.stopWorkout()
        session.stopWorkout()
        assertNull(workout())
    }

    @Test
    fun `from the overview of one's abode, what awaits one is led, what one set for others is not`() {
        transport.tell(serverEvent { turned = turned {} })
        transport.tell(serverEvent {
            outstanding = outstanding {
                awaited += outstandingBehest { room = "Ведение — boris"; behest = training(5, mine = true); underway = going(5) }
                owed += outstandingBehest { room = "Послушание — boris"; behest = training(7) }
                owed += outstandingBehest { room = "Послушание — viktor"; behest = training(9); underway = going(9) }
            }
        })
        transport.tell(serverEvent { historyEnd = historyEnd {} })
        assertEquals(Workout("", "Послушание — viktor", 9), workout())
    }

    @Test
    fun `a training begun and resting, seen in the overview of one's abode, is led, an abandoned one is not`() {
        transport.tell(serverEvent { turned = turned {} })
        transport.tell(serverEvent {
            outstanding = outstanding {
                owed += outstandingBehest { room = "Послушание — viktor"; behest = rested(9, minutesAgo = 20) }
                owed += outstandingBehest { room = "Послушание — boris"; behest = rested(7, minutesAgo = 1) }
            }
        })
        transport.tell(serverEvent { historyEnd = historyEnd {} })
        assertEquals(Workout("", "Послушание — boris", 7), workout())
        assertEquals(1, phone.trainingsLed)
    }

    @Test
    fun `an approach underway is led before a training resting`() {
        transport.tell(serverEvent { turned = turned {} })
        transport.tell(serverEvent {
            outstanding = outstanding {
                owed += outstandingBehest { room = "Послушание — boris"; behest = rested(7, minutesAgo = 1) }
                owed += outstandingBehest { room = "Послушание — viktor"; behest = training(9); underway = going(9) }
            }
        })
        transport.tell(serverEvent { historyEnd = historyEnd {} })
        assertEquals(9L, workout()?.behestId)
    }

    @Test
    fun `a training resting in one's room is led`() {
        transport.tell(serverEvent { turned = turned { room = "Послушание — boris" } })
        transport.tell(serverEvent { word = rested(7, minutesAgo = 1) })
        transport.tell(serverEvent { historyEnd = historyEnd {} })
        assertEquals(Workout("", "Послушание — boris", 7), workout())
    }

    @Test
    fun `a training fulfilled in one's room is not led, however lately rested`() {
        transport.tell(serverEvent { turned = turned { room = "Послушание — boris" } })
        transport.tell(serverEvent { word = rested(7, minutesAgo = 1) })
        transport.tell(serverEvent { word = word { id = 8; kind = Word.Kind.DEED; behestId = 7 } })
        transport.tell(serverEvent { historyEnd = historyEnd {} })
        assertNull(workout())
    }
}
