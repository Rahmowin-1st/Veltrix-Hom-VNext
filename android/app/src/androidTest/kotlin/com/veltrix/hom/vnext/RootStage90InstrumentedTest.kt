package com.veltrix.hom.vnext

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.metrics.performance.FrameDataApi31
import androidx.metrics.performance.JankStats
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
import java.util.Collections
import kotlin.math.ceil

/**
 * Current-root Stage 90 proof. This intentionally launches MainActivity, never the legacy evidence
 * activity, so screenshots, semantics and frame diagnostics describe the actual root-reset UI.
 * JankStats numbers are emulator sanity evidence only; they are not physical-device PF claims.
 */
class RootStage90InstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val targetContext get() = instrumentation.targetContext
    private val stage90Dir: File
        get() = File(requireNotNull(targetContext.getExternalFilesDir(null)), "stage90").apply { mkdirs() }

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
            awaitTag("personal-stage50", 20_000L); assertMinTouchTarget("personal-menu"); capture("personal")

            compose.onNodeWithTag("world-STORE").performClick(); compose.waitForIdle()
            awaitTag("store-stage70", 20_000L); awaitTag("store-preview", 20_000L); assertMinTouchTarget("store-menu"); capture("store")

            compose.onNodeWithTag("world-PROJECTS").performClick(); compose.waitForIdle()
            awaitTag("projects-stage60", 20_000L); awaitTag("project-card-${project.id}", 20_000L)
            assertMinTouchTarget("projects-menu")
            capture("projects")

            compose.onNodeWithTag("project-card-${project.id}").performClick(); compose.waitForIdle()
            awaitTag("project-workspace", 20_000L); capture("project-workspace")

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle(); awaitTag("projects-stage60", 10_000L)
        }

        val pngs = listOf("home", "personal", "store", "projects", "project-workspace").map { File(stage90Dir, "$it.png") }
        assertTrue("All five current-root proof screenshots must exist", pngs.all { it.isFile && it.length() > 20_000L })
        File(stage90Dir, "visual-a11y-report.txt").writeText(
            buildString {
                appendLine("ROOT_STAGE90_VISUAL_MATRIX=PASS screens=${pngs.size}")
                appendLine("CRITICAL_TOUCH_TARGETS=PASS min_dp=48")
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
            capture("font200-home")
        }

        runBlocking { SessionStore(targetContext).clear(explicitSignOut = true) }
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitTag("continue-google", 20_000L)
            awaitTag("auth-login", 20_000L)
            compose.onNodeWithTag("auth-password").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("auth-submit").performScrollTo().assertIsDisplayed()
            capture("font200-auth")
        }
        File(stage90Dir, "font200-report.txt").writeText("FONT_SCALE_200=PASS\nHOME_CRITICAL_CONTROLS=PASS\nAUTH_CRITICAL_CONTROLS=PASS\n")
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

    @Test
    fun currentRootPrimaryNavigationHasNonPathologicalEmulatorFrameClassification() {
        val apiSession = VeltrixApiClient().register("stage90-pf-${System.currentTimeMillis()}", "Veltrix!Runtime2026", "Stage 90 PF")
        runBlocking { SessionStore(targetContext).save(LocalSession(apiSession.accountId, apiSession.token)) }
        val samples = Collections.synchronizedList(ArrayList<FrameSample>(256))
        var tracker: JankStats? = null

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitTag("home-stage40", 30_000L)
            scenario.onActivity { activity ->
                tracker = JankStats.createAndTrack(activity.window) { volatile ->
                    val frame = volatile as? FrameDataApi31 ?: return@createAndTrack
                    samples += FrameSample(
                        uiNanos = frame.frameDurationUiNanos,
                        cpuNanos = frame.frameDurationCpuNanos,
                        totalNanos = frame.frameDurationTotalNanos,
                        overrunNanos = frame.frameOverrunNanos,
                        jank = frame.isJank,
                    )
                }.also { it.isTrackingEnabled = false }
            }

            fun cycle(settleMs: Long) {
                for (world in listOf("PERSONAL", "STORE", "PROJECTS", "HOME")) {
                    compose.onNodeWithTag("world-$world").performClick()
                    compose.waitForIdle()
                    SystemClock.sleep(settleMs)
                }
            }

            cycle(300)
            synchronized(samples) { samples.clear() }
            scenario.onActivity { requireNotNull(tracker).isTrackingEnabled = true }
            repeat(3) { cycle(280) }
            SystemClock.sleep(350)
            scenario.onActivity { requireNotNull(tracker).isTrackingEnabled = false }
        }

        val snapshot = synchronized(samples) { samples.toList() }
        val jankCount = snapshot.count { it.jank }
        val jankPct = if (snapshot.isEmpty()) 100.0 else jankCount * 100.0 / snapshot.size
        val invalid = snapshot.count { it.uiNanos < 0L || it.cpuNanos < 0L || it.totalNanos < 0L || it.cpuNanos < it.uiNanos }
        val uiP95 = percentile(snapshot.map { it.uiNanos / 1_000_000.0 }, 95)
        val cpuP95 = percentile(snapshot.map { it.cpuNanos / 1_000_000.0 }, 95)
        val totalP95 = percentile(snapshot.map { it.totalNanos / 1_000_000.0 }, 95)
        val overrunP95 = percentile(snapshot.map { it.overrunNanos / 1_000_000.0 }, 95)
        val pass = snapshot.size >= 20 && invalid == 0 && jankPct <= 25.0
        val report = buildString {
            appendLine("ROOT_STAGE90_JANKSTATS=${if (pass) "PASS" else "FAIL"}")
            appendLine("samples=${snapshot.size} jank=$jankCount jank_pct=${"%.2f".format(jankPct)} invalid=$invalid")
            appendLine("ui_p95_ms=${"%.2f".format(uiP95)} cpu_p95_ms=${"%.2f".format(cpuP95)} total_p95_ms=${"%.2f".format(totalP95)} overrun_p95_ms=${"%.2f".format(overrunP95)}")
            appendLine("classification=DEBUG_API36_EMULATOR_SANITY_ONLY")
            appendLine("RELEASE_PROFILEABLE_PF=NOT_VERIFIED")
            appendLine("PHYSICAL_DEVICE_PF=NOT_VERIFIED")
        }
        File(stage90Dir, "jankstats-root.txt").writeText(report)
        assertTrue(report, pass)
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
        SystemClock.sleep(180)
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

    private fun percentile(values: List<Double>, percentile: Int): Double {
        if (values.isEmpty()) return Double.POSITIVE_INFINITY
        val sorted = values.sorted()
        val index = (ceil(percentile / 100.0 * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private data class FrameSample(
        val uiNanos: Long,
        val cpuNanos: Long,
        val totalNanos: Long,
        val overrunNanos: Long,
        val jank: Boolean,
    )
}
