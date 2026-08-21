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
import androidx.test.platform.app.InstrumentationRegistry
import com.veltrix.hom.vnext.core.CapabilityRoute
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class ShellInstrumentedTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var seededSession: LocalSession

    private val seedSessionRule = TestRule { base: Statement, _: Description ->
        object : Statement() {
            override fun evaluate() {
                val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
                val apiSession = VeltrixApiClient().register(
                    "stage100-shell-${System.currentTimeMillis()}@example.test",
                    "Veltrix!Runtime2026",
                    "Stage100 Shell",
                )
                seededSession = LocalSession(apiSession.accountId, apiSession.token)
                runBlocking { SessionStore(targetContext).save(seededSession) }
                base.evaluate()
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(seedSessionRule).around(compose)

    @Test
    fun fourPrimaryWorldsAndGlobalCapabilitiesRemainReachable() {
        val persistedSession = runBlocking {
            SessionStore(compose.activity.applicationContext).read()
        }
        Log.i(
            "VELTRIX_HOME_DIAG",
            "sessionPresent=${persistedSession != null} accountId=${persistedSession?.accountId ?: "none"}",
        )
        assertNotNull("Fresh backend session must survive into the real Activity test process", persistedSession)
        assertEquals("Shell must validate the session seeded before Activity launch", seededSession.accountId, persistedSession?.accountId)

        // RootResetApp intentionally fail-closes behind an asynchronous server-session validation.
        // Do not treat the initial CHECKING gate as a missing shell; wait for the validated PRODUCT world.
        compose.waitUntil(15_000) {
            runCatching {
                compose.onNodeWithTag("world-HOME").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        compose.onNodeWithTag("world-HOME").assertIsDisplayed().assertHasClickAction()
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

        compose.onNodeWithTag("world-PERSONAL").assertIsDisplayed().assertHasClickAction().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("personal-screen").assertIsDisplayed()
        compose.onNodeWithTag("active-route").assertTextEquals("Personal")

        compose.onNodeWithTag("world-STORE").assertIsDisplayed().assertHasClickAction().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("active-route").assertTextEquals("Store")

        compose.onNodeWithTag("world-PROJECTS").assertIsDisplayed().assertHasClickAction().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("projects-screen").assertIsDisplayed()
        compose.onNodeWithTag("active-route").assertTextEquals("Projects")

        assertEquals(11, CapabilityRoute.entries.size)
        compose.onNodeWithTag("open-capabilities").assertIsDisplayed().assertHasClickAction().performClick()
        compose.waitForIdle()
        val capabilityList = compose.onNodeWithTag("capability-list").assertIsDisplayed()
        val listBounds = capabilityList.fetchSemanticsNode().boundsInRoot
        val minUsableHeightPx = 160f * compose.activity.resources.displayMetrics.density
        assertTrue(
            "Capability list must retain a usable viewport; bounds=$listBounds minHeightPx=$minUsableHeightPx",
            listBounds.height >= minUsableHeightPx,
        )
        capabilityList.performScrollToNode(hasTestTag("capability-CHAT"))
        compose.onNodeWithTag("capability-CHAT").assertIsDisplayed().assertHasClickAction().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("active-route").assertTextEquals("Chat")
    }
}
