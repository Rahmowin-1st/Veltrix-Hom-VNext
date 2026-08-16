package com.veltrix.hom.vnext

import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
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
import androidx.test.platform.app.InstrumentationRegistry
import com.veltrix.hom.vnext.core.CapabilityRoute
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ShellInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    /**
     * Execute the same Android shell input path used by ADB, but derive the tap from the exact
     * Compose semantic hit target translated through the real ComposeView screen position.
     * This catches z-order/inset/hit-target regressions without relying on accessibility-window
     * availability inside the instrumentation process.
     */
    private fun physicalShellTap(tag: String) {
        val interaction = compose.onNodeWithTag(tag).assertIsDisplayed().assertHasClickAction()
        val bounds = interaction.fetchSemanticsNode().boundsInRoot
        assertTrue("Physical target '$tag' must have positive semantic bounds: $bounds", bounds.width > 0f && bounds.height > 0f)

        val content = compose.activity.findViewById<ViewGroup>(android.R.id.content)
        val composeRoot = content.getChildAt(0) ?: content
        val screenOffset = IntArray(2)
        composeRoot.getLocationOnScreen(screenOffset)
        val x = (screenOffset[0] + (bounds.left + bounds.right) / 2f).toInt()
        val y = (screenOffset[1] + (bounds.top + bounds.bottom) / 2f).toInt()
        assertTrue("Physical shell tap x must be on-screen: $x", x >= 0)
        assertTrue("Physical shell tap y must be on-screen: $y", y >= 0)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        Log.i("VELTRIX_SHELL_TOUCH", "tag=$tag rootOffset=${screenOffset.contentToString()} bounds=$bounds tap=[$x,$y]")
        instrumentation.uiAutomation.executeShellCommand("input tap $x $y").close()
        SystemClock.sleep(180)
        instrumentation.waitForIdleSync()
        compose.waitForIdle()
    }

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

        compose.onNodeWithTag("nav-HOME").assertIsDisplayed()
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
        compose.onNodeWithTag("active-route").assertTextEquals("Store")

        compose.onNodeWithTag("nav-PROJECTS").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("projects-screen").assertIsDisplayed()
        compose.onNodeWithTag("active-route").assertTextEquals("Projects")

        assertEquals(11, CapabilityRoute.entries.size)

        // Physical shell tap after traversing all four worlds. The drawer must really open.
        physicalShellTap("open-capabilities")
        compose.waitUntil(5_000) {
            runCatching {
                compose.onNodeWithTag("capability-list").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        compose.onNodeWithTag("capability-list").performScrollToNode(hasTestTag("capability-CHAT"))
        compose.onNodeWithTag("capability-CHAT").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("active-route").assertTextEquals("Chat")
    }
}
