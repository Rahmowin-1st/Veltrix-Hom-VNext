package com.veltrix.hom.vnext

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Deterministic visual-motion evidence for the real MainActivity primary navigation lens.
 *
 * This test intentionally does not claim physical/runtime FPS. It freezes Compose's test clock,
 * drives the production spring one logical frame at a time, and captures a real rendered image
 * after every frame advance. The resulting image sequence proves that the production animation
 * contains meaningful intermediate visual states instead of a 1-2fps state slideshow.
 * Runtime pacing remains a separate JankStats sanity signal; physical touch feel remains external.
 */
class MotionFidelityInstrumentedTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    private val seedSessionRule = TestRule { base: Statement, _: Description ->
        object : Statement() {
            override fun evaluate() {
                val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
                val apiSession = VeltrixApiClient().register(
                    "stage100-motion-${System.currentTimeMillis()}@example.test",
                    "Veltrix!Runtime2026",
                    "Stage100 Motion",
                )
                runBlocking {
                    SessionStore(targetContext).save(LocalSession(apiSession.accountId, apiSession.token))
                }
                base.evaluate()
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(seedSessionRule).around(compose)

    @Test
    fun primaryNavigationLensProducesFrameByFrameVisualSequence() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val outputDir = File(target.filesDir, "motion-fidelity").apply {
            deleteRecursively()
            check(mkdirs()) { "Unable to create motion evidence directory: $absolutePath" }
        }

        awaitTag("world-HOME", 20_000)
        awaitTag("home-stage40", 30_000)
        compose.onNodeWithTag("world-HOME").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("home-stage40").assertIsDisplayed()

        val signatures = linkedSetOf<Long>()
        val lensPositions = linkedSetOf<Int>()
        var outputWidth = 0
        var outputHeight = 0

        fun captureFrame(index: Int) {
            compose.waitForIdle()
            val screenshot = checkNotNull(
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot(),
            ) { "Unable to capture rendered display for motion frame $index" }
            val source = screenshot.copy(Bitmap.Config.ARGB_8888, false)
            screenshot.recycle()
            val width = minOf(720, source.width).coerceAtLeast(2).let { if (it % 2 == 0) it else it - 1 }
            val rawHeight = (source.height * (width.toFloat() / source.width.toFloat())).roundToInt().coerceAtLeast(2)
            val height = if (rawHeight % 2 == 0) rawHeight else rawHeight - 1
            val scaled = if (source.width == width && source.height == height) {
                source.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                Bitmap.createScaledBitmap(source, width, height, true)
            }
            source.recycle()

            outputWidth = width
            outputHeight = height

            // A sampled visual signature proves that encoded playback is backed by distinct
            // rendered Compose states rather than duplicated frames.
            var signature = 1125899906842597L
            val xStep = (scaled.width / 48).coerceAtLeast(1)
            val yStep = (scaled.height / 80).coerceAtLeast(1)
            var y = 0
            while (y < scaled.height) {
                var x = 0
                while (x < scaled.width) {
                    signature = signature * 31L + scaled.getPixel(x, y).toLong()
                    x += xStep
                }
                y += yStep
            }
            signatures += signature
            val lensLeft = compose
                .onNodeWithTag("world-lens", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
                .left
                .roundToInt()
            lensPositions += lensLeft

            val frameFile = File(outputDir, "frame-%03d.jpg".format(index))
            FileOutputStream(frameFile).use { stream ->
                check(scaled.compress(Bitmap.CompressFormat.JPEG, 88, stream)) {
                    "Failed to encode ${frameFile.name}"
                }
            }
            scaled.recycle()
        }

        val frameCount = 49 // initial state + 48 production spring frame advances
        val previousAutoAdvance = compose.mainClock.autoAdvance
        try {
            compose.mainClock.autoAdvance = false
            captureFrame(0)
            compose.onNodeWithTag("world-PERSONAL").performClick()
            repeat(frameCount - 1) { offset ->
                compose.mainClock.advanceTimeByFrame()
                captureFrame(offset + 1)
            }
        } finally {
            compose.mainClock.autoAdvance = previousAutoAdvance
        }

        compose.waitForIdle()
        awaitTag("personal-stage50", 10_000)
        compose.onNodeWithTag("world-PERSONAL").assertIsSelected()
        compose.onNodeWithTag("personal-stage50").assertIsDisplayed()

        val minimumDistinctFrames = 12
        val minimumDistinctLensPositions = 12
        val pass = signatures.size >= minimumDistinctFrames &&
            lensPositions.size >= minimumDistinctLensPositions &&
            outputWidth >= 480 &&
            outputHeight >= 800 &&
            File(outputDir, "frame-000.jpg").length() > 0L &&
            File(outputDir, "frame-048.jpg").length() > 0L

        val report = buildString {
            appendLine(
                "MOTION_DETERMINISTIC_VISUAL_SEQUENCE=${if (pass) "PASS" else "FAIL"} " +
                    "frames=$frameCount distinct_frames=${signatures.size} " +
                    "distinct_lens_positions=${lensPositions.size} size=${outputWidth}x${outputHeight} " +
                    "transition=HOME_TO_PERSONAL source=compose_main_test_clock",
            )
            appendLine("FRAME_ADVANCE=advanceTimeByFrame playback_fps_claim=NONE runtime_fps_claim=NONE")
            appendLine("PRODUCTION_PRIMITIVE=RootKineticBottomBar world-lens spring")
            appendLine("PHYSICAL_TOUCH_FEEL=NOT_VERIFIED")
        }
        File(outputDir, "report.txt").writeText(report)
        assertTrue(report, pass)
    }

    private fun awaitTag(tag: String, timeoutMillis: Long) {
        compose.waitUntil(timeoutMillis) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
