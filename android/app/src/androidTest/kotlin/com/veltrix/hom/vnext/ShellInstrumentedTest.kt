package com.veltrix.hom.vnext

import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
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
import kotlin.math.roundToInt

class ShellInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    private fun physicalTap(tag: String) {
        val interaction = compose.onNodeWithTag(tag).assertIsDisplayed().assertHasClickAction()
        val bounds = interaction.fetchSemanticsNode().boundsInRoot
        val x = ((bounds.left + bounds.right) / 2f).roundToInt()
        val y = ((bounds.top + bounds.bottom) / 2f).roundToInt()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0)
        val up = MotionEvent.obtain(downTime, downTime + 60L, MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0)
        try {
            assertTrue("Physical ACTION_DOWN must inject", instrumentation.uiAutomation.injectInputEvent(down, true))
            assertTrue("Physical ACTION_UP must inject", instrumentation.uiAutomation.injectInputEvent(up, true))
        } finally {
            down.recycle()
            up.recycle()
        }
        instrumentation.waitForIdleSync()
        compose.waitForIdle()
    }

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
        // Separate semantics reachability from true Android input dispatch: inject physical
        // down/up events at the exact semantic bounds after navigating through all four worlds.
        physicalTap("open-capabilities")
        compose.onNodeWithTag("capability-list").assertIsDisplayed()
        compose.onNodeWithTag("capability-list").performScrollToNode(hasTestTag("capability-CHAT"))
        physicalTap("capability-CHAT")
        compose.onNodeWithTag("active-route").assertIsDisplayed()
    }
}
