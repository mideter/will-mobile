package com.will.app.ui

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
import org.robolectric.annotation.Config
import will.v1.MessengerOuterClass.Dweller
import will.v1.MessengerOuterClass.DwellerKind
import will.v1.MessengerOuterClass.RoomAspect
import will.v1.MessengerOuterClass.RoomPart
import will.v1.dweller
import will.v1.dwellers
import will.v1.dwelling
import will.v1.dwellings
import will.v1.historyEnd
import will.v1.room
import will.v1.rooms
import will.v1.serverEvent
import will.v1.supplications
import will.v1.turned

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HelmScreenTest {

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
            (state.view as? View.Room)?.let { HelmScreen(state, it, session) }
        }
    }

    private fun cell(name: String, outer: Boolean) = room {
        this.name = name
        part = if (outer) RoomPart.OUTER else RoomPart.INNER
        aspect = RoomAspect.WORDS
    }

    private val cores = listOf(
        room { name = "Врата"; part = RoomPart.OUTER; aspect = RoomAspect.THRESHOLD },
        room { name = "Кормчая"; part = RoomPart.INNER; aspect = RoomAspect.DWELLERS },
    )

    private fun helm(people: List<Dweller> = emptyList(), asking: List<String> = emptyList(), dwellingIn: List<String> = emptyList()) {
        transport.tell(serverEvent { dwellings = dwellings { dwellingIn.forEach { dwellings += dwelling { hostName = it } } } })
        transport.tell(serverEvent { supplications = supplications { suppliantNames += asking } })
        transport.tell(serverEvent { turned = turned { room = "Кормчая"; aspect = RoomAspect.DWELLERS } })
        transport.tell(serverEvent { dwellers = dwellers { dwellers += people } })
        transport.tell(serverEvent { rooms = rooms { rooms += cell("Слово", outer = false); rooms += cores } })
        transport.tell(serverEvent { historyEnd = historyEnd {} })
    }

    private fun acquaintance(name: String, setup: will.v1.DwellerKt.Dsl.() -> Unit = {}) = dweller {
        this.name = name
        kind = DwellerKind.ACQUAINTANCE
        setup()
    }

    @Test
    fun `no one dwelling, it says where one lets in`() {
        helm()
        compose.onNodeWithText("В вашей Обители никто не обитает. Впускают у Врат.").assertIsDisplayed()
    }

    @Test
    fun `a supplication is accepted or rejected here`() {
        helm(people = listOf(acquaintance("boris") { asks = true }), asking = listOf("boris"))
        compose.onNodeWithText("Просьбы").assertIsDisplayed()
        compose.onNodeWithText("Принять").performClick()
        assertEquals("boris", transport.sent.last { it.hasAcceptSupplication() }.acceptSupplication.suppliantName)
        compose.onNodeWithText("Отклонить").performClick()
        assertEquals("boris", transport.sent.last { it.hasRejectSupplication() }.rejectSupplication.suppliantName)
    }

    @Test
    fun `a dweller is regarded anew by his kind, and his own kind asks nothing`() {
        helm(people = listOf(acquaintance("boris")))
        compose.onNodeWithText("знакомый").performClick()
        assertEquals(0, transport.sent.count { it.hasRegard() })
        compose.onNodeWithText("друг").performClick()
        val regard = transport.sent.last { it.hasRegard() }.regard
        assertEquals("boris", regard.name)
        assertEquals(DwellerKind.FRIEND, regard.kind)
    }

    @Test
    fun `one asks to be trained only one in whose abode one dwells, and not one who asks or trains already`() {
        val cases = listOf(
            Triple(acquaintance("boris"), true, null),
            Triple(acquaintance("viktor"), false, null),
            Triple(acquaintance("gleb") { trainer = true }, true, "Ваш Тренер"),
            Triple(acquaintance("dana") { asks = true }, true, "Просит вас стать его Тренером"),
            Triple(acquaintance("egor") { asked = true }, true, "Просьба отправлена, ждёт ответа"),
            Triple(acquaintance("zoya") { novice = true }, true, "Ваш ученик"),
        )
        val asks = mapOf("boris" to 1, "viktor" to 0, "gleb" to 0, "dana" to 0, "egor" to 0, "zoya" to 1)
        for ((dweller, dwelling, standing) in cases) {
            helm(people = listOf(dweller), dwellingIn = if (dwelling) listOf(dweller.name) else emptyList())
            compose.onNodeWithText(dweller.name).assertIsDisplayed()
            standing?.let { compose.onNodeWithText(it).assertIsDisplayed() }
            compose.onAllNodesWithText("Просить стать Тренером").assertCountEquals(asks.getValue(dweller.name))
        }
        helm(people = listOf(acquaintance("boris")), dwellingIn = listOf("boris"))
        compose.onNodeWithText("Просить стать Тренером").performClick()
        assertEquals("boris", transport.sent.last { it.hasSupplicate() }.supplicate.addresseeName)
    }


    @Test
    fun `cells are moved between the parts, and the gates and the helm are not`() {
        helm()
        compose.onNodeWithText("внутренняя часть").assertIsDisplayed()
        compose.onAllNodesWithText("Врата").assertCountEquals(0)
        compose.onNodeWithText("Во внешнюю").performClick()
        val arranged = transport.sent.last { it.hasArrange() }.arrange
        assertEquals("Слово", arranged.room)
        assertEquals(RoomPart.OUTER, arranged.part)

        // Сервер говорит комнаты заново — Келья уже во внешней части.
        transport.tell(serverEvent { rooms = rooms { rooms += cell("Слово", outer = true); rooms += cores } })
        compose.onNodeWithText("внешняя часть").assertIsDisplayed()
        compose.onNodeWithText("Во внутреннюю").assertIsDisplayed()
    }
}
