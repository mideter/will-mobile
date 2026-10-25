package com.will.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class NamedScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the name given is said with who gave it, and one begins by touching the name`() {
        var begun = 0
        compose.setContent { NamedScreen("b66d1mpo", "ih7wzipz") { begun++ } }
        compose.onNodeWithText("Теперь у вас есть имя").assertIsDisplayed()
        compose.onNodeWithText("Его дал ih7wzipz.").assertIsDisplayed()
        compose.onNodeWithText("ваше имя").assertIsDisplayed()
        compose.onAllNodesWithText("Начать", substring = true).assertCountEquals(0)
        compose.onNodeWithText("b66d1mpo").performClick()
        assertEquals(1, begun)
    }
}
