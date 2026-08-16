package com.veltrix.hom.vnext

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
            compose.onNodeWithText("Sign in").assertIsDisplayed()
            compose.onNodeWithText("Create account").assertIsDisplayed()
            compose.onNodeWithTag("primary-worlds").assertDoesNotExist()
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
            awaitTag("root-sidebar")
            compose.onNodeWithText("Settings / Account").performClick()
            awaitTag("sign-out")
            compose.onNodeWithTag("sign-out").performClick()

            awaitTag("continue-google")
            compose.onNodeWithTag("primary-worlds").assertDoesNotExist()
            compose.onNodeWithTag("continue-google").assertIsDisplayed()
        }
    }

    private fun awaitTag(tag: String, timeoutMillis: Long = 15_000L) {
        compose.waitUntil(timeoutMillis) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
