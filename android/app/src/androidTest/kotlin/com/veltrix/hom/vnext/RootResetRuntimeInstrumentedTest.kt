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
        val apiSession = VeltrixApiClient().register("root-runtime-${System.currentTimeMillis()}", "Veltrix!Runtime2026", "Root Runtime")
        runBlocking { SessionStore(targetContext).save(LocalSession(apiSession.accountId, apiSession.token)) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitTag("primary-worlds", 30_000L)
            listOf("HOME", "PERSONAL", "STORE", "PROJECTS").forEach { compose.onNodeWithTag("world-$it").assertIsDisplayed() }
            compose.onNodeWithTag("world-HOME").assertIsSelected()
            compose.onNodeWithTag("world-PERSONAL").performClick(); compose.waitForIdle(); compose.onNodeWithTag("world-PERSONAL").assertIsSelected()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle(); compose.onNodeWithTag("world-HOME").assertIsSelected()
            compose.onNodeWithTag("home-menu").performClick(); compose.mainClock.advanceTimeBy(1_000L); compose.waitForIdle(); awaitTag("root-sidebar")
            compose.onNodeWithTag("root-sidebar-list").performScrollToIndex(18); compose.waitForIdle(); awaitTag("drawer-secondary-SETTINGS")
            compose.onNodeWithTag("drawer-secondary-SETTINGS").performClick(); compose.mainClock.advanceTimeBy(1_000L); compose.waitForIdle(); awaitTag("settings-stage70")
            awaitTag("settings-sign-out"); compose.onNodeWithTag("settings-sign-out").performClick(); awaitTag("continue-google")
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
            val profile = Part2FeatureRepository(targetContext).gameProfile(apiSession, true).value ?: error("Fresh game profile required")
            SessionStore(targetContext).save(LocalSession(apiSession.accountId, apiSession.token))
            profile.avatarId
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitTag("home-stage40", 30_000L); awaitTag("veltrix-character-$expectedAvatar", 30_000L)
            compose.onNodeWithTag("world-PERSONAL").performClick(); compose.waitForIdle()
            awaitTag("personal-stage50", 30_000L); awaitTag("personal-character", 30_000L); awaitTag("personal-map-world", 30_000L)
            compose.onNodeWithTag("personal-character").assertIsDisplayed(); awaitTag("veltrix-character-$expectedAvatar", 30_000L)
            compose.waitUntil(10_000L) { compose.onAllNodesWithTag("map-locked-gate").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("map-active-world").fetchSemanticsNodes().isNotEmpty() }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle(); awaitTag("home-stage40")
            compose.onNodeWithTag("world-HOME").assertIsSelected(); awaitTag("veltrix-character-$expectedAvatar")
        }
    }

    @Test
    fun projectsStage60OpensFreshWorkspaceAndBackPreservesProjectHierarchy() {
        val stamp = System.currentTimeMillis()
        val apiSession = VeltrixApiClient().register("projects60-runtime-$stamp", "Veltrix!Runtime2026", "Projects Runtime")
        val title = "Operating World $stamp"
        val created = runBlocking {
            val project = Part2FeatureRepository(targetContext).createProject(apiSession, title, "Keep one verified goal context isolated.")
            SessionStore(targetContext).save(LocalSession(apiSession.accountId, apiSession.token))
            project
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitTag("home-stage40", 30_000L)
            compose.onNodeWithTag("world-PROJECTS").performClick(); compose.waitForIdle()
            awaitTag("projects-stage60", 30_000L); awaitTag("project-card-${created.id}", 30_000L)
            compose.onNodeWithTag("project-card-${created.id}").assertIsDisplayed().performClick()
            awaitTag("project-workspace", 30_000L); awaitText(title, 30_000L, true); awaitTag("project-workspace-stats", 30_000L); awaitTag("project-brain", 30_000L)
            compose.onNodeWithTag("project-workspace").assertIsDisplayed(); compose.onNodeWithTag("project-workspace-title").assertIsDisplayed()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle(); awaitTag("projects-stage60", 10_000L); compose.onNodeWithTag("world-PROJECTS").assertIsSelected(); compose.onAllNodesWithTag("project-workspace").assertCountEquals(0)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle(); awaitTag("home-stage40", 10_000L); compose.onNodeWithTag("world-HOME").assertIsSelected()
        }
    }

    @Test
    fun storeStage70RendersFreshServerBalanceCatalogAndEquippedCharacterTruth() {
        val stamp = System.currentTimeMillis()
        val apiSession = VeltrixApiClient().register("store70-runtime-$stamp", "Veltrix!Runtime2026", "Store Runtime")
        val snapshot = runBlocking {
            val repo = Part2FeatureRepository(targetContext)
            val store = repo.store(apiSession, true).value ?: error("Fresh store required")
            val avatars = repo.avatars(apiSession, true).value ?: error("Fresh avatars required")
            val game = repo.gameProfile(apiSession, true).value ?: error("Fresh game profile required")
            SessionStore(targetContext).save(LocalSession(apiSession.accountId, apiSession.token))
            Triple(store, avatars, game)
        }
        val store = snapshot.first
        val avatars = snapshot.second
        val game = snapshot.third
        val equipped = avatars.firstOrNull { it.equipped }?.avatarId ?: game.avatarId

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitTag("home-stage40", 30_000L)
            compose.onNodeWithTag("world-STORE").performClick(); compose.waitForIdle()
            awaitTag("store-stage70", 30_000L); awaitTag("store-balance", 30_000L); awaitTag("store-preview", 30_000L); awaitTag("store-avatar-equipped", 30_000L)
            compose.onNodeWithTag("store-stage70").assertIsDisplayed(); compose.onNodeWithTag("store-balance").assertIsDisplayed(); compose.onNodeWithTag("store-preview").assertIsDisplayed(); compose.onNodeWithTag("store-avatar-equipped").assertIsDisplayed()
            awaitTag("store-avatar-$equipped", 30_000L)
            store.items.firstOrNull()?.let { awaitTag("store-item-${it.itemId}", 30_000L) }
            awaitTag("store-inventory-count", 30_000L)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle(); awaitTag("home-stage40", 10_000L); compose.onNodeWithTag("world-HOME").assertIsSelected()
        }
    }

    @Test
    fun stage70GlobalSecondaryRoutesAreRealCapabilitiesWithNoPlaceholderBridge() {
        val apiSession = VeltrixApiClient().register("global70-runtime-${System.currentTimeMillis()}", "Veltrix!Runtime2026", "Global Runtime")
        runBlocking { SessionStore(targetContext).save(LocalSession(apiSession.accountId, apiSession.token)) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitTag("home-stage40", 30_000L)
            val routes = listOf(
                Triple("CHAT", 8, "chat-screen"),
                Triple("LIBRARY", 9, null),
                Triple("TESTING", 10, "testing-screen"),
                Triple("PRACTICE", 11, "practice-screen"),
                Triple("QUIZZES", 12, "quiz-screen"),
                Triple("FLASHCARDS", 13, null),
                Triple("MISTAKES", 14, null),
                Triple("CALCULATOR", 15, "calculator-screen"),
                Triple("TRANSLATE", 16, "translate-screen"),
                Triple("NOTIFICATIONS", 17, "notifications-screen"),
                Triple("SETTINGS", 18, "settings-stage70"),
            )
            routes.forEach { (name, index, innerTag) ->
                openSecondary(name, index)
                awaitTag("root-capability-$name", 20_000L)
                innerTag?.let { awaitTag(it, 20_000L) }
            }
            awaitTag("settings-sign-out", 20_000L)
            compose.onAllNodesWithText("is retained and is being moved into the new Veltrix shell.", substring = true).assertCountEquals(0)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle(); awaitTag("home-stage40", 10_000L); compose.onNodeWithTag("world-HOME").assertIsSelected()
        }
    }

    private fun openSecondary(name: String, drawerIndex: Int) {
        val rootMenuVisible = compose.onAllNodesWithTag("root-menu").fetchSemanticsNodes().isNotEmpty()
        if (rootMenuVisible) compose.onNodeWithTag("root-menu").performClick()
        else compose.onNodeWithTag("home-menu").performClick()
        compose.mainClock.advanceTimeBy(1_000L)
        compose.waitForIdle()
        awaitTag("root-sidebar", 10_000L)
        compose.onNodeWithTag("root-sidebar-list").performScrollToIndex(drawerIndex)
        compose.waitForIdle()
        awaitTag("drawer-secondary-$name", 10_000L)
        compose.onNodeWithTag("drawer-secondary-$name").performClick()
        compose.mainClock.advanceTimeBy(1_000L)
        compose.waitForIdle()
    }

    private fun awaitTag(tag:String, timeoutMillis:Long=15_000L) { compose.waitUntil(timeoutMillis) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun awaitText(text:String, timeoutMillis:Long=15_000L, substring:Boolean=false) { compose.waitUntil(timeoutMillis) { compose.onAllNodesWithText(text, substring=substring).fetchSemanticsNodes().isNotEmpty() } }
}
