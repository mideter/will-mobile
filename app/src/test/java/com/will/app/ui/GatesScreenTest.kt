package com.will.app.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import will.v1.MessengerOuterClass.DwellerKind
import will.v1.MessengerOuterClass.RoomAspect
import will.v1.dwelling
import will.v1.dwellings
import will.v1.historyEnd
import will.v1.serverEvent
import will.v1.threshold
import will.v1.turned
import will.v1.unborn

// Диалог «К чужим Вратам» здесь не пробуют: под Robolectric окно диалога не даёт Compose успокоиться.
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
        transport.tell(serverEvent { turned = turned { abodeOf = host; room = "Врата"; aspect = RoomAspect.THRESHOLD } })
        transport.tell(serverEvent { threshold = threshold { this.open = open; keeping = host.isEmpty(); this.waiting += waiting } })
        if (host.isEmpty()) transport.tell(serverEvent { unborn = unborn { marks += unbornMarks } })
        transport.tell(serverEvent { historyEnd = historyEnd {} })
    }

    private fun dwellingIn(host: String) =
        transport.tell(serverEvent { dwellings = dwellings { dwellings += dwelling { hostName = host; kind = DwellerKind.NEIGHBOUR } } })

    private fun lastTurn() = transport.sent.last { it.hasTurn() }.turn

    @Test
    fun `at one's own gates one lets in who waits and bears the unborn`() {
        gates("", open = true, waiting = listOf("boris"), unbornMarks = listOf(4))
        compose.onNodeWithText("‹ anna").assertIsDisplayed()
        compose.onNodeWithText("Впустить").performClick()
        assertEquals("boris", transport.sent.last { it.hasAdmit() }.admit.name)
        compose.onNodeWithText("#4").assertIsDisplayed()
        compose.onNodeWithText("Родить").performClick()
        assertEquals(4L, transport.sent.last { it.hasBear() }.bear.mark)
    }

    @Test
    fun `no one waiting and no one awaiting birth, it says so`() {
        gates("", open = true)
        compose.onAllNodesWithText("Никто не ждёт").assertCountEquals(2)
        compose.onNodeWithText("Вас пока никто не знает").assertIsDisplayed()
    }

    @Test
    fun `from one's own gates one goes out to the abodes one dwells in`() {
        gates("", open = true)
        dwellingIn("viktor")
        compose.onNodeWithText("знает вас · ближний").assertIsDisplayed()
        compose.onNodeWithText("viktor").performClick()
        assertEquals("viktor", lastTurn().abodeOf)
        assertEquals("Врата", lastTurn().room)
    }

    @Test
    fun `at another's gates a dweller goes in`() {
        dwellingIn("viktor")
        gates("viktor", open = false)
        compose.onNodeWithText("Врата — viktor").assertIsDisplayed()
        compose.onNodeWithText("‹ Врата — anna").assertIsDisplayed()
        compose.onNodeWithText("Врата закрыты: хозяина нет у Врат.").assertIsDisplayed()
        compose.onNodeWithText("viktor знает вас — ближний.").assertIsDisplayed()
        compose.onNodeWithText("Войти").performClick()
        assertEquals("viktor", lastTurn().abodeOf)
        assertEquals("", lastTurn().room)
    }

    @Test
    fun `one who has come waits, sees the host come to the gates, and once let in goes in`() {
        gates("viktor", open = false)
        compose.onNodeWithText("Можно ждать здесь: подойдя к Вратам, хозяин увидит вас.").assertIsDisplayed()
        compose.onAllNodesWithText("Войти").assertCountEquals(0)
        transport.tell(serverEvent { threshold = threshold { open = true } })
        compose.onNodeWithText("Врата открыты: хозяин у Врат.").assertIsDisplayed()
        compose.onNodeWithText("Хозяин видит вас: ждите, пока впустит.").assertIsDisplayed()
        dwellingIn("viktor")
        compose.onNodeWithText("Войти").assertIsDisplayed()
    }
}
