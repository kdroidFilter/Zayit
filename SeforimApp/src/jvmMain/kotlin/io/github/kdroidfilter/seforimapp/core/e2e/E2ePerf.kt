package io.github.kdroidfilter.seforimapp.core.e2e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import java.lang.management.ManagementFactory

/**
 * Frame times of one window for [E2eSolarPerfScenario]: [Record] in the window logs each frame's start while a
 * [measure] runs. Off unless the harness is on.
 */
object E2ePerf {
    @Volatile
    private var frames: MutableList<Long>? = null

    /** Records this composition's frames while measuring; nothing unless the harness is on. */
    @Composable
    fun Record() {
        if (!E2e.enabled) return
        LaunchedEffect(Unit) {
            while (true) withFrameNanos { now -> frames?.let { synchronized(it) { it.add(now) } } }
        }
    }

    /** Runs [block] while recording, and sums up its frames and the process' CPU time. */
    suspend fun measure(
        label: String,
        block: suspend () -> Unit,
    ): String {
        val list = mutableListOf<Long>()
        val os = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
        val cpu0 = os.processCpuTime
        val wall0 = System.nanoTime()
        frames = list
        block()
        frames = null
        val wall = System.nanoTime() - wall0
        val cpu = os.processCpuTime - cpu0
        val gaps = synchronized(list) { list.zipWithNext { a, b -> (b - a) / 1e6 } }.sorted()
        if (gaps.isEmpty()) return "$label: no frame"
        fun pct(p: Double) = gaps[((gaps.size - 1) * p).toInt()]
        return "%s: fps=%.1f frame avg=%.2fms p50=%.2f p95=%.2f p99=%.2f max=%.2f cpu=%.0f%%".format(
            label,
            gaps.size * 1e9 / wall,
            gaps.average(),
            pct(0.5),
            pct(0.95),
            pct(0.99),
            gaps.last(),
            cpu * 100.0 / wall,
        )
    }
}
