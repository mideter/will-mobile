package com.will.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
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
import will.v1.MessengerOuterClass.ClientEvent
import will.v1.MessengerOuterClass.Word
import will.v1.approach
import will.v1.effort
import will.v1.exerted
import will.v1.exercise
import will.v1.historyEnd
import will.v1.remark
import will.v1.remarked
import will.v1.reply
import will.v1.serverEvent
import will.v1.turned
import will.v1.underway
import will.v1.word

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class TrainingRunTest {

    @get:Rule
    val compose = createComposeRule()

    private val phone = FakePhone()
    private val transport = FakeTransport()
    private val session = session(phone, transport)

    private val id = 7L
    private val begunNs get() = phone.nowMs * 1_000_000

    @Before
    fun openRoom() {
        session.start()
        transport.welcome()
        compose.setContent {
            val state by session.state.collectAsState()
            (state.view as? View.Room)?.let { RoomScreen(state, it, session) }
        }
    }

    private fun room(count: Int) {
        transport.tell(serverEvent { turned = turned { room = "Послушание — boris" } })
        transport.tell(serverEvent {
            word = word {
                id = this@TrainingRunTest.id
                kind = Word.Kind.BEHEST
                name = "boris"
                body = "Понедельник"
                exercises += exercise {
                    name = "Присед"
                    repeat(count) { i -> approaches += approach { weightGrams = 40_000; repetitions = 5 + i; restSeconds = 60 } }
                }
            }
        })
        transport.tell(serverEvent { historyEnd = historyEnd {} })
        compose.onNodeWithText("Понедельник").performClick()
    }

    private fun last(case: (ClientEvent) -> Boolean) = transport.sent.last(case)

    private fun begin(approach: Int) {
        compose.onNodeWithText("Приступить").performClick()
        val asked = last { it.hasBeginApproach() }
        assertEquals(approach, asked.beginApproach.approach)
        transport.tell(serverEvent { underway = underway { behestId = id; this.approach = approach; begunAtNs = begunNs; doing = true } })
        transport.answer(asked)
    }

    private fun end(approach: Int) {
        compose.onNodeWithText("Завершить подход").performClick()
        last { it.hasEndApproach() }
        transport.tell(serverEvent {
            underway = underway { behestId = id; this.approach = approach; begunAtNs = begunNs; endedAtNs = begunNs; doing = true }
        })
    }

    private fun serveTelling(approach: Int, told: ClientEvent) {
        val said = told.finishApproach
        transport.tell(serverEvent {
            exerted = exerted {
                behestId = id
                effort = effort {
                    this.approach = approach
                    weightGrams = said.weightGrams
                    repetitions = said.repetitions
                    strain = said.strain
                    begunAtNs = begunNs
                    finishedAtNs = begunNs
                }
            }
        })
        transport.tell(serverEvent { underway = underway { behestId = id } })
        transport.answer(told)
    }

    @Test
    fun `an approach is begun, told, and the panel waits for the server before it closes`() {
        room(count = 2)
        compose.onNodeWithText("Далее подход 1 · 40 кг × 5").assertIsDisplayed()
        begin(0)
        compose.onNodeWithText("Подход 1").assertIsDisplayed()
        // В подходе цель видна: крупно на пульте и в строке подхода, с тем, сколько он идёт.
        compose.onNodeWithText("40 кг × 5").assertIsDisplayed()
        compose.onNodeWithText("40 кг × 5 · идёт", substring = true).assertIsDisplayed()
        end(0)
        compose.onNodeWithText("Как прошёл подход 1").assertIsDisplayed()

        compose.onNodeWithText("Готово").performClick()
        val told = last { it.hasFinishApproach() }
        assertEquals(40_000, told.finishApproach.weightGrams)
        assertEquals(5, told.finishApproach.repetitions)
        compose.onNodeWithText("Отправляется…").assertIsNotEnabled()
        compose.onNodeWithText("Как прошёл подход 1").assertIsDisplayed()

        serveTelling(0, told)
        compose.onAllNodesWithText("Как прошёл подход 1").assertCountEquals(0)
        compose.onNodeWithText("Отдых · далее подход 2 · 40 кг × 6").assertIsDisplayed()
    }

    @Test
    fun `refused, the telling stays with what was said, and is said again as it was`() {
        room(count = 2)
        begin(0)
        end(0)
        compose.onAllNodesWithText("−")[1].performClick()
        compose.onNodeWithText("!! тяжело").performClick()
        compose.onNodeWithText("Готово").performClick()
        transport.refuse(last { it.hasFinishApproach() })

        compose.onNodeWithText("Как прошёл подход 1").assertIsDisplayed()
        compose.onNodeWithText("4").assertIsDisplayed()
        compose.onNodeWithText("Готово").performClick()
        val again = last { it.hasFinishApproach() }
        assertEquals(4, again.finishApproach.repetitions)
        assertEquals(2, again.finishApproach.strain)
    }

    @Test
    fun `told, the next approach begins without the telling standing in the way`() {
        room(count = 2)
        begin(0)
        end(0)
        compose.onNodeWithText("Готово").performClick()
        serveTelling(0, last { it.hasFinishApproach() })
        begin(1)
        compose.onNodeWithText("Завершить подход").assertIsDisplayed()
        compose.onAllNodesWithText("Как прошёл подход 2").assertCountEquals(0)
    }

    @Test
    fun `a remark stays in its field until the server takes it`() {
        room(count = 2)
        compose.onNodeWithText("заметка").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Колено")
        compose.onNodeWithText("↑").performClick()
        val said = last { it.hasSayRemark() }
        assertEquals("Колено", said.sayRemark.body)
        compose.onNodeWithText("↑").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()

        transport.tell(serverEvent { remarked = remarked { behestId = id; remark = remark { body = "Колено"; saidAtNs = begunNs } } })
        transport.tell(serverEvent { reply = reply { requestId = said.requestId; granted = true } })
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Колено").assertIsDisplayed()
    }

    @Test
    fun `all willed done, the panel says so once and offers to finish`() {
        room(count = 1)
        begin(0)
        end(0)
        compose.onNodeWithText("Готово").performClick()
        serveTelling(0, last { it.hasFinishApproach() })
        compose.onNodeWithText("Все заданные подходы сделаны — можно добавить подход сверх заданного").assertIsDisplayed()
        compose.onNodeWithText("Завершить тренировку").assertIsDisplayed()
        compose.onAllNodesWithText("Приступить").assertCountEquals(0)
    }
}
