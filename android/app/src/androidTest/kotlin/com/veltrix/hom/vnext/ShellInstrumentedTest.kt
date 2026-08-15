package com.veltrix.hom.vnext

import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToLog
import com.veltrix.hom.vnext.core.CapabilityRoute
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class ShellInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test
    fun fourPrimaryWorldsAndGlobalCapabilitiesRemainReachable(){
        val persistedSession = runBlocking {
            SessionStore(compose.activity.applicationContext).read()
        }
        Log.i(
            "VELTRIX_HOME_DIAG",
            "sessionPresent=${persistedSession != null} accountId=${persistedSession?.accountId ?: "none"}",
        )
        assertNotNull("Seeded session must survive into the real Activity test process", persistedSession)

        compose.onNodeWithTag("nav-HOME").assertIsDisplayed()

        // Part 2 intentionally delegates its unresolved/loading state to the accepted Part 1
        // Home state family. During that short handoff both wrappers may temporarily expose the
        // same diagnostic test tag. The acceptance target is the loaded real Home world, so wait
        // for its authoritative primary action first, then keep the home-screen check strict.
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