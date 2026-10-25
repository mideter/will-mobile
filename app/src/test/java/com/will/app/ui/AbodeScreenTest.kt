package com.will.app.ui

import android.content.ClipboardManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import will.v1.effort
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
            (state.view as? View.Abode)?.let { AbodeScreen(state, it, session) }
        }
    }

    private val ownRooms = listOf(
        room { name = "Слово"; part = RoomPart.INNER; aspect = RoomAspect.WORDS },
        room { name = "Врата"; part = RoomPart.OUTER; aspect = RoomAspect.THRESHOLD },
        room { name = "Кормчая"; part = RoomPart.INNER; aspect = RoomAspect.DWELLERS },
    )

    private fun taught(by: String) = room { name = "Послушание — $by"; part = RoomPart.INNER; aspect = RoomAspect.WORDS; testator = by }

    private fun teaching(whom: String) = room { name = "Ведение — $whom"; part = RoomPart.INNER; aspect = RoomAspect.WORDS }

    private fun abode(
        host: String,
        shown: List<Room>,
        owed: List<Pair<String, String>> = emptyList(),
        awaited: List<Pair<String, String>> = emptyList(),
        unreviewed: List<Pair<String, String>> = emptyList(),
        waiting: Int = 0,
    ) {
        transport.tell(serverEvent { turned = turned { abodeOf = host } })
        transport.tell(serverEvent { rooms = rooms { rooms += shown } })
        if (host.isEmpty()) {
            transport.tell(serverEvent {
                outstanding = outstanding {
                    owed.forEachIndexed { i, (body, room) -> this.owed += waiting(10L + i, body, room, going = i == 0, ended = i == 1) }
                    awaited.forEachIndexed { i, (body, room) -> this.awaited += waiting(20L + i, body, room, training = true) }
                    unreviewed.forEachIndexed { i, (body, room) -> this.unreviewed += waiting(30L + i, body, room) }
                    this.waiting = waiting
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
        compose.onNodeWithText("anna").assertIsDisplayed()
        compose.onNodeWithText("дома").assertIsDisplayed()
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
        compose.onNodeWithText("anna").performClick()
        val clipboard = RuntimeEnvironment.getApplication().getSystemService(ClipboardManager::class.java)
        assertEquals("anna", clipboard.primaryClip?.getItemAt(0)?.text.toString())
    }

    @Test
    fun `under a tie room is said what waits in it, with the approach going or ended, and the room is entered`() {
        abode(
            "", ownRooms + taught("boris") + taught("viktor") + teaching("gleb"),
            owed = listOf("Понедельник" to "Послушание — boris", "Среда" to "Послушание — viktor"),
            awaited = listOf("Пятница" to "Ведение — gleb"),
        )
        compose.onAllNodesWithText("выполнить 1").assertCountEquals(2)
        compose.onNodeWithText("идёт подход").assertIsDisplayed()
        compose.onNodeWithText("подход закончен — как прошёл?").assertIsDisplayed()
        compose.onNodeWithText("ожидаю 1").assertIsDisplayed()
        compose.onNodeWithText("Тренер — boris").performClick()
        assertEquals("Послушание — boris", lastTurn().room)
    }

    @Test
    fun `a training begun and resting is said under its room, as an approach underway is`() {
        transport.tell(serverEvent { turned = turned { abodeOf = "" } })
        transport.tell(serverEvent { rooms = rooms { rooms += ownRooms + taught("boris") } })
        val finished = session.clock.nowNs() - 60_000_000_000L
        val owedTraining = waiting(10L, "Понедельник", "Послушание — boris", training = true)
        val rested = owedTraining.behest.toBuilder()
            .addEfforts(effort { weightGrams = 40_000; repetitions = 5; begunAtNs = finished - 30_000_000_000L; finishedAtNs = finished })
        transport.tell(serverEvent { outstanding = outstanding { owed += owedTraining.toBuilder().setBehest(rested).build() } })
        transport.tell(serverEvent { historyEnd = historyEnd {} })
        compose.onNodeWithText("выполнить 1").assertIsDisplayed()
        compose.onNodeWithText("идёт отдых").assertIsDisplayed()
    }

    @Test
    fun `the supplications are said in red under the helm, which they lead to`() {
        transport.tell(serverEvent { supplications = supplications { suppliantNames += listOf("boris", "viktor") } })
        abode("", ownRooms)
        compose.onNodeWithText("boris, viktor просят стать Тренером").assertIsDisplayed()
        compose.onNodeWithText("Кормчая").performClick()
        assertEquals("Кормчая", lastTurn().room)
    }

    @Test
    fun `that some knock at the gates is said under them, without names, and they are entered`() {
        abode("", ownRooms, waiting = 1)
        compose.onNodeWithText("стук").assertIsDisplayed()
        abode("", ownRooms, waiting = 3)
        compose.onNodeWithText("стук · 3").assertIsDisplayed()
        compose.onNodeWithText("Врата").performClick()
        assertEquals("Врата", lastTurn().room)
    }

    @Test
    fun `what was done and not yet seen is to review, said before what one awaits in the same room`() {
        abode(
            "", ownRooms + teaching("viktor"),
            awaited = listOf("Пятница" to "Ведение — viktor"),
            unreviewed = listOf("Среда" to "Ведение — viktor"),
        )
        compose.onNodeWithText("проверить 1 · ожидаю 1").assertIsDisplayed()
    }

    @Test
    fun `what waits is told anew while one looks at the abode`() {
        abode("", ownRooms + teaching("gleb"), awaited = listOf("Пятница" to "Ведение — gleb"))
        compose.onNodeWithText("ожидаю 1").assertIsDisplayed()
        transport.tell(serverEvent {
            outstanding = outstanding { unreviewed += waiting(20L, "Пятница", "Ведение — gleb", training = true) }
        })
        compose.onNodeWithText("проверить 1").assertIsDisplayed()
        compose.onAllNodesWithText("ожидаю 1").assertCountEquals(0)
    }

    @Test
    fun `another's abode shows only the rooms open to one, and back leads to its gates`() {
        abode("boris", listOf(room { name = "Врата"; part = RoomPart.OUTER; aspect = RoomAspect.THRESHOLD }))
        compose.onNodeWithText("boris").assertIsDisplayed()
        compose.onAllNodesWithText("Слово").assertCountEquals(0)
        compose.onNodeWithText("‹ Врата").performClick()
        assertEquals("boris", lastTurn().abodeOf)
        assertEquals("Врата", lastTurn().room)
    }

    @Test
    fun `no room open in another's abode, it says so`() {
        abode("boris", emptyList())
        compose.onNodeWithText("Ни одна комната вам здесь не открыта").assertIsDisplayed()
    }

    @Test
    fun `a room where one is taught is named by one's trainer, and entered by its own name`() {
        abode("", ownRooms + taught("boris"))
        compose.onAllNodesWithText("Послушание — boris").assertCountEquals(0)
        compose.onNodeWithText("Тренер — boris").performClick()
        assertEquals("Послушание — boris", lastTurn().room)
    }
}
