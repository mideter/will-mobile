package com.will.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import will.v1.MessengerOuterClass.NoticeCode
import will.v1.MessengerOuterClass.RoomAspect
import will.v1.historyEnd
import will.v1.protocolNotice
import will.v1.serverEvent
import will.v1.stirred
import will.v1.supplicationOffer
import will.v1.tieFormed
import will.v1.turned

class WillSessionNoticesTest {

    private val phone = FakePhone()
    private val transport = FakeTransport()
    private val session = session(phone, transport).apply { start() }
    private val heard = mutableListOf<String>()
    private val listening = CoroutineScope(Dispatchers.Unconfined).apply {
        launch { session.notices.collect { heard += it } }
    }

    init {
        transport.welcome()
    }

    @After
    fun stop() = listening.cancel()

    private fun stir() = transport.tell(serverEvent { stirred = stirred { room = "Послушание — boris"; authorName = "boris" } })

    private fun offer() = transport.tell(serverEvent { supplicationOffer = supplicationOffer { suppliantName = "gleb" } })

    @Test
    fun `what one's own abode shows is not said while one looks at it, and said elsewhere`() {
        transport.show("", "")
        stir()
        offer()
        assertEquals(emptyList<String>(), heard)

        transport.show("", "Слово")
        stir()
        offer()
        assertEquals(listOf("boris написал в комнате Послушание — boris", "gleb просит вас стать его Тренером"), heard)
    }

    @Test
    fun `a supplication is not said at one's own helm, where it is shown`() {
        transport.tell(serverEvent { turned = turned { room = "Кормчая"; aspect = RoomAspect.DWELLERS } })
        transport.tell(serverEvent { historyEnd = historyEnd {} })
        offer()
        assertEquals(emptyList<String>(), heard)
    }

    @Test
    fun `a tie is said to the novice, not to the one who accepted it`() {
        transport.show("", "Слово")
        transport.tell(serverEvent { tieFormed = tieFormed { counterpartName = "boris"; asNovice = false } })
        transport.tell(serverEvent { tieFormed = tieFormed { counterpartName = "gleb"; asNovice = true } })
        assertEquals(listOf("gleb — теперь ваш Тренер"), heard)
    }

    @Test
    fun `what one has done oneself is not said, a refusal is`() {
        transport.tell(serverEvent { protocolNotice = protocolNotice { code = NoticeCode.NOTICE_BEHEST_FULFILLED; message = "behest 9 fulfilled" } })
        transport.tell(serverEvent { protocolNotice = protocolNotice { code = NoticeCode.NOTICE_PASS_THROUGH_GATES; message = "one goes there only through gates" } })
        assertEquals(listOf("Туда идут через Врата"), heard)
    }
}
