package com.will.app.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.will.app.FakePhone
import com.will.app.FakeTransport
import com.will.app.View
import com.will.app.session
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import will.v1.MessengerOuterClass.Word
import will.v1.historyEnd
import will.v1.serverEvent
import will.v1.turned
import will.v1.word

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class RoomScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val phone = FakePhone()
    private val transport = FakeTransport()
    private val session = session(phone, transport)

    @Before
    fun openRoom() {
        session.start()
        transport.welcome()
        compose.setContent {
            val state by session.state.collectAsState()
            (state.view as? View.Room)?.let { RoomScreen(state, it, session) }
        }
        transport.tell(serverEvent { turned = turned { room = "Послушание — boris" } })
        transport.tell(serverEvent { word = word { id = 9; kind = Word.Kind.BEHEST; name = "boris"; body = "Пробежать 5 км" } })
        transport.tell(serverEvent { historyEnd = historyEnd {} })
    }

    @Test
    fun `a task is fulfilled in place, its card opened with a report, and closed as the server grants it`() {
        compose.onAllNodesWithText("Отчёт, если есть что сказать").assertCountEquals(0)
        compose.onNodeWithText("Пробежать 5 км").performClick()
        compose.onNodeWithText("Отчёт, если есть что сказать").performTextInput("Пробежал ")
        compose.onNodeWithText("Выполнить").performClick()
        val asked = transport.sent.last { it.hasFulfil() }
        assertEquals(9L, asked.fulfil.behestId)
        assertEquals("Пробежал", asked.fulfil.report)
        compose.onNodeWithText("Отправляется…").assertIsDisplayed()
        transport.answer(asked)
        compose.onAllNodesWithText("Выполнить").assertCountEquals(0)
    }
}
