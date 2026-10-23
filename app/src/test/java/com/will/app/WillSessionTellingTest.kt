package com.will.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WillSessionTellingTest {

    private val phone = FakePhone()
    private val transport = FakeTransport()
    private val session = session(phone, transport).apply { start() }

    init {
        transport.welcome()
    }

    private fun told() = transport.sent.filter { it.hasFinishApproach() }

    private fun remarks() = transport.sent.filter { it.hasSayRemark() }

    @Test
    fun `the telling of an approach is asked by number and waits for the answer`() {
        session.finishApproach(7, 40_000, 5, 1)
        val telling = told().single()
        assertTrue(telling.requestId > 0)
        assertEquals(1, telling.finishApproach.strain)
        assertEquals(Reply.Pending, session.state.value.telling)
        // Пока ответа нет, второй раз не говорят.
        session.finishApproach(7, 40_000, 5, 1)
        assertEquals(1, told().size)
        transport.answer(telling)
        assertEquals(Reply.None, session.state.value.telling)
    }

    @Test
    fun `refused, the telling may be said again`() {
        session.finishApproach(7, 40_000, 5, 0)
        transport.refuse(told().single())
        assertEquals(Reply.None, session.state.value.telling)
        session.finishApproach(7, 40_000, 5, 0)
        assertEquals(2, told().size)
    }

    @Test
    fun `the connection broken before the answer, the telling waits no more`() {
        session.finishApproach(7, 40_000, 5, 0)
        transport.close("gone")
        assertEquals(Reply.None, session.state.value.telling)
    }

    @Test
    fun `unconnected, nothing is told and nothing awaited`() {
        transport.close("gone")
        session.finishApproach(7, 40_000, 5, 0)
        assertTrue(told().isEmpty())
        assertEquals(Reply.None, session.state.value.telling)
    }

    @Test
    fun `a remark is asked by number, and once taken the field may close`() {
        session.sayRemark(7, 0, "  колено  ")
        val remark = remarks().single()
        assertTrue(remark.requestId > 0)
        assertEquals("колено", remark.sayRemark.body)
        assertEquals(Reply.Pending, session.state.value.remarking)
        session.sayRemark(7, 0, "ещё")
        assertEquals(1, remarks().size)
        transport.answer(remark)
        assertEquals(Reply.Granted, session.state.value.remarking)
        session.remarkingSeen()
        assertEquals(Reply.None, session.state.value.remarking)
    }

    @Test
    fun `a remark refused or cut off keeps the field, and an empty one is not said`() {
        session.sayRemark(7, 0, "колено")
        transport.refuse(remarks().single())
        assertEquals(Reply.None, session.state.value.remarking)
        session.sayRemark(7, 0, "колено")
        transport.close("gone")
        assertEquals(Reply.None, session.state.value.remarking)
        session.sayRemark(7, 0, "   ")
        assertEquals(2, remarks().size)
    }
}
