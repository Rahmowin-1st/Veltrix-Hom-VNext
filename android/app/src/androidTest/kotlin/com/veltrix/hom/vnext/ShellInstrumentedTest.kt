package com.veltrix.hom.vnext

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
        compose.waitForIdle()
        compose.onNodeWithTag("active-route").assertTextEquals("PROJECTS").assertIsDisplayed()
        compose.onNodeWithText("Projects — functional local persistence harness").assertIsDisplayed()

        val required = CapabilityRoute.entries.map { it.name }
        assertEquals(11, required.size)
        assertEquals("CHAT", required.first())
        assertEquals("SETTINGS", required.last())

        compose.onNodeWithTag("open-capabilities").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("active-route").assertTextEquals("CHAT").assertIsDisplayed()
        compose.onNodeWithTag("capability-CHAT").assertIsDisplayed()

        CapabilityRoute.entries.drop(1).forEach { route ->
            compose.onNodeWithTag("capability-${route.name}").performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("active-route").assertTextEquals(route.name).assertIsDisplayed()
        }
    }
}
