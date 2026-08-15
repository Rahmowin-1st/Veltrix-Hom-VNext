package com.veltrix.hom.vnext

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.veltrix.hom.vnext.core.CapabilityRoute
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ShellInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test
    fun fourPrimaryWorldsAndGlobalCapabilitiesRemainReachable(){
        compose.onNodeWithTag("nav-HOME").assertIsDisplayed()
        // Real Activity + API 36 viewport owns visibility proof for the primary Home CTA.
        compose.waitUntil(8_000) {
            runCatching {
                compose.onNodeWithTag("home-primary-action").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        compose.onNodeWithTag("home-primary-action").assertIsDisplayed()

        compose.onNodeWithTag("nav-PERSONAL").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("personal-screen").assertIsDisplayed()

        compose.onNodeWithTag("nav-STORE").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("active-route").assertIsDisplayed()

        compose.onNodeWithTag("nav-PROJECTS").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("projects-screen").assertIsDisplayed()

        assertEquals(11,CapabilityRoute.entries.size)
        compose.onNodeWithTag("open-capabilities").performClick()
        compose.waitUntil(5_000) {
            runCatching {
                compose.onNodeWithTag("capability-CHAT").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        compose.onNodeWithTag("capability-CHAT").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("active-route").assertIsDisplayed()
    }
}
