package com.will.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import will.v1.MessengerOuterClass.NoticeCode

class NoticesTest {

    @Test
    fun `every code is said in its own words, not the server's`() {
        for (code in NoticeCode.values()) {
            if (code == NoticeCode.NOTICE_UNSPECIFIED || code == NoticeCode.UNRECOGNIZED) continue
            val said = Notices.say(code, listOf("anna", "boris"), "the server's words")
            assertNotEquals("$code", "the server's words", said)
            assertTrue("$code", said.isNotBlank())
        }
    }

    @Test
    fun `the names in a notice are put in their places`() {
        assertEquals("Родился yc01fdhl", Notices.say(NoticeCode.NOTICE_BORN, listOf("yc01fdhl"), "yc01fdhl born"))
        assertEquals(
            "anna впустил boris знакомым",
            Notices.say(NoticeCode.NOTICE_LET_IN, listOf("anna", "boris"), "anna let boris in as an acquaintance"),
        )
        assertEquals("«Слово» — во внешней части", Notices.say(NoticeCode.NOTICE_ARRANGED_OUTER, listOf("Слово"), ""))
    }

    @Test
    fun `without a code the server's words are translated as before, and the unknown shown as it is`() {
        assertEquals("Пустое сообщение", Notices.say(NoticeCode.NOTICE_UNSPECIFIED, emptyList(), "Saying must be non-empty"))
        assertEquals("something new", Notices.say(NoticeCode.NOTICE_UNSPECIFIED, emptyList(), "something new"))
    }
}
