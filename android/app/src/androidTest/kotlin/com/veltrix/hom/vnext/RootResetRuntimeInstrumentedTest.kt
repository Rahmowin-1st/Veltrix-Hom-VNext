package com.veltrix.hom.vnext

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Runtime acceptance for the account-first root shell.
 *
 * This intentionally uses the real MainActivity, SessionStore and VeltrixApiClient. The host CI
 * supplies the accepted backend over adb reverse. A stored session only enters PRODUCT after the
 * server validates it; signed-out state never exposes the four account worlds.
 */
class RootResetRuntimeInstrumentedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearBefore() = runBlocking {
        SessionStore(targetContext).clear(explicitSignOut = true)
    }

    @After
    fun clearAfter() = runBlocking {
        SessionStore(targetContext).clear(explicitSignOut = true)
    }

    @Test
    fun signedOutColdLaunchRequiresAccountAndNeverShowsGuestWorlds() {
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitTag("continue-google")
            compose.onNodeWithTag("continue-google").assertIsDisplayed()
            compose.onAllNodesWithText("Sign in").assertCountEquals(2)
            compose.onAllNodesWithText("Create account").assertCountEquals(1)
            compose.onAllNodesWithTag("primary-worlds").assertCountEquals(0)
        }
    }

    @Test
    fun serverValidatedSessionEntersFourWorldsBackReturnsHomeAndSignOutClosesWorld() {
        val login = "root-runtime-${System.currentTimeMillis()}"
        val apiSession = VeltrixApiClient().register(
            login = login,
            password = "Veltrix!Runtime2026",
            displayName = "Root Runtime",
        )
        runBlocking {
            SessionStore(targetContext).save(LocalSession(apiSession.accountId, apiSession.token))
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitTag("primary-worlds", 30_000L)
            compose.onNodeWithTag("primary-worlds").assertIsDisplayed()
            listOf("HOME", "PERSONAL", "STORE", "PROJECTS").forEach { world ->
                compose.onNodeWithTag("world-$world").assertIsDisplayed()
            }
            compose.onNodeWithTag("world-HOME").assertIsSelected()

            compose.onNodeWithTag("world-PERSONAL").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("world-PERSONAL").assertIsSelected()

            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
            compose.waitForIdle()
            compose.onNodeWithTag("world-HOME").assertIsSelected()

            compose.onNodeWithText("≡").performClick()
            compose.mainClock.advanceTimeBy(1_000L)
            compose.waitForIdle()
            awaitTag("root-sidebar")

            compose.onNodeWithTag("drawer-secondary-SETTINGS")
                .performScrollTo()
                .performClick()
            compose.mainClock.advanceTimeBy(1_000L)
            compose.waitForIdle()
            awaitTag("root-account-surface")
            compose.onNodeWithTag("root-account-surface").assertIsDisplayed()
            awaitTag("sign-out")
            compose.onNodeWithTag("sign-out").performClick()

            awaitTag("continue-google")
            compose.onAllNodesWithTag("primary-worlds").assertCountEquals(0)
            compose.onNodeWithTag("continue-google").assertIsDisplayed()
        }
    }

    private fun awaitTag(tag: String, timeoutMillis: Long = 15_000L) {
        compose.waitUntil(timeoutMillis) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
