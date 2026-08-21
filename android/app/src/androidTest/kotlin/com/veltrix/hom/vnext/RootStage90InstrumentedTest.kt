package com.veltrix.hom.vnext

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * Current-root Stage 90 structural visual/A11Y proof. It deliberately launches MainActivity and
 * checks the defect classes that escaped the earlier screenshot-existence gate. Human/agent pixel
 * review remains a separate final acceptance gate; this class does not self-certify aesthetics.
 */
class RootStage90InstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val targetContext get() = instrumentation.targetContext
    private val stage90Dir: File
        get() = File(targetContext.filesDir, "stage90").apply { mkdirs() }

    @Before
    fun before() {
        resetSystemAdaptation()
        runBlocking { SessionStore(targetContext).clear(explicitSignOut = true) }
    }

    @After
    fun after() {
        resetSystemAdaptation()
        runBlocking { SessionStore(targetContext).clear(explicitSignOut = true) }
    }

    @Test
    fun realRootVisualMatrixAndCriticalTouchTargetsAreValid() {
        val stamp = System.currentTimeMillis()
        val apiSession = VeltrixApiClient().register("stage90-visual-$stamp", "Veltrix!Runtime2026", "Stage 90 Visual")
        val project = runBlocking {
            val created = Part2FeatureRepository(targetContext).createProject(apiSession, "Stage 90 World $stamp", "Visual and accessibility proof on the real product root.")
            SessionStore(targetContext).save(LocalSession(apiSession.accountId, apiSession.token))
            created
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitTag("home-stage40", 30_000L)
            awaitTag("home-next-move", 30_000L)
            assertMinTouchTarget("home-menu")
            assertMinTouchTarget("home-next-move")
            listOf("HOME", "PERSONAL", "STORE", "PROJECTS").forEach { assertMinTouchTarget("world-$it") }
            capture("home")

            compose.onNodeWithTag("world-PERSONAL").performClick(); compose.waitForIdle()
            awaitTag("personal-stage50", 20_000L)
            assertMinTouchTarget("personal-menu")
            assertPersonalSignalLayout()
            capture("personal")

            compose.onNodeWithTag("world-STORE").performClick(); compose.waitForIdle()
            awaitTag("store-stage70", 20_000L)
            awaitTag("store-preview", 20_000L)
            assertMinTouchTarget("store-menu")
            assertStoreHasNoImplementationLeakage()
            capture("store")

            compose.onNodeWithTag("world-PROJECTS").performClick(); compose.waitForIdle()
            awaitTag("projects-stage60", 20_000L)
            awaitTag("project-card-${project.id}", 20_000L)
            assertMinTouchTarget("projects-menu")
            capture("projects")

            compose.onNodeWithTag("project-card-${project.id}").performClick(); compose.waitForIdle()
            awaitTag("project-workspace", 30_000L)
            assertWorkspaceFullyLoaded()
            capture("project-workspace")

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle(); awaitTag("projects-stage60", 10_000L)
        }

        val pngs = listOf("home", "personal", "store", "projects", "project-workspace").map { File(stage90Dir, "$it.png") }
        assertTrue("All five current-root proof screenshots must exist", pngs.all { it.isFile && it.length() > 20_000L })
        File(stage90Dir, "visual-a11y-report.txt").writeText(
            buildString {
                appendLine("ROOT_STAGE90_VISUAL_MATRIX=PASS screens=${pngs.size}")
                appendLine("CRITICAL_TOUCH_TARGETS=PASS min_dp=48")
                appendLine("PERSONAL_SIGNAL_LAYOUT=PASS min_width_dp=220")
                appendLine("STORE_NO_RAW_JSON=PASS")
                appendLine("STORE_NO_INTERNAL_RULE_COPY=PASS")
                appendLine("PROJECT_WORKSPACE_FULLY_LOADED=PASS")
                pngs.forEach { appendLine("screen=${it.name} bytes=${it.length()}") }
            },
        )
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

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitTag("home-stage40", 30_000L)
            capture("reduced-motion-home")
            compose.onNodeWithTag("world-PERSONAL").performClick(); compose.waitForIdle()
            awaitTag("personal-stage50", 15_000L)
            compose.onNodeWithTag("world-PERSONAL").assertIsDisplayed()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle(); awaitTag("home-stage40", 15_000L)
        }
        File(stage90Dir, "reduced-motion-report.txt").writeText("REDUCED_MOTION_PATH=PASS\nDIRECT_NAVIGATION=PASS\nBACK_CONTINUITY=PASS\n")
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
        listOf("strength", "needs-review", "goal").forEach { suffix ->
            val node = compose.onNodeWithTag("personal-signal-$suffix").fetchSemanticsNode()
            val widthDp = node.boundsInRoot.width / density
            assertTrue("personal-signal-$suffix width ${widthDp}dp must stay readable", widthDp + .5f >= minDp)
        }
    }

    private fun assertStoreHasNoImplementationLeakage() {
        val forbidden = listOf("minLevel", "requirements", "identityMetadataJson", "noob default", "noob identity", "noob tier")
        forbidden.forEach { text ->
            val matches = compose.onAllNodesWithText(text, substring = true, ignoreCase = true, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("Store must not expose internal contract text: $text", matches.isEmpty())
        }
    }

    private fun assertWorkspaceFullyLoaded() {
        compose.onNodeWithTag("project-workspace").assertIsDisplayed()
        compose.onNodeWithTag("project-workspace-title").assertIsDisplayed()
        compose.onNodeWithTag("project-brain").performScrollTo().assertIsDisplayed()
        val loading = compose.onAllNodesWithTag("project-workspace-loading").fetchSemanticsNodes()
        val unavailable = compose.onAllNodesWithTag("project-workspace-unavailable").fetchSemanticsNodes()
        assertTrue("Success capture cannot still be loading", loading.isEmpty())
        assertTrue("Success capture cannot be unavailable/error state", unavailable.isEmpty())
    }

    private fun assertMinTouchTarget(tag: String, minDp: Float = 48f) {
        val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
        val density = targetContext.resources.displayMetrics.density
        val widthDp = node.boundsInRoot.width / density
        val heightDp = node.boundsInRoot.height / density
        assertTrue("$tag touch target ${widthDp}x${heightDp}dp must be at least ${minDp}dp", widthDp + .5f >= minDp && heightDp + .5f >= minDp)
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(220)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val file = File(stage90Dir, "$name.png")
        FileOutputStream(file).use { output ->
            assertTrue("PNG compression failed for $name", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        assertTrue("Screenshot $name must have real phone dimensions", bitmap.width >= 720 && bitmap.height >= 1280)
        bitmap.recycle()
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250)
    }

    private fun resetSystemAdaptation() {
        runCatching { shell("settings put system font_scale 1.0") }
        runCatching { shell("settings put global animator_duration_scale 1.0") }
        runCatching { shell("settings put global transition_animation_scale 1.0") }
        runCatching { shell("settings put global window_animation_scale 1.0") }
    }

    private fun awaitTag(tag: String, timeoutMillis: Long = 15_000L) {
        compose.waitUntil(timeoutMillis) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
}
