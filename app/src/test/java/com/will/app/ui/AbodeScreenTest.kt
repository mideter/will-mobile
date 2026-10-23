package com.will.app.ui

import android.content.ClipboardManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import will.v1.MessengerOuterClass.Room
import will.v1.MessengerOuterClass.RoomAspect
import will.v1.MessengerOuterClass.RoomPart
import will.v1.MessengerOuterClass.Word
import will.v1.exercise
import will.v1.historyEnd
import will.v1.outstanding
import will.v1.outstandingBehest
import will.v1.room
import will.v1.rooms
import will.v1.serverEvent
import will.v1.supplications
import will.v1.turned
import will.v1.underway
import will.v1.word

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class AbodeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val phone = FakePhone()
    private val transport = FakeTransport()
    private val session = session(phone, transport)

    @Before
    fun wake() {
        session.start()
        transport.welcome("anna")
        compose.setContent {
            val state by session.state.collectAsState()
            var tab by remember { mutableStateOf(AbodeTab.Deeds) }
            (state.view as? View.Abode)?.let { AbodeScreen(state, it, session, tab) { tab = it } }
        }
    }

    private val ownRooms = listOf(
        room { name = "Слово"; part = RoomPart.INNER; aspect = RoomAspect.WORDS },
        room { name = "Врата"; part = RoomPart.OUTER; aspect = RoomAspect.THRESHOLD },
        room { name = "Кормчая"; part = RoomPart.INNER; aspect = RoomAspect.DWELLERS },
    )

    private fun abode(host: String, shown: List<Room>, owed: List<Pair<String, String>> = emptyList(), awaited: List<Pair<String, String>> = emptyList()) {
        transport.tell(serverEvent { turned = turned { abodeOf = host } })
        transport.tell(serverEvent { rooms = rooms { rooms += shown } })
        if (host.isEmpty()) {
            transport.tell(serverEvent {
                outstanding = outstanding {
                    owed.forEachIndexed { i, (body, room) -> this.owed += waiting(10L + i, body, room, going = i == 0, ended = i == 1) }
                    awaited.forEachIndexed { i, (body, room) -> this.awaited += waiting(20L + i, body, room, training = true) }
                }
            })
        }
        transport.tell(serverEvent { historyEnd = historyEnd {} })
    }

    private fun waiting(id: Long, body: String, room: String, going: Boolean = false, ended: Boolean = false, training: Boolean = false) = outstandingBehest {
        this.room = room
        counterpart = room.substringAfter("— ")
        behest = word {
            this.id = id; kind = Word.Kind.BEHEST; this.body = body
            if (training) exercises += exercise { name = "Присед" }
        }
        if (going || ended) underway = underway { behestId = id; doing = true; begunAtNs = 1; endedAtNs = if (ended) 2 else 0 }
    }

    private fun lastTurn() = transport.sent.last { it.hasTurn() }.turn

    @Test
    fun `one's own abode shows one's name and its rooms by parts, the core before the cells`() {
        abode("", ownRooms)
        compose.onNodeWithText("Моя Обитель").assertIsDisplayed()
        compose.onNodeWithText("Я — anna").assertIsDisplayed()
        compose.onNodeWithText("Комнаты").performClick()
        val inner = compose.onNodeWithText("Внутренняя часть").fetchSemanticsNode().boundsInRoot.top
        val helm = compose.onNodeWithText("Кормчая").fetchSemanticsNode().boundsInRoot.top
        val word = compose.onNodeWithText("Слово").fetchSemanticsNode().boundsInRoot.top
        val outer = compose.onNodeWithText("Внешняя часть").fetchSemanticsNode().boundsInRoot.top
        val gates = compose.onNodeWithText("Врата").fetchSemanticsNode().boundsInRoot.top
        assertEquals(listOf(inner, helm, word, outer, gates), listOf(inner, helm, word, outer, gates).sorted())
        compose.onNodeWithText("Слово").performClick()
        assertEquals("", lastTurn().abodeOf)
        assertEquals("Слово", lastTurn().room)
    }

    @Test
    fun `one's name is copied by a touch on the title`() {
        abode("", ownRooms)
        compose.onNodeWithText("Моя Обитель").performClick()
        val clipboard = RuntimeEnvironment.getApplication().getSystemService(ClipboardManager::class.java)
        assertEquals("anna", clipboard.primaryClip?.getItemAt(0)?.text.toString())
    }

    @Test
    fun `what awaits one and what one set for others are shown, whose and what, with the approach going or ended, and lead to their rooms`() {
        abode(
            "", ownRooms,
            owed = listOf("Понедельник" to "Послушание — boris", "Среда" to "Послушание — viktor"),
            awaited = listOf("Пятница" to "Ведение — gleb"),
        )
        compose.onNodeWithText("Ожидает внимания").assertIsDisplayed()
        compose.onNodeWithText("Задано другим").assertIsDisplayed()
        compose.onNodeWithText("boris · задание").assertIsDisplayed()
        compose.onNodeWithText("gleb · тренировка").assertIsDisplayed()
        compose.onNodeWithText("идёт подход").assertIsDisplayed()
        compose.onNodeWithText("подход закончен — как прошёл?").assertIsDisplayed()
        compose.onNodeWithText("Пятница").performClick()
        assertEquals("Ведение — gleb", lastTurn().room)
    }

    @Test
    fun `the supplications are among the deeds, counted with what awaits one only from the rooms, and lead to the helm`() {
        transport.tell(serverEvent { supplications = supplications { suppliantNames += listOf("boris", "viktor") } })
        abode("", ownRooms, owed = listOf("Понедельник" to "Послушание — gleb"))
        compose.onAllNodesWithText("3").assertCountEquals(0)
        compose.onNodeWithText("Комнаты").performClick()
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithText("Дела").performClick()
        compose.onNodeWithText("Просьбы").assertIsDisplayed()
        compose.onNodeWithText("viktor").performClick()
        assertEquals("Кормчая", lastTurn().room)
    }

    @Test
    fun `with nothing awaiting, the deeds say so`() {
        abode("", ownRooms)
        compose.onNodeWithText("Дел нет").assertIsDisplayed()
    }

    @Test
    fun `another's abode shows only the rooms open to one, and back leads to its gates`() {
        abode("boris", listOf(room { name = "Врата"; part = RoomPart.OUTER; aspect = RoomAspect.THRESHOLD }))
        compose.onNodeWithText("Обитель boris").assertIsDisplayed()
        compose.onNodeWithText("Вы обитаете здесь").assertIsDisplayed()
        compose.onAllNodesWithText("Слово").assertCountEquals(0)
        compose.onAllNodesWithText("Ожидает внимания").assertCountEquals(0)
        compose.onNodeWithText("←").performClick()
        assertEquals("boris", lastTurn().abodeOf)
        assertEquals("Врата", lastTurn().room)
    }

    @Test
    fun `no room open in another's abode, it says so`() {
        abode("boris", emptyList())
        compose.onNodeWithText("Ни одна комната вам здесь не открыта").assertIsDisplayed()
    }
}
