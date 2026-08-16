package com.veltrix.hom.vnext

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.metrics.performance.FrameDataApi31
import androidx.metrics.performance.JankStats
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Collections
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Hardware-emulator sanity gate for Veltrix UI/CPU frame cost.
 *
 * This deliberately does not gate total/GPU duration or JankStats.isJank on CI emulators:
 * emulator GPU/fence timing is not representative of physical hardware. Instead API31+
 * JankStats lets us isolate the UI-thread and non-GPU CPU portions of each frame. Physical
 * device performance and human touch-feel remain a separate external proof boundary.
 *
 * ActivityScenarioRule is intentional: unlike ComposeTestRule it does not virtualize the
 * Compose animation clock, so the shell taps below drive production Choreographer timing.
 */
class JankStatsPerformanceInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private data class FrameSample(
        val uiNanos: Long,
        val cpuNanos: Long,
        val totalNanos: Long,
        val overrunNanos: Long,
        val jank: Boolean,
    )

    private data class WindowGeometry(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val density: Float,
    )

    @Test
    fun warmedPrimaryNavigationKeepsUiAndCpuWithinEmulatorSanityBudget() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val samples = Collections.synchronizedList(ArrayList<FrameSample>(256))
        var jankStats: JankStats? = null
        var geometry: WindowGeometry? = null

        activityRule.scenario.onActivity { activity ->
            val decor = activity.window.decorView
            val location = IntArray(2)
            decor.getLocationOnScreen(location)
            geometry = WindowGeometry(
                left = location[0],
                top = location[1],
                width = decor.width,
                height = decor.height,
                density = activity.resources.displayMetrics.density,
            )
            jankStats = JankStats.createAndTrack(activity.window) { volatileFrame ->
                val frame = volatileFrame as? FrameDataApi31 ?: return@createAndTrack
                // Copy only primitive scalar values and return immediately; FrameData is reused.
                samples.add(
                    FrameSample(
                        uiNanos = frame.frameDurationUiNanos,
                        cpuNanos = frame.frameDurationCpuNanos,
                        totalNanos = frame.frameDurationTotalNanos,
                        overrunNanos = frame.frameOverrunNanos,
                        jank = frame.isJank,
                    ),
                )
            }.also { it.isTrackingEnabled = false }
        }

        val g = requireNotNull(geometry)
        assertTrue("MainActivity window must be laid out before PF measurement: $g", g.width > 0 && g.height > 0)

        // The bottom navigation lens is 68dp high inside 10dp vertical shell padding. Aim at
        // its visual center (~44dp above the window bottom) instead of a magic device pixel.
        val navY = g.top + g.height - (44f * g.density).roundToInt()
        val navX = intArrayOf(
            g.left + g.width / 8,
            g.left + 3 * g.width / 8,
            g.left + 5 * g.width / 8,
            g.left + 7 * g.width / 8,
        )

        fun shellTap(x: Int, y: Int) {
            val pfd = instrumentation.uiAutomation.executeShellCommand("input tap $x $y")
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
        }

        fun navigationCycle(settleMs: Long) {
            // Personal -> Store -> Projects -> Home. Real shell input keeps this independent
            // from Compose test-clock auto-advancement and exercises the persistent shell.
            for (index in intArrayOf(1, 2, 3, 0)) {
                shellTap(navX[index], navY)
                SystemClock.sleep(settleMs)
            }
        }

        // Let initial network/cache work settle, then warm every primary destination once so
        // class loading/JIT/cold composition does not masquerade as steady-state interaction cost.
        SystemClock.sleep(2_000)
        navigationCycle(420)
        instrumentation.waitForIdleSync()
        synchronized(samples) { samples.clear() }

        activityRule.scenario.onActivity { requireNotNull(jankStats).isTrackingEnabled = true }
        repeat(3) { navigationCycle(420) }
        SystemClock.sleep(450)
        activityRule.scenario.onActivity { requireNotNull(jankStats).isTrackingEnabled = false }

        val snapshot = synchronized(samples) { samples.toList() }
        val uiMs = snapshot.map { it.uiNanos / 1_000_000.0 }
        val cpuMs = snapshot.map { it.cpuNanos / 1_000_000.0 }
        val totalMs = snapshot.map { it.totalNanos / 1_000_000.0 }
        val overrunMs = snapshot.map { it.overrunNanos / 1_000_000.0 }

        fun percentile(values: List<Double>, percentile: Int): Double {
            if (values.isEmpty()) return Double.POSITIVE_INFINITY
            val sorted = values.sorted()
            val index = (ceil(percentile / 100.0 * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)
            return sorted[index]
        }

        val uiP50 = percentile(uiMs, 50)
        val uiP95 = percentile(uiMs, 95)
        val uiP99 = percentile(uiMs, 99)
        val uiMax = uiMs.maxOrNull() ?: Double.POSITIVE_INFINITY
        val cpuP50 = percentile(cpuMs, 50)
        val cpuP95 = percentile(cpuMs, 95)
        val totalP95 = percentile(totalMs, 95)
        val overrunP95 = percentile(overrunMs, 95)
        val jankCount = snapshot.count { it.jank }
        val jankPct = if (snapshot.isEmpty()) 100.0 else jankCount * 100.0 / snapshot.size

        // Pre-declared broad CI-emulator sanity budgets. These reject pathological UI/CPU stalls
        // without pretending that a debug emulator proves 60/90/120Hz physical-device quality.
        val minSamples = 24
        val uiP95LimitMs = 80.0
        val cpuP95LimitMs = 160.0
        val uiMaxLimitMs = 250.0
        val pass = snapshot.size >= minSamples &&
            uiP95 <= uiP95LimitMs &&
            cpuP95 <= cpuP95LimitMs &&
            uiMax <= uiMaxLimitMs

        val report = buildString {
            appendLine(
                "JANKSTATS_UI_CPU samples=${snapshot.size} " +
                    "ui_p50_ms=${"%.2f".format(uiP50)} ui_p95_ms=${"%.2f".format(uiP95)} " +
                    "ui_p99_ms=${"%.2f".format(uiP99)} ui_max_ms=${"%.2f".format(uiMax)} " +
                    "cpu_p50_ms=${"%.2f".format(cpuP50)} cpu_p95_ms=${"%.2f".format(cpuP95)} " +
                    "total_p95_ms=${"%.2f".format(totalP95)} overrun_p95_ms=${"%.2f".format(overrunP95)} " +
                    "jank=${jankCount} jank_pct=${"%.2f".format(jankPct)}",
            )
            appendLine(
                "JANKSTATS_UI_CPU_EMULATOR=${if (pass) "PASS" else "FAIL"} " +
                    "min_samples=$minSamples ui_p95_limit_ms=$uiP95LimitMs " +
                    "cpu_p95_limit_ms=$cpuP95LimitMs ui_max_limit_ms=$uiMaxLimitMs",
            )
            appendLine("GPU_TOTAL_JANK=DIAGNOSTIC_ONLY physical_device_claim=NONE")
            appendLine("PHYSICAL_DEVICE_PF=NOT_VERIFIED")
        }
        target.openFileOutput(REPORT_FILE, Context.MODE_PRIVATE).bufferedWriter().use { it.write(report) }
        Log.i("VELTRIX_PF", report.replace('\n', ' '))

        assertTrue(report, pass)
    }

    private companion object {
        const val REPORT_FILE = "jankstats-ui-cpu.txt"
    }
}
