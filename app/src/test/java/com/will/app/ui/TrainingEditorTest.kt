package com.will.app.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onLast
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
import will.v1.approach
import will.v1.exercise
import will.v1.historyEnd
import will.v1.serverEvent
import will.v1.turned
import will.v1.word

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class TrainingEditorTest {

    @get:Rule
    val compose = createComposeRule()

    private val phone = FakePhone()
    private val transport = FakeTransport()
    private val session = session(phone, transport)

    @Before
    fun openShepherding() {
        session.start()
        transport.welcome()
        transport.tell(serverEvent { turned = turned { room = "Ведение — boris"; writable = true } })
        // Прежняя тренировка: её упражнения — подсказка для названий.
        transport.tell(serverEvent {
            word = word {
                id = 3
                kind = Word.Kind.BEHEST
                isMine = true
                name = "anna"
                body = "Понедельник"
                exercises += exercise { name = "Присед"; approaches += approach { weightGrams = 40_000; repetitions = 5 } }
            }
        })
        transport.tell(serverEvent { historyEnd = historyEnd {} })
        compose.setContent {
            val state by session.state.collectAsState()
            (state.view as? View.Room)?.let { RoomScreen(state, it, session) }
        }
        compose.onNodeWithText("+ Тренировка").performClick()
    }

    private fun field(index: Int) = compose.onAllNodes(hasSetTextAction())[index]

    private fun ask(): SemanticsNodeInteraction = compose.onNode(hasText("Задать") and hasClickAction())

    private fun trained() = transport.sent.last { it.hasTrain() }

    private fun addExercise() {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("+ упражнение"))
        compose.onNodeWithText("+ упражнение").performClick()
    }

    @Test
    fun `without a name to every exercise the training is not set`() {
        compose.onNodeWithText("Новая тренировка").assertIsDisplayed()
        compose.onNodeWithText("У каждого упражнения должно быть название").assertIsDisplayed()
        ask().assertIsNotEnabled()
        field(1).performTextInput("Жим")
        ask().assertIsEnabled()
        addExercise()
        ask().assertIsNotEnabled()
    }

    @Test
    fun `the training is set as composed, and the editor waits for the server before it closes`() {
        field(0).performTextInput("Среда")
        field(1).performTextInput("Жим")
        field(2).performTextInput("Лопатки сведены")
        repeat(4) { compose.onAllNodesWithText("+")[1].performClick() }
        compose.onAllNodesWithText("−")[2].performClick()
        compose.onNodeWithText("+ подход").performClick()
        ask().performClick()

        val train = trained().train
        assertEquals("Среда", train.title)
        val press = train.exercisesList.single()
        assertEquals("Жим", press.name)
        assertEquals("Лопатки сведены", press.instruction)
        assertEquals(listOf(4_000 to 9, 4_000 to 9), press.approachesList.map { it.weightGrams to it.repetitions })
        compose.onNodeWithText("Отправляется…").assertIsDisplayed()
        ask().assertIsNotEnabled()

        transport.answer(trained())
        compose.onAllNodesWithText("Новая тренировка").assertCountEquals(0)
    }

    @Test
    fun `refused, the editor stays with what was composed`() {
        field(0).performTextInput("Среда")
        field(1).performTextInput("Жим")
        ask().performClick()
        transport.refuse(trained())
        compose.onNodeWithText("Новая тренировка").assertIsDisplayed()
        compose.onNodeWithText("Среда").assertIsDisplayed()
        ask().assertIsEnabled()
    }

    @Test
    fun `an exercise and an approach are removed while more than one remains`() {
        compose.onAllNodesWithText("✕").assertCountEquals(0)
        compose.onNodeWithText("+ подход").performClick()
        compose.onAllNodesWithText("✕").assertCountEquals(2)
        compose.onAllNodesWithText("✕")[0].performClick()
        compose.onAllNodesWithText("✕").assertCountEquals(0)
        addExercise()
        compose.onAllNodesWithText("Упражнение").assertCountEquals(2)
        compose.onAllNodesWithText("✕")[1].performClick()
        compose.onAllNodesWithText("Упражнение").assertCountEquals(1)
    }

    @Test
    fun `an exercise named before is offered as one types`() {
        field(1).performTextInput("при")
        compose.onNodeWithText("Присед").performClick()
        field(1).assert(hasText("Присед"))
    }

    @Test
    fun `a number is typed in place, and what is not a number is not taken`() {
        field(0).performTextInput("Среда")
        field(1).performTextInput("Жим")
        compose.onAllNodesWithText("10")[0].performClick()
        compose.onAllNodes(hasSetTextAction()).onLast().performTextReplacement("12")
        compose.onAllNodes(hasSetTextAction()).onLast().performImeAction()
        compose.onAllNodesWithText("12")[0].performClick()
        compose.onAllNodes(hasSetTextAction()).onLast().performTextReplacement("x")
        compose.onAllNodes(hasSetTextAction()).onLast().performImeAction()
        ask().performClick()
        assertEquals(12, trained().train.exercisesList.single().approachesList.single().repetitions)
    }

    @Test
    fun `a number typed is taken though the field is not left before the training is set`() {
        field(0).performTextInput("Среда")
        field(1).performTextInput("Жим")
        compose.onAllNodesWithText("10")[0].performClick()
        compose.onAllNodes(hasSetTextAction()).onLast().performTextReplacement("12")
        ask().performClick()
        assertEquals(12, trained().train.exercisesList.single().approachesList.single().repetitions)
    }

    @Test
    fun `a step while typing goes from the number typed`() {
        field(0).performTextInput("Среда")
        field(1).performTextInput("Жим")
        compose.onAllNodesWithText("10")[0].performClick()
        compose.onAllNodes(hasSetTextAction()).onLast().performTextReplacement("12")
        compose.onAllNodesWithText("+")[2].performClick()
        compose.onNodeWithText("13").assertIsDisplayed()
        ask().performClick()
        assertEquals(13, trained().train.exercisesList.single().approachesList.single().repetitions)
    }
}
