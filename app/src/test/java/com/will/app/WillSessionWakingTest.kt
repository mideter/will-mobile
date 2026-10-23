package com.will.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WillSessionWakingTest {

    private val phone = FakePhone()
    private val transport = FakeTransport()

    private fun turns() = transport.sent.filter { it.hasTurn() }

    private fun historyRequests() = transport.sent.filter { it.hasHistoryRequest() }

    private fun reconnect() {
        transport.close("gone")
        phone.pass(2_000)
        transport.sent.clear()
        transport.welcome()
    }

    @Test
    fun `waking for the first time, one looks at one's abode`() {
        session(phone, transport).start()
        transport.welcome()
        assertEquals(1, historyRequests().size)
        assertTrue(turns().isEmpty())
    }

    @Test
    fun `waking again in one's own room, one goes back to it at once, past the abode`() {
        val session = session(phone, transport).apply { start() }
        transport.welcome()
        session.enter("Послушание — boris")
        transport.show("", "Послушание — boris")
        val shown = session.state.value.view

        reconnect()
        val back = turns().single()
        assertTrue(back.requestId > 0)
        assertEquals("", back.turn.abodeOf)
        assertEquals("Послушание — boris", back.turn.room)
        assertTrue(historyRequests().isEmpty())
        // Пока комната идёт, на экране остаётся прежний вид: открытое в нём не закрывается.
        assertSame(shown, session.state.value.view)
    }

    @Test
    fun `one's room gone while asleep, one is at home`() {
        val session = session(phone, transport).apply { start() }
        transport.welcome()
        session.enter("Послушание — boris")
        transport.show("", "Послушание — boris")

        reconnect()
        transport.refuse(turns().single())
        val home = turns().last()
        assertEquals("", home.turn.abodeOf)
        assertEquals("", home.turn.room)
    }

    @Test
    fun `waking in another's abode, one is at home`() {
        val session = session(phone, transport).apply { start() }
        transport.welcome()
        session.gates("boris")
        transport.show("boris", "Врата")

        reconnect()
        assertTrue(turns().isEmpty())
        assertEquals(1, historyRequests().size)
    }
}
