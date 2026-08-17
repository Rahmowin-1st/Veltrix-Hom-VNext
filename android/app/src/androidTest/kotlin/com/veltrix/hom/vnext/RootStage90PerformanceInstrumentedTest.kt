package com.veltrix.hom.vnext

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.metrics.performance.FrameDataApi31
import androidx.metrics.performance.JankStats
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil

/**
 * Stage 90 performance sanity for the actual authenticated MainActivity root.
 *
 * Deliberately has no Compose test rule: Compose test clocks can virtualize animation time and
 * contaminate JankStats. Navigation is driven by real shell input against the accessibility bounds
 * of the production primary-worlds control, so Choreographer owns timing exactly as it does at runtime.
 *
 * Input is paced by rendered-frame quiescence, not a fixed tap spam cadence. On a software-rendered
 * emulator a world can legitimately take longer than a fixed 420 ms interval to finish its frame;
 * issuing another navigation before that frame completes measures an artificial render backlog rather
 * than one completed user interaction. The acceptance threshold and sample floor remain fail-closed.
 *
 * This remains debug/API36-emulator sanity only; it is not a release/profileable or physical-device claim.
 */
class RootStage90PerformanceInstrumentedTest {
    private data class FrameSample(
        val label: String,
        val uiNanos: Long,
        val cpuNanos: Long,
        val totalNanos: Long,
        val overrunNanos: Long,
        val jank: Boolean,
    )

    @Test
    fun warmedCurrentRootPrimaryNavigationHasNonPathologicalFrameClassification() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val out = File(target.filesDir, "stage90").apply { mkdirs() }
        val phaseFile = File(out, "performance-phases.txt").apply { writeText("") }
        fun phase(value: String) {
            val now = SystemClock.uptimeMillis()
            phaseFile.appendText("$now $value\n")
            instrumentation.sendStatus(
                2,
                Bundle().apply {
                    putString("stage90_pf_phase", value)
                    putLong("stage90_pf_uptime_ms", now)
                },
            )
        }

        phase("start")
        val apiSession = VeltrixApiClient().register(
            "stage90-realpf-${System.currentTimeMillis()}",
            "Veltrix!Runtime2026",
            "Stage 90 Real PF",
        )
        phase("account_registered")
        runBlocking { SessionStore(target).save(LocalSession(apiSession.accountId, apiSession.token)) }
        phase("session_saved")

        val samples = Collections.synchronizedList(ArrayList<FrameSample>(384))
        val currentLabel = AtomicReference("unclassified")
        val lastFrameUptime = AtomicLong(0L)
        var tracker: JankStats? = null

        phase("direct_activity_start")
        val activity = launchMainActivityWithoutIdleSync(
            instrumentation = instrumentation,
            timeoutMillis = 30_000L,
            onProgress = ::phase,
        )
        phase("activity_launched")
        phase("nav_lookup_start")
        val navBounds = waitForPrimaryWorldsBounds(
            instrumentation = instrumentation,
            timeoutMillis = 30_000L,
            onProgress = ::phase,
        )
        phase("primary_worlds_found_${navBounds.width()}x${navBounds.height()}")
        val navY = navBounds.centerY()
        val navX = IntArray(4) { index ->
            navBounds.left + ((index * 2 + 1) * navBounds.width()) / 8
        }

        instrumentation.runOnMainSync {
            tracker = JankStats.createAndTrack(activity.window) { volatile ->
                val frame = volatile as? FrameDataApi31 ?: return@createAndTrack
                samples += FrameSample(
                    label = currentLabel.get(),
                    uiNanos = frame.frameDurationUiNanos,
                    cpuNanos = frame.frameDurationCpuNanos,
                    totalNanos = frame.frameDurationTotalNanos,
                    overrunNanos = frame.frameOverrunNanos,
                    jank = frame.isJank,
                )
                lastFrameUptime.set(SystemClock.uptimeMillis())
            }.also { it.isTrackingEnabled = true }
        }
        phase("tracker_created_tracking_enabled")

        fun sampleCount(): Int = synchronized(samples) { samples.size }

        fun shellTap(x: Int, y: Int) {
            instrumentation.uiAutomation.executeShellCommand("input tap $x $y").close()
        }

        fun waitForRenderedFrameQuiescence(label: String, baselineCount: Int) {
            val deadline = SystemClock.uptimeMillis() + 8_000L
            var observedFrame = false
            while (SystemClock.uptimeMillis() < deadline) {
                val now = SystemClock.uptimeMillis()
                val count = sampleCount()
                if (count > baselineCount) observedFrame = true
                val last = lastFrameUptime.get()
                if (observedFrame && last > 0L && now - last >= 320L) {
                    phase("${label}_quiescent_frames_${count - baselineCount}")
                    return
                }
                SystemClock.sleep(40L)
            }
            phase("${label}_quiescence_timeout")
            error("$label did not reach rendered-frame quiescence within 8000ms")
        }

        val worldLabels = arrayOf("HOME", "PERSONAL", "STORE", "PROJECTS")
        fun navigationCycle(cycleLabel: String) {
            // Personal -> Store -> Projects -> Home on the persistent production shell.
            for (index in intArrayOf(1, 2, 3, 0)) {
                val label = "${cycleLabel}_${worldLabels[index]}"
                val before = sampleCount()
                currentLabel.set(label)
                phase("${label}_tap")
                shellTap(navX[index], navY)
                waitForRenderedFrameQuiescence(label, before)
            }
        }

        // Warm each destination once with the same real-render barrier used by measurement. The
        // warmup frames are then excluded from acceptance accounting.
        SystemClock.sleep(1_000L)
        phase("warmup_start")
        navigationCycle("warmup")
        synchronized(samples) { samples.clear() }
        currentLabel.set("measurement_ready")
        phase("warmup_complete")

        // Collect enough real frames to satisfy the unchanged floor. At least three full cycles are
        // exercised; additional cycles are allowed only to obtain a statistically usable >=24 sample
        // set on slow renderers, never to dilute or selectively discard jank.
        phase("measurement_start")
        var cycles = 0
        while ((cycles < 3 || sampleCount() < 24) && cycles < 6) {
            navigationCycle("measure_$cycles")
            cycles += 1
        }
        currentLabel.set("post_measure_settle")
        val finalBaseline = sampleCount()
        SystemClock.sleep(360L)
        phase("measurement_complete_cycles_${cycles}_samples_${sampleCount()}")
        instrumentation.runOnMainSync { requireNotNull(tracker).isTrackingEnabled = false }
        phase("tracking_disabled")

        phase("measurement_snapshot_started")
        val snapshot = synchronized(samples) { samples.toList() }
        val jankCount = snapshot.count { it.jank }
        val jankPct = if (snapshot.isEmpty()) 100.0 else jankCount * 100.0 / snapshot.size
        val invalid = snapshot.count {
            it.uiNanos < 0L || it.cpuNanos < 0L || it.totalNanos < 0L || it.cpuNanos < it.uiNanos
        }
        val uiP95 = percentile(snapshot.map { it.uiNanos / 1_000_000.0 }, 95)
        val cpuP95 = percentile(snapshot.map { it.cpuNanos / 1_000_000.0 }, 95)
        val totalP95 = percentile(snapshot.map { it.totalNanos / 1_000_000.0 }, 95)
        val overrunP95 = percentile(snapshot.map { it.overrunNanos / 1_000_000.0 }, 95)
        val minSamples = 24
        val maxJankPct = 25.0
        val pass = snapshot.size >= minSamples && invalid == 0 && jankPct <= maxJankPct

        val report = buildString {
            appendLine("ROOT_STAGE90_JANKSTATS=${if (pass) "PASS" else "FAIL"}")
            appendLine(
                "samples=${snapshot.size} jank=$jankCount jank_pct=${"%.2f".format(jankPct)} " +
                    "invalid=$invalid min_samples=$minSamples system_jank_pct_limit=${"%.1f".format(maxJankPct)}",
            )
            appendLine(
                "ui_p95_ms=${"%.2f".format(uiP95)} cpu_p95_ms=${"%.2f".format(cpuP95)} " +
                    "total_p95_ms=${"%.2f".format(totalP95)} overrun_p95_ms=${"%.2f".format(overrunP95)}",
            )
            appendLine("cycles=$cycles final_settle_new_frames=${sampleCount() - finalBaseline}")
            appendLine("WORLD_ATTRIBUTION_BEGIN")
            snapshot.groupBy { it.label }.toSortedMap().forEach { (label, frames) ->
                val labelJank = frames.count { it.jank }
                val labelPct = if (frames.isEmpty()) 0.0 else labelJank * 100.0 / frames.size
                appendLine(
                    "label=$label samples=${frames.size} jank=$labelJank jank_pct=${"%.2f".format(labelPct)} " +
                        "cpu_p95_ms=${"%.2f".format(percentile(frames.map { it.cpuNanos / 1_000_000.0 }, 95))} " +
                        "total_p95_ms=${"%.2f".format(percentile(frames.map { it.totalNanos / 1_000_000.0 }, 95))}",
                )
            }
            appendLine("WORLD_ATTRIBUTION_END")
            appendLine("FRAME_SAMPLES_BEGIN")
            snapshot.forEachIndexed { index, frame ->
                appendLine(
                    "frame=$index label=${frame.label} jank=${frame.jank} " +
                        "ui_ms=${"%.2f".format(frame.uiNanos / 1_000_000.0)} " +
                        "cpu_ms=${"%.2f".format(frame.cpuNanos / 1_000_000.0)} " +
                        "total_ms=${"%.2f".format(frame.totalNanos / 1_000_000.0)} " +
                        "overrun_ms=${"%.2f".format(frame.overrunNanos / 1_000_000.0)}",
                )
            }
            appendLine("FRAME_SAMPLES_END")
            appendLine("measurement=RAW_SHELL_INPUT_REAL_CHOREOGRAPHER")
            appendLine("input_pacing=RENDERED_FRAME_QUIESCENCE_BARRIER")
            appendLine("activity_launch=DIRECT_MAIN_ACTIVITY_START_NO_IDLE_SYNC")
            appendLine("classification=DEBUG_API36_EMULATOR_SANITY_ONLY")
            appendLine("RELEASE_PROFILEABLE_PF=NOT_VERIFIED")
            appendLine("PHYSICAL_DEVICE_PF=NOT_VERIFIED")
        }
        File(out, "jankstats-root.txt").writeText(report)
        phase("report_written")
        phase("activity_cleanup_deferred_to_isolated_process_exit")
        assertTrue(report, pass)
    }

    private fun launchMainActivityWithoutIdleSync(
        instrumentation: android.app.Instrumentation,
        timeoutMillis: Long,
        onProgress: (String) -> Unit,
    ): MainActivity {
        val target = instrumentation.targetContext
        val intent = Intent(target, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        target.startActivity(intent)

        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        var poll = 0
        while (SystemClock.uptimeMillis() < deadline) {
            var resumed: MainActivity? = null
            instrumentation.runOnMainSync {
                resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>()
                    .firstOrNull()
            }
            if (resumed != null) return requireNotNull(resumed)
            poll += 1
            if (poll == 1 || poll % 10 == 0) onProgress("activity_resume_poll_$poll")
            SystemClock.sleep(200L)
        }
        onProgress("activity_resume_timeout")
        error("MainActivity did not reach RESUMED within ${timeoutMillis}ms")
    }

    private fun waitForPrimaryWorldsBounds(
        instrumentation: android.app.Instrumentation,
        timeoutMillis: Long,
        onProgress: (String) -> Unit,
    ): Rect {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        var poll = 0
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val node = root?.let { findByContentDescription(it, "Primary worlds") }
            if (node != null) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (bounds.width() > 0 && bounds.height() > 0) return bounds
            }
            poll += 1
            if (poll == 1 || poll % 10 == 0) onProgress("nav_lookup_poll_$poll")
            SystemClock.sleep(200L)
        }
        onProgress("nav_lookup_timeout")
        error("Primary worlds accessibility surface did not become available within ${timeoutMillis}ms")
    }

    private fun findByContentDescription(node: AccessibilityNodeInfo, expected: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString() == expected) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = findByContentDescription(child, expected)
            if (found != null) return found
        }
        return null
    }

    private fun percentile(values: List<Double>, percentile: Int): Double {
        if (values.isEmpty()) return Double.POSITIVE_INFINITY
        val sorted = values.sorted()
        val index = (ceil(percentile / 100.0 * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }
}