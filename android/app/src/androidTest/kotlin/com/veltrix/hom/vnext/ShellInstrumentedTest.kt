package com.veltrix.hom.vnext

import android.util.Log
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToLog
import com.veltrix.hom.vnext.core.CapabilityRoute
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class ShellInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun fourPrimaryWorldsAndGlobalCapabilitiesRemainReachable() {
        val persistedSession = runBlocking {
            SessionStore(compose.activity.applicationContext).read()
        }
        Log.i(
            "VELTRIX_HOME_DIAG",
            "sessionPresent=${persistedSession != null} accountId=${persistedSession?.accountId ?: "none"}",
        )
        assertNotNull("Seeded session must survive into the real Activity test process", persistedSession)

        compose.onNodeWithTag("nav-HOME").assertIsDisplayed().assertHasClickAction()
        try {
            compose.waitUntil(8_000) {
                runCatching {
                    compose.onNodeWithTag("home-primary-action").assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
        } catch (failure: Throwable) {
            compose.onRoot(useUnmergedTree = true).printToLog("VELTRIX_HOME_TREE")
            Log.e("VELTRIX_HOME_DIAG", "Home primary action was not visible after 8s", failure)
            throw failure
        }
        compose.onNodeWithTag("home-screen").assertIsDisplayed()
        compose.onNodeWithTag("home-primary-action").assertIsDisplayed().assertHasClickAction()

        compose.onNodeWithTag("nav-PERSONAL").assertIsDisplayed().assertHasClickAction().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("personal-screen").assertIsDisplayed()
        compose.onNodeWithTag("active-route").assertTextEquals("Personal")

        compose.onNodeWithTag("nav-STORE").assertIsDisplayed().assertHasClickAction().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("active-route").assertTextEquals("Store")

        compose.onNodeWithTag("nav-PROJECTS").assertIsDisplayed().assertHasClickAction().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("projects-screen").assertIsDisplayed()
        compose.onNodeWithTag("active-route").assertTextEquals("Projects")

        assertEquals(11, CapabilityRoute.entries.size)
        compose.onNodeWithTag("open-capabilities").assertIsDisplayed().assertHasClickAction().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("capability-list").assertIsDisplayed()
        compose.onNodeWithTag("capability-list").performScrollToNode(hasTestTag("capability-CHAT"))
        compose.onNodeWithTag("capability-CHAT").assertIsDisplayed().assertHasClickAction().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("active-route").assertTextEquals("Chat")
    }
}
