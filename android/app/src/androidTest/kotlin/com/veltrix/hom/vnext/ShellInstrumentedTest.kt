package com.veltrix.hom.vnext

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.veltrix.hom.vnext.core.CapabilityRoute
import org.junit.Assert.assertEquals
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
        val required = listOf("CHAT", "LIBRARY", "TESTING", "PRACTICE", "QUIZZES", "FLASHCARDS", "MISTAKES", "CALCULATOR", "TRANSLATE", "NOTIFICATIONS", "SETTINGS")
        assertEquals(required, CapabilityRoute.entries.map { it.name })
        compose.onNodeWithText("Capabilities").performClick()
        compose.onNodeWithText("Route contract reachable. Feature business logic belongs to repositories/domain services, not this composable.").assertIsDisplayed()
        CapabilityRoute.entries.drop(1).forEach { route ->
            compose.onNodeWithText(route.name).performScrollTo().performClick()
            compose.onNodeWithText("Route contract reachable. Feature business logic belongs to repositories/domain services, not this composable.").assertIsDisplayed()
        }
    }
}
