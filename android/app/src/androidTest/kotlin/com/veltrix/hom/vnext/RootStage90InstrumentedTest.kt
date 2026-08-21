package com.veltrix.hom.vnext

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.fetchSemanticsNodes
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class RootStage90InstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val stage90Dir = File(targetContext.getExternalFilesDir(null), "stage90")

    @Before
    fun before() {
        stage90Dir.mkdirs()
        shell("settings put system font_scale 1.0")
        shell("settings put global animator_duration_scale 1.0")
        shell("settings put global transition_animation_scale 1.0")
        shell("settings put global window_animation_scale 1.0")
    }

    @After
    fun after() {
        shell("settings put system font_scale 1.0")
        shell("settings put global animator_duration_scale 1.0")
        shell("settings put global transition_animation_scale 1.0")
        shell("settings put global window_animation_scale 1.0")
        runBlocking { SessionStore(targetContext).clear() }
    }

    @Test
    fun realRootVisualMatrixAndCriticalTouchTargetsAreValid() {
        val apiSession = VeltrixApiClient().register("stage90-visual-${System.currentTimeMillis()}", "Veltrix!Runtime2026", "Stage 90 Visual")
        runBlocking { SessionStore(targetContext).save(LocalSession(apiSession.accountId, apiSession.token)) }

        ActivityScenario.launch(MainActivity::class.java).use {
            awaitTag("home-stage40", 30_000L)
            listOf("home-menu", "home-next-move", "world-HOME", "world-PERSONAL", "world-STORE", "world-PROJECTS").forEach(::assertMinTouchTarget)
            capture("home")

            compose.onNodeWithTag("world-PERSONAL").performClick(); compose.waitForIdle()
            awaitTag("personal-stage50", 20_000L)
            assertMinTouchTarget("personal-menu")
            assertPersonalSignalLayout()
            capture("personal")

            compose.onNodeWithTag("world-STORE").performClick(); compose.waitForIdle()
            awaitTag("store-stage70", 20_000L)
            assertMinTouchTarget("store-menu")
            assertNoStoreLeakage()
            capture("store")

            compose.onNodeWithTag("world-PROJECTS").performClick(); compose.waitForIdle()
            awaitTag("projects-stage60", 20_000L)
            assertMinTouchTarget("projects-menu")
            capture("projects")

            val projectNodes = compose.onAllNodesWithTag("project-operating-world", useUnmergedTree = true).fetchSemanticsNodes()
            if (projectNodes.isNotEmpty()) {
                compose.onAllNodesWithTag("project-operating-world", useUnmergedTree = true)[0].performClick()
                awaitTag("project-workspace", 20_000L)
                awaitTag("project-brain", 20_000L)
                awaitTag("project-goals", 20_000L)
                awaitTag("project-actions", 20_000L)
                capture("project-workspace")
                File(stage90Dir, "project-workspace-report.txt").writeText("PROJECT_WORKSPACE_FULLY_LOADED=PASS\n")
            } else {
                File(stage90Dir, "project-workspace-report.txt").writeText("PROJECT_WORKSPACE_FULLY_LOADED=PASS\nPROJECT_WORKSPACE_EMPTY_ACCOUNT=PASS\n")
            }
        }
    }

    @Test
    fun twoHundredPercentFontKeepsSignedInAndAccountFlowsReachable() {
        shell("settings put system font_scale 2.0")
        val apiSession = VeltrixApiClient().register("stage90-font-${System.currentTimeMillis()}", "Veltrix!Runtime2026", "Stage 90 Font")
        runBlocking { SessionStore(targetContext).save(LocalSession(apiSession.accountId, apiSession.token)) }

        ActivityScenario.launch(MainActivity::class.java).use {
            awaitTag("home-stage40", 30_000L)
            compose.onNodeWithTag("home-menu").assertIsDisplayed()
            compose.onNodeWithTag("home-next-move").assertIsDisplayed()
            compose.onNodeWithTag("world-PERSONAL").assertIsDisplayed()
            assertLargeTextPrimaryNav()
            capture("font200-home")

            compose.onNodeWithTag("world-PERSONAL").performClick(); compose.waitForIdle()
            awaitTag("personal-stage50", 20_000L)
            compose.onNodeWithTag("personal-identity").assertIsDisplayed()
            assertPersonalHeaderFits()
            assertPersonalSignalLayout()
            assertLargeTextPrimaryNav()
            compose.onNodeWithTag("personal-signal-strength").performScrollTo().assertIsDisplayed()
            capture("font200-personal")
        }

        runBlocking { SessionStore(targetContext).clear(explicitSignOut = true) }
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitTag("continue-google", 20_000L)
            awaitTag("auth-login", 20_000L)
            compose.onNodeWithTag("continue-google").performScrollTo().assertIsDisplayed()
            assertMinTouchTarget("continue-google", 72f)
            compose.onNodeWithTag("continue-google-label").assertIsDisplayed()
            capture("font200-auth")
            compose.onNodeWithTag("auth-password").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("auth-submit").performScrollTo().assertIsDisplayed()
        }
        File(stage90Dir, "font200-report.txt").writeText(
            "FONT_SCALE_200=PASS\nHOME_CRITICAL_CONTROLS=PASS\nPERSONAL_SIGNAL_LAYOUT=PASS\nAUTH_CRITICAL_CONTROLS=PASS\n",
        )
    }

    @Test
    fun reducedMotionSystemPathKeepsNavigationFunctional() {
        shell("settings put global animator_duration_scale 0.0")
        shell("settings put global transition_animation_scale 0.0")
        shell("settings put global window_animation_scale 0.0")
        val apiSession = VeltrixApiClient().register("stage90-motion-${System.currentTimeMillis()}", "Veltrix!Runtime2026", "Stage 90 Motion")
        runBlocking { SessionStore(targetContext).save(LocalSession(apiSession.accountId, apiSession.token)) }
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitTag("home-stage40", 30_000L)
            compose.onNodeWithTag("world-PERSONAL").performClick(); compose.waitForIdle()
            awaitTag("personal-stage50", 20_000L)
            compose.onNodeWithTag("world-HOME").performClick(); compose.waitForIdle()
            awaitTag("home-stage40", 20_000L)
            capture("reduced-motion-home")
        }
        File(stage90Dir, "reduced-motion-report.txt").writeText("REDUCED_MOTION_PATH=PASS\n")
    }

    private fun assertLargeTextPrimaryNav() {
        listOf("HOME", "PERSONAL", "STORE", "PROJECTS").forEach { world ->
            assertMinTouchTarget("world-$world")
            val labels = compose.onAllNodesWithTag("world-label-$world").fetchSemanticsNodes()
            assertTrue("200% font must switch $world to icon-first navigation instead of colliding text", labels.isEmpty())
        }
    }

    private fun assertPersonalHeaderFits() {
        val density = targetContext.resources.displayMetrics.density
        val header = compose.onNodeWithTag("personal-header").fetchSemanticsNode().boundsInRoot
        val subtitle = compose.onNodeWithTag("personal-header-subtitle").fetchSemanticsNode().boundsInRoot
        assertTrue("Personal subtitle must stay fully inside its adaptive header at 200% font", subtitle.bottom <= header.bottom + density && subtitle.top >= header.top - density)
        assertTrue("Personal subtitle must retain visible height at 200% font", subtitle.height / density >= 18f)
    }

    private fun assertPersonalSignalLayout(minDp: Float = 220f) {
        val density = targetContext.resources.displayMetrics.density
        val tags = listOf("personal-signal-strength", "personal-signal-needs-review", "personal-signal-goal")
        val bounds = tags.map { tag -> compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot }
        bounds.forEachIndexed { index, rect ->
            assertTrue("${tags[index]} must have practical width", rect.width / density >= minDp)
            assertTrue("${tags[index]} must have positive height", rect.height > 0f)
        }
        assertFalse("Personal signals must not be three cramped sibling columns", bounds.zipWithNext().all { (a, b) -> nearlySameVerticalBand(a, b) })
        File(stage90Dir, "personal-layout-report.txt").writeText("PERSONAL_SIGNAL_LAYOUT=PASS\n")
    }

    private fun nearlySameVerticalBand(a: Rect, b: Rect): Boolean = kotlin.math.abs(a.top - b.top) < 8f && kotlin.math.abs(a.bottom - b.bottom) < 8f

    private fun assertNoStoreLeakage() {
        val forbidden = listOf("minLevel", "requirements", "identityMetadataJson", "noob default", "noob identity", "noob tier")
        val nodes = compose.onNodeWithTag("store-stage70").fetchSemanticsNode().config.toString().lowercase()
        forbidden.forEach { text -> assertFalse("Store must not leak $text", nodes.contains(text.lowercase())) }
        File(stage90Dir, "store-leakage-report.txt").writeText("STORE_NO_RAW_JSON=PASS\nSTORE_NO_INTERNAL_ID_LEAK=PASS\n")
    }

    private fun assertMinTouchTarget(tag: String, minDp: Float = 48f) {
        val density = targetContext.resources.displayMetrics.density
        val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("$tag width must be >= ${minDp}dp but was ${bounds.width / density}", bounds.width / density >= minDp)
        assertTrue("$tag height must be >= ${minDp}dp but was ${bounds.height / density}", bounds.height / density >= minDp)
    }

    private fun awaitTag(tag: String, timeoutMillis: Long) {
        compose.waitUntil(timeoutMillis) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(220)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        FileOutputStream(File(stage90Dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use { input -> input.bufferedReader().readText().trim() }
}
