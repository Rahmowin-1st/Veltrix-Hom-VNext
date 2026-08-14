package com.veltrix.hom.vnext

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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
        waitForActiveRoute("CHAT")
        compose.onNodeWithTag("capability-CHAT").assertIsDisplayed()

        CapabilityRoute.entries.drop(1).forEachIndexed { offset, route ->
            val index = offset + 1
            val tag = "capability-${route.name}"
            // Drive the LazyColumn through its own scroll semantics. Child performScrollTo()
            // can decide an item is visible enough while its clickable bounds remain clipped.
            compose.onNodeWithTag("capability-list").performScrollToIndex(index)
            compose.waitForIdle()
            compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
            waitForActiveRoute(route.name)
        }
    }

    private fun waitForActiveRoute(expected: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                compose.onNodeWithTag("active-route").assertTextEquals(expected)
                true
            }.getOrDefault(false)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("active-route").assertTextEquals(expected).assertIsDisplayed()
    }
}
