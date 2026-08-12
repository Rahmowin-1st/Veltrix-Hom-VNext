package com.veltrix.hom.vnext

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class ShellInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun primaryDestinationsAndCapabilitiesAreReachable() {
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.onNodeWithText("Personal").assertIsDisplayed()
        compose.onNodeWithText("Store").assertIsDisplayed()
        compose.onNodeWithText("Projects").performClick()
        compose.onNodeWithText("Projects — functional local persistence harness").assertIsDisplayed()
        compose.onNodeWithText("Capabilities").performClick()
        compose.onNodeWithText("CHAT").assertIsDisplayed()
        compose.onNodeWithText("LIBRARY").assertIsDisplayed()
        compose.onNodeWithText("TESTING").assertIsDisplayed()
        compose.onNodeWithText("PRACTICE").assertIsDisplayed()
        compose.onNodeWithText("QUIZZES").assertIsDisplayed()
        compose.onNodeWithText("FLASHCARDS").assertIsDisplayed()
        compose.onNodeWithText("MISTAKES").assertIsDisplayed()
        compose.onNodeWithText("CALCULATOR").assertIsDisplayed()
        compose.onNodeWithText("TRANSLATE").assertIsDisplayed()
        compose.onNodeWithText("NOTIFICATIONS").assertIsDisplayed()
        compose.onNodeWithText("SETTINGS").assertIsDisplayed()
    }
}
