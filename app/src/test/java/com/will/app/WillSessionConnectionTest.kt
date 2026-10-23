package com.will.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import will.v1.historyEnd
import will.v1.outstanding
import will.v1.outstandingBehest
import will.v1.serverEvent
import will.v1.turned
import will.v1.underway
import will.v1.word
import will.v1.exercise
import will.v1.approach
import will.v1.MessengerOuterClass.Word

class WillSessionConnectionTest {

    private val phone = FakePhone()
    private val transport = FakeTransport()
    private val session = session(phone, transport).apply { start() }

    private fun connection() = session.state.value.connection

    @Test
    fun `the network the connection was opened on lost, the connection is dropped at once`() {
        transport.welcome()
        phone.lose(FakePhone.WIFI)
        assertEquals(1, transport.disconnections)
        assertEquals(Connection.Reconnecting, connection())
    }

    @Test
    fun `when a network appears again it connects at once, not waiting out the pause`() {
        transport.welcome()
        phone.lose(FakePhone.WIFI)
        phone.network = FakePhone.MOBILE
        phone.becomeAvailable(FakePhone.MOBILE)
        assertEquals(2, transport.connections)
    }

    @Test
    fun `another network become the default, the old connection is dropped and a new one opened on it`() {
        transport.welcome()
        phone.network = FakePhone.MOBILE
        phone.becomeAvailable(FakePhone.MOBILE)
        assertEquals(1, transport.disconnections)
        assertEquals(2, transport.connections)
        // Открытое на новой сети не рвут, когда пропадает старая.
        transport.welcome()
        phone.lose(FakePhone.WIFI)
        assertEquals(1, transport.disconnections)
        assertEquals(Connection.Ready, connection())
    }

    @Test
    fun `the network it was opened on, told again, changes nothing`() {
        transport.welcome()
        phone.becomeAvailable(FakePhone.WIFI)
        assertEquals(0, transport.disconnections)
        assertEquals(1, transport.connections)
    }

    @Test
    fun `closed by the server, it connects again after a pause`() {
        transport.welcome()
        transport.close("gone")
        assertEquals(Connection.Reconnecting, connection())
        phone.pass(500)
        assertEquals(1, transport.connections)
        phone.pass(1_000)
        assertEquals(2, transport.connections)
    }

    @Test
    fun `welcomed, the heart beats at once, numbered, and calmly after`() {
        transport.welcome()
        val first = transport.beats().single()
        assertTrue(first.requestId > 0)
        transport.answer(first)
        phone.pass(29_000)
        assertEquals(1, transport.beats().size)
        phone.pass(1_000)
        assertEquals(2, transport.beats().size)
    }

    @Test
    fun `a beat unanswered for three seconds drops the connection`() {
        transport.welcome()
        phone.pass(2_900)
        assertEquals(0, transport.disconnections)
        phone.pass(100)
        assertEquals(1, transport.disconnections)
        assertEquals(Connection.Reconnecting, connection())
        assertTrue(phone.logged.last(), phone.logged.last().contains("сервер не слышен"))
    }

    @Test
    fun `any word of the server answers the beat`() {
        transport.welcome()
        transport.tell(serverEvent { historyEnd = historyEnd {} })
        phone.pass(10_000)
        assertEquals(0, transport.disconnections)
    }

    @Test
    fun `with an approach underway in one's training the heart beats every half second`() {
        transport.welcome()
        transport.answer(transport.beats().single())
        // Обзор своей Обители: тренировка, где подход идёт.
        transport.tell(serverEvent { turned = turned {} })
        transport.tell(serverEvent {
            outstanding = outstanding {
                owed += outstandingBehest {
                    room = "Послушание — boris"
                    behest = word {
                        id = 7
                        kind = Word.Kind.BEHEST
                        body = "Понедельник"
                        exercises += exercise { name = "Присед"; approaches += approach { weightGrams = 40_000; repetitions = 5 } }
                    }
                    underway = underway { behestId = 7; doing = true; begunAtNs = 1 }
                }
            }
        })
        transport.tell(serverEvent { historyEnd = historyEnd {} })
        assertEquals(1, phone.trainingsLed)
        phone.pass(500)
        assertEquals(2, transport.beats().size)
        transport.answer(transport.beats().last())
        phone.pass(500)
        assertEquals(3, transport.beats().size)
    }

    @Test
    fun `while unconnected the heart is still`() {
        transport.welcome()
        transport.close("gone")
        val beaten = transport.beats().size
        phone.pass(500)
        assertEquals(beaten, transport.beats().size)
    }
}
