package com.veltrix.hom.vnext

import android.util.Log
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        assertEquals(
            "Shell must validate the session seeded before Activity launch",
            seededSession.accountId,
            persistedSession?.accountId,
        )

        // RootResetApp intentionally fail-closes behind asynchronous server-session validation.
        // Wait for the current Stage40 product shell, not retired pre-reset shell selectors.
        awaitTag("world-HOME", 20_000)
        awaitTag("home-stage40", 30_000)
        compose.onNodeWithTag("world-HOME").assertIsDisplayed().assertHasClickAction().assertIsSelected()
        compose.onNodeWithTag("home-stage40").assertIsDisplayed()
        compose.onNodeWithTag("home-next-move").assertIsDisplayed().assertHasClickAction()

        compose.onNodeWithTag("world-PERSONAL").assertIsDisplayed().assertHasClickAction().performClick()
        awaitTag("personal-stage50", 20_000)
        compose.onNodeWithTag("world-PERSONAL").assertIsSelected()
        compose.onNodeWithTag("personal-stage50").assertIsDisplayed()

        compose.onNodeWithTag("world-STORE").assertIsDisplayed().assertHasClickAction().performClick()
        awaitTag("store-stage70", 20_000)
        compose.onNodeWithTag("world-STORE").assertIsSelected()
        compose.onNodeWithTag("store-stage70").assertIsDisplayed()

        compose.onNodeWithTag("world-PROJECTS").assertIsDisplayed().assertHasClickAction().performClick()
        awaitTag("projects-stage60", 20_000)
        compose.onNodeWithTag("world-PROJECTS").assertIsSelected()
        compose.onNodeWithTag("projects-stage60").assertIsDisplayed()

        compose.onNodeWithTag("world-HOME").assertHasClickAction().performClick()
        awaitTag("home-stage40", 20_000)
        compose.onNodeWithTag("world-HOME").assertIsSelected()

        val globalCapabilities = listOf(
            "CHAT",
            "LIBRARY",
            "TESTING",
            "PRACTICE",
            "QUIZZES",
            "FLASHCARDS",
            "MISTAKES",
            "CALCULATOR",
            "TRANSLATE",
            "NOTIFICATIONS",
            "SETTINGS",
        )
        assertEquals("Current root drawer must expose all 11 accepted global capabilities", 11, globalCapabilities.size)
        assertEquals("Frontend capability contract must remain 11 routes", 11, CapabilityRoute.entries.size)

        compose.onNodeWithTag("home-menu").assertIsDisplayed().assertHasClickAction().performClick()
        awaitTag("root-sidebar-list", 10_000)
        val drawerList = compose.onNodeWithTag("root-sidebar-list").assertIsDisplayed()
        globalCapabilities.forEach { capability ->
            val tag = "drawer-secondary-$capability"
            drawerList.performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).assertIsDisplayed().assertHasClickAction()
        }

        drawerList.performScrollToNode(hasTestTag("drawer-secondary-CHAT"))
        compose.onNodeWithTag("drawer-secondary-CHAT").assertIsDisplayed().assertHasClickAction().performClick()
        awaitTag("root-capability-CHAT", 20_000)
        compose.onNodeWithTag("root-capability-CHAT").assertIsDisplayed()
    }

    private fun awaitTag(tag: String, timeoutMillis: Long) {
        compose.waitUntil(timeoutMillis) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
