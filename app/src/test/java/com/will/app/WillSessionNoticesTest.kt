package com.will.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import will.v1.MessengerOuterClass.RoomPart
import will.v1.MessengerOuterClass.NoticeCode
import will.v1.MessengerOuterClass.Word
import will.v1.exercise
import will.v1.historyEnd
import will.v1.protocolNotice
import will.v1.serverEvent
import will.v1.stirred
import will.v1.supplicationOffer
import will.v1.tieFormed
import will.v1.turned
import will.v1.word

class WillSessionNoticesTest {

    private val phone = FakePhone()
    private val transport = FakeTransport()
    private val session = session(phone, transport).apply { start() }
    private val heard = mutableListOf<String>()
    private val notices = mutableListOf<Notice>()
    private val listening = CoroutineScope(Dispatchers.Unconfined).apply {
        launch { session.notices.collect { heard += it.text; notices += it } }
    }

    init {
        transport.welcome()
    }

    @After
    fun stop() = listening.cancel()

    private fun stir(said: Word = word { kind = Word.Kind.BEHEST; body = "Присед" }) =
        transport.tell(serverEvent { stirred = stirred { room = "Послушание — boris"; authorName = "boris"; word = said } })

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
        assertEquals(listOf("boris · новое задание", "gleb · Просьба стать Тренером"), heard)
    }

    @Test
    fun `a supplication is not said at one's own helm, where it is shown`() {
        transport.tell(serverEvent { turned = turned { edge = RoomPart.INNER } })
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
        assertEquals(listOf("Туда идут из своих Врат"), heard)
    }

    @Test
    fun `what was stirred leads to its room, standing in one's own abode only`() {
        transport.show("", "Слово")
        stir()
        val stirred = notices.single()
        assertTrue(session.leads(stirred))
        session.follow(stirred)
        val turn = transport.sent.last { it.hasTurn() }.turn
        assertEquals("" to "Послушание — boris", turn.abodeOf to turn.room)

        transport.show("gleb", "Слово")
        val turns = transport.sent.count { it.hasTurn() }
        assertFalse(session.leads(stirred))
        session.follow(stirred)
        assertEquals(turns, transport.sent.count { it.hasTurn() })
    }

    @Test
    fun `a supplication leads to one's helm, a refusal nowhere`() {
        transport.show("", "Слово")
        offer()
        transport.tell(serverEvent { protocolNotice = protocolNotice { code = NoticeCode.NOTICE_PASS_THROUGH_GATES; message = "" } })
        assertEquals(listOf("Кормчая", null), notices.map { it.room })
        assertEquals(listOf("gleb", null), notices.map { it.suppliant })
    }

    @Test
    fun `what was stirred is said by whom and what, a training, a task or a deed`() {
        transport.show("", "Слово")
        stir(word { kind = Word.Kind.BEHEST; body = "Ноги"; exercises += exercise { name = "Присед" } })
        stir(word { kind = Word.Kind.BEHEST; body = "Пробежать 5 км" })
        stir(word { kind = Word.Kind.DEED; body = "совершено" })
        assertEquals(listOf("boris · новая тренировка", "boris · новое задание", "boris · выполнено"), heard)
    }
}
