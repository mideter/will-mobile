package com.will.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import will.v1.MessengerOuterClass.DwellerKind
import will.v1.authOk
import will.v1.dwelling
import will.v1.serverEvent

class WillSessionNamingTest {

    private val phone = FakePhone()
    private val transport = FakeTransport()
    private val session = session(phone, transport).apply { start() }
    private val heard = mutableListOf<String>()
    private val listening = CoroutineScope(Dispatchers.Unconfined).apply {
        launch { session.notices.collect { heard += it.text } }
    }

    @After
    fun stop() = listening.cancel()

    private fun knownBy(host: String, kind: DwellerKind = DwellerKind.ACQUAINTANCE) =
        transport.tell(serverEvent { dwelling = dwelling { hostName = host; this.kind = kind } })

    @Test
    fun `given a name, one is told it and who gave it, until one begins`() {
        transport.tell(serverEvent { authOk = authOk { unborn = true; mark = 2 } })
        assertEquals(2L, session.state.value.unbornMark)

        transport.tell(serverEvent { authOk = authOk { name = "xv5tnijj"; father = "ih7wzipz" } })
        assertNull(session.state.value.unbornMark)
        assertEquals("xv5tnijj", session.state.value.ownName)
        assertEquals("ih7wzipz", session.state.value.namedBy)

        session.begin()
        assertNull(session.state.value.namedBy)
    }

    @Test
    fun `the one who gave the name is not said to know one, for the screen says it, and another is`() {
        transport.tell(serverEvent { authOk = authOk { unborn = true; mark = 2 } })
        transport.tell(serverEvent { authOk = authOk { name = "xv5tnijj"; father = "ih7wzipz" } })
        knownBy("ih7wzipz")
        assertEquals(emptyList<String>(), heard)
        knownBy("boris")
        assertEquals(listOf("boris знает вас"), heard)
    }

    @Test
    fun `an ordinary welcome names no one who gave the name`() {
        transport.welcome("anna")
        assertNull(session.state.value.namedBy)
    }
}
