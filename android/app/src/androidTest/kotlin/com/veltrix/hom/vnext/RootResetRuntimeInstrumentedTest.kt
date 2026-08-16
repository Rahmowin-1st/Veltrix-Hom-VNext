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
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RootResetRuntimeInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun clearBefore() = runBlocking { SessionStore(targetContext).clear(explicitSignOut = true) }
    @After fun clearAfter() = runBlocking { SessionStore(targetContext).clear(explicitSignOut = true) }

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
        val apiSession = VeltrixApiClient().register(login, "Veltrix!Runtime2026", "Root Runtime")
        runBlocking { SessionStore(targetContext).save(LocalSession(apiSession.accountId, apiSession.token)) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitTag("primary-worlds", 30_000L)
            listOf("HOME", "PERSONAL", "STORE", "PROJECTS").forEach { compose.onNodeWithTag("world-$it").assertIsDisplayed() }
            compose.onNodeWithTag("world-HOME").assertIsSelected()
            compose.onNodeWithTag("world-PERSONAL").performClick(); compose.waitForIdle(); compose.onNodeWithTag("world-PERSONAL").assertIsSelected()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle(); compose.onNodeWithTag("world-HOME").assertIsSelected()
            compose.onNodeWithText("≡").performClick(); compose.mainClock.advanceTimeBy(1_000L); compose.waitForIdle(); awaitTag("root-sidebar")
            compose.onNodeWithTag("root-sidebar-list").performScrollToIndex(18); compose.waitForIdle(); awaitTag("drawer-secondary-SETTINGS")
            compose.onNodeWithTag("drawer-secondary-SETTINGS").performClick(); compose.mainClock.advanceTimeBy(1_000L); compose.waitForIdle(); awaitTag("root-account-surface")
            compose.onNodeWithTag("sign-out").performClick(); awaitTag("continue-google")
            compose.onAllNodesWithTag("primary-worlds").assertCountEquals(0)
        }
    }

    @Test
    fun homeStage40ShowsFreshProjectBrainAndNextMovePerformsARealRoute() {
        val stamp = System.currentTimeMillis()
        val apiSession = VeltrixApiClient().register("home40-runtime-$stamp", "Veltrix!Runtime2026", "Home Runtime")
        val projectTitle = "Stage 40 Runtime $stamp"
        runBlocking {
            Part2FeatureRepository(targetContext).createProject(apiSession, projectTitle, "Prove the live Home command center route.")
            SessionStore(targetContext).save(LocalSession(apiSession.accountId, apiSession.token))
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitTag("home-stage40", 30_000L); awaitText(projectTitle, 30_000L, true); awaitTag("home-brain-pulse", 30_000L); awaitTag("home-next-move", 30_000L)
            compose.onNodeWithTag("home-brain-pulse").assertIsDisplayed(); compose.onNodeWithTag("home-next-move").assertIsDisplayed(); compose.onNodeWithTag("home-active-project").assertIsDisplayed(); compose.onNodeWithTag("home-progression").assertIsDisplayed()
            compose.onNodeWithTag("home-next-move").performClick(); compose.waitForIdle()
            compose.waitUntil(5_000L) { compose.onAllNodesWithTag("home-stage40").fetchSemanticsNodes().isEmpty() }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle(); awaitTag("home-stage40")
            compose.onNodeWithTag("world-HOME").assertIsSelected()
        }
    }

    @Test
    fun personalStage50UsesPersistentCharacterAndProgressionWorldWithoutInventedMapAccess() {
        val stamp = System.currentTimeMillis()
        val apiSession = VeltrixApiClient().register("personal50-runtime-$stamp", "Veltrix!Runtime2026", "Personal Runtime")
        val expectedAvatar = runBlocking {
            val profile = Part2FeatureRepository(targetContext).gameProfile(apiSession, true).value
                ?: error("Fresh game profile required")
            SessionStore(targetContext).save(LocalSession(apiSession.accountId, apiSession.token))
            profile.avatarId
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitTag("home-stage40", 30_000L)
            awaitTag("veltrix-character-$expectedAvatar", 30_000L)
            compose.onNodeWithTag("veltrix-character-$expectedAvatar").assertIsDisplayed()
            compose.onNodeWithTag("world-PERSONAL").performClick(); compose.waitForIdle()
            awaitTag("personal-stage50", 30_000L); awaitTag("personal-character", 30_000L); awaitTag("personal-map-world", 30_000L)
            compose.onNodeWithTag("personal-stage50").assertIsDisplayed(); compose.onNodeWithTag("personal-character").assertIsDisplayed(); compose.onNodeWithTag("personal-map-world").assertIsDisplayed()
            awaitTag("veltrix-character-$expectedAvatar", 30_000L); compose.onNodeWithTag("veltrix-character-$expectedAvatar").assertIsDisplayed()
            compose.waitUntil(10_000L) {
                compose.onAllNodesWithTag("map-locked-gate").fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithTag("map-active-world").fetchSemanticsNodes().isNotEmpty()
            }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle(); awaitTag("home-stage40")
            compose.onNodeWithTag("world-HOME").assertIsSelected(); awaitTag("veltrix-character-$expectedAvatar")
        }
    }

    private fun awaitTag(tag:String, timeoutMillis:Long=15_000L) { compose.waitUntil(timeoutMillis) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun awaitText(text:String, timeoutMillis:Long=15_000L, substring:Boolean=false) { compose.waitUntil(timeoutMillis) { compose.onAllNodesWithText(text, substring=substring).fetchSemanticsNodes().isNotEmpty() } }
}
