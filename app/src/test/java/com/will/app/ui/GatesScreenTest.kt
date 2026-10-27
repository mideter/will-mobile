package com.will.app.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.will.app.FakePhone
import com.will.app.FakeTransport
import com.will.app.View
import com.will.app.session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import will.v1.MessengerOuterClass.RoomPart
import will.v1.MessengerOuterClass.DwellerKind
import will.v1.dwelling
import will.v1.dwellings
import will.v1.historyEnd
import will.v1.serverEvent
import will.v1.threshold
import will.v1.turned
import will.v1.unborn

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class GatesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val phone = FakePhone()
    private val transport = FakeTransport()
    private val session = session(phone, transport)

    @Before
    fun wake() {
        session.start()
        transport.welcome()
        compose.setContent {
            val state by session.state.collectAsState()
            (state.view as? View.Room)?.let { GatesScreen(state, it, session) }
        }
    }

    private fun gates(host: String, open: Boolean, waiting: List<String> = emptyList(), unbornMarks: List<Long> = emptyList()) {
        transport.tell(serverEvent { turned = turned { abodeOf = host; edge = RoomPart.OUTER } })
        transport.tell(serverEvent { threshold = threshold { this.open = open; keeping = host.isEmpty(); this.waiting += waiting } })
        if (host.isEmpty()) transport.tell(serverEvent { unborn = unborn { marks += unbornMarks } })
        transport.tell(serverEvent { historyEnd = historyEnd {} })
    }

    private fun dwellingIn(host: String) =
        transport.tell(serverEvent { dwellings = dwellings { dwellings += dwelling { hostName = host; kind = DwellerKind.NEIGHBOUR } } })

    private fun lastTurn() = transport.sent.last { it.hasTurn() }.turn

    @Test
    fun `at one's own gates one lets in who knocks and bears the unborn`() {
        gates("", open = true, waiting = listOf("boris"), unbornMarks = listOf(4))
        compose.onNodeWithText("‹ anna").assertIsDisplayed()
        compose.onNodeWithText("Стучит").assertIsDisplayed()
        compose.onNodeWithText("Впустить").performClick()
        assertEquals("boris", transport.sent.last { it.hasAdmit() }.admit.name)
        compose.onNodeWithText("#4").assertIsDisplayed()
        compose.onNodeWithText("Дать").performClick()
        assertEquals(4L, transport.sent.last { it.hasBear() }.bear.mark)
    }

    @Test
    fun `no one knocking and no one awaiting birth, their sections are not shown, and one may still visit by name`() {
        gates("", open = true)
        compose.onAllNodesWithText("Стучат").assertCountEquals(0)
        compose.onAllNodesWithText("Дать имя").assertCountEquals(0)
        compose.onNodeWithText("по имени…").assertIsDisplayed()
    }

    @Test
    fun `from one's own gates one visits those who know one, by name`() {
        gates("", open = true)
        dwellingIn("viktor")
        compose.onNodeWithText("знает вас · ближний").assertIsDisplayed()
        compose.onNodeWithText("viktor").performClick()
        assertEquals("viktor", lastTurn().abodeOf)
        assertFalse(lastTurn().hasEdge())
        assertEquals("", lastTurn().room)
    }

    @Test
    fun `one visits by name, the row opened in place, and not without a name`() {
        gates("", open = true)
        compose.onNodeWithText("по имени…").performClick()
        compose.onNode(hasText("Посетить") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("Имя").performTextInput(" boris ")
        compose.onNode(hasText("Посетить") and hasClickAction()).performClick()
        assertEquals("boris", lastTurn().abodeOf)
        assertFalse(lastTurn().hasEdge())
        assertEquals("", lastTurn().room)
    }

    @Test
    fun `at another's gates a dweller goes in`() {
        dwellingIn("viktor")
        gates("viktor", open = false)
        compose.onNodeWithText("viktor").assertIsDisplayed()
        compose.onNodeWithText("‹ Врата — anna").assertIsDisplayed()
        compose.onAllNodesWithText("Стук").assertCountEquals(0)
        compose.onNodeWithText("Войти").performClick()
        assertEquals("viktor", lastTurn().abodeOf)
        assertEquals("", lastTurn().room)
    }

    @Test
    fun `one who has come knocks once, hears that the host hears, and once let in goes in`() {
        gates("viktor", open = false)
        compose.onAllNodesWithText("Войти").assertCountEquals(0)
        compose.onNodeWithText("Стук").performClick()
        assertTrue(transport.sent.last().hasKnock())
        transport.tell(serverEvent { threshold = threshold { knocking = true } })
        compose.onAllNodesWithText("Стук").assertCountEquals(0)
        compose.onNodeWithText("Вы постучали.").assertIsDisplayed()
        transport.tell(serverEvent { threshold = threshold { open = true; knocking = true } })
        compose.onNodeWithText("Хозяин слышит ваш стук.").assertIsDisplayed()
        dwellingIn("viktor")
        compose.onNodeWithText("Войти").assertIsDisplayed()
    }
}
