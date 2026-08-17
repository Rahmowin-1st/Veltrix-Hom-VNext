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
import kotlin.math.ceil

/**
 * Stage 90 performance sanity for the actual authenticated MainActivity root.
 *
 * Deliberately has no Compose test rule: Compose test clocks can virtualize animation time and
 * contaminate JankStats. Navigation is driven by real shell input against the accessibility bounds
 * of the production primary-worlds control, so Choreographer owns timing exactly as it does at runtime.
 *
 * MainActivity is started directly rather than through ActivityScenario. ActivityScenario waits on
 * instrumentation-idle lifecycle synchronization; the real root intentionally owns an infinite ambient
 * transition, so that synchronization is not a valid readiness boundary for this PF test. We instead
 * start the production Activity normally and resolve its RESUMED instance from the Android lifecycle
 * monitor with a bounded timeout. This preserves real runtime timing without a Compose test clock.
 *
 * This remains debug/API36-emulator sanity only; it is not a release/profileable or physical-device claim.
 */
class RootStage90PerformanceInstrumentedTest {
    private data class FrameSample(
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

        val samples = Collections.synchronizedList(ArrayList<FrameSample>(256))
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
                    uiNanos = frame.frameDurationUiNanos,
                    cpuNanos = frame.frameDurationCpuNanos,
                    totalNanos = frame.frameDurationTotalNanos,
                    overrunNanos = frame.frameOverrunNanos,
                    jank = frame.isJank,
                )
            }.also { it.isTrackingEnabled = false }
        }
        phase("tracker_created")

        fun shellTap(x: Int, y: Int) {
            // `input tap` is fire-and-settle input. Reading this shell pipe to EOF can block
            // indefinitely on some emulator/adb combinations even after the tap was delivered.
            // Close immediately; the bounded settle below owns timing, not transport EOF.
            instrumentation.uiAutomation.executeShellCommand("input tap $x $y").close()
        }

        fun navigationCycle(settleMs: Long, label: String) {
            // Personal -> Store -> Projects -> Home on the persistent production shell.
            for (index in intArrayOf(1, 2, 3, 0)) {
                phase("${label}_tap_$index")
                shellTap(navX[index], navY)
                SystemClock.sleep(settleMs)
            }
        }

        // Exclude cold composition, class loading and first network materialization from the
        // steady-state interaction sample; every destination is exercised once before capture.
        SystemClock.sleep(1_500L)
        phase("warmup_start")
        navigationCycle(420L, "warmup")
        // The root intentionally owns a low-frequency infinite ambient transition. Waiting for
        // global instrumentation idleness here can therefore stall despite a healthy UI. A fixed
        // post-input settle is the correct boundary for this real-Choreographer PF sample.
        SystemClock.sleep(350L)
        synchronized(samples) { samples.clear() }
        phase("warmup_complete")

        instrumentation.runOnMainSync { requireNotNull(tracker).isTrackingEnabled = true }
        phase("tracking_enabled")
        repeat(3) { cycle -> navigationCycle(420L, "measure_$cycle") }
        SystemClock.sleep(450L)
        instrumentation.runOnMainSync { requireNotNull(tracker).isTrackingEnabled = false }
        phase("tracking_disabled")

        // Snapshot and persist acceptance evidence before process cleanup. The Stage90 shell already
        // force-stops target/test packages between isolated invocations, so Activity teardown is not
        // part of the PF measurement or a prerequisite for evidence durability.
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
            appendLine("measurement=RAW_SHELL_INPUT_REAL_CHOREOGRAPHER")
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
            // Do not call waitForIdleSync(): the redesigned root intentionally contains an infinite
            // ambient Compose transition, so global "idle" is not a valid readiness signal. Poll the
            // accessibility tree directly and keep every retry bounded by the outer uptime deadline.
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
