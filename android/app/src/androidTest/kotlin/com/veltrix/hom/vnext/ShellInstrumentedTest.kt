package com.veltrix.hom.vnext

import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
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
import java.util.ArrayDeque

class ShellInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    /**
     * Compose semantics bounds are root-relative, while UiAutomation injects display-space input.
     * Resolve the actual accessibility node and use its boundsInScreen so this is a genuine
     * Android hit-target proof rather than a coordinate approximation.
     */
    private fun physicalTapByDescription(description: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val root = instrumentation.uiAutomation.rootInActiveWindow
            ?: error("No active accessibility window while locating '$description'")
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var hit: AccessibilityNodeInfo? = null
        while (queue.isNotEmpty() && hit == null) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser && node.contentDescription?.toString() == description) {
                hit = node
                break
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        val node = requireNotNull(hit) { "Accessibility node '$description' was not visible" }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        assertTrue("Physical target '$description' must have positive bounds: $bounds", bounds.width() > 0 && bounds.height() > 0)
        assertTrue("Physical target '$description' must expose clickability", node.isClickable)

        val x = bounds.centerX().toFloat()
        val y = bounds.centerY().toFloat()
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 70L, MotionEvent.ACTION_UP, x, y, 0)
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

        // Real display-space Android touch on the persistent shell menu after traversing all
        // primary worlds. This catches hit-target, z-order and inset regressions that semantics-
        // only performClick cannot detect.
        physicalTapByDescription("Open Veltrix capabilities")
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
