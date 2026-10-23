package com.will.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import will.v1.MessengerOuterClass.NoticeCode

class NoticesTest {

    @Test
    fun `every code is said in its own words, not the server's, or not said at all`() {
        for (code in NoticeCode.values()) {
            if (code == NoticeCode.NOTICE_UNSPECIFIED || code == NoticeCode.UNRECOGNIZED) continue
            val said = Notices.say(code, listOf("anna", "boris"), "the server's words") ?: continue
            assertNotEquals("$code", "the server's words", said)
            assertTrue("$code", said.isNotBlank())
        }
    }

    @Test
    fun `what one has done oneself is not said, the screen shows it`() {
        assertNull(Notices.say(NoticeCode.NOTICE_BORN, listOf("yc01fdhl"), "yc01fdhl born"))
        assertNull(Notices.say(NoticeCode.NOTICE_ADMITTED, listOf("boris"), "boris admitted as an acquaintance"))
        assertNull(Notices.say(NoticeCode.NOTICE_BEHEST_FULFILLED, emptyList(), "behest 9 fulfilled"))
    }

    @Test
    fun `the names in a notice are put in their places`() {
        assertEquals("Просьба к boris отклонена", Notices.say(NoticeCode.NOTICE_SUPPLICATION_DECLINED, listOf("boris"), ""))
    }

    @Test
    fun `without a code the server's words are translated as before, and the unknown shown as it is`() {
        assertEquals("Пустое сообщение", Notices.say(NoticeCode.NOTICE_UNSPECIFIED, emptyList(), "Saying must be non-empty"))
        assertEquals("something new", Notices.say(NoticeCode.NOTICE_UNSPECIFIED, emptyList(), "something new"))
    }
}
