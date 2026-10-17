package io.github.kdroidfilter.seforimapp.core.e2e

import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforimapp.features.home.widgets.encodeLayout
import io.github.kdroidfilter.seforimapp.features.home.widgets.homeWidgets
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.concurrent.thread

/**
 * Opening and switching tabs on the default Home, with its Filament widgets (`ZAYIT_E2E_SCENARIO=tabopen`): each
 * open, then each switch back, has its longest UI-thread stall noted, and the UI thread's stack is sampled while it
 * stalls (`stalls.txt`), to see what holds it; the 2nd and last opens are exported at fixed times after the open, to
 * see when the widgets show (an export itself holds the UI thread some 400 ms, its own stack in `stalls.txt`).
 * `ZAYIT_E2E_OPENS` tabs are opened (6 by default, past the retained compositions so some are torn down). The user's
 * widget layout is put back.
 */
object E2eTabOpenScenario {
    private const val STALL_MS = 100L
    private const val SAMPLE_MS = 20L

    suspend fun run(sc: E2eScenario) {
        if (E2e.scenario != "tabopen") return
        coroutineScope { runIn(this, sc) }
    }

    private suspend fun runIn(
        @StructuredScope scope: CoroutineScope,
        sc: E2eScenario,
    ) {
        val settings = sc.graph().appSettings
        val window =
            sc
                .graph()
                .desktopManager.windows.value
                .first()
        val saved = settings.homeWidgetsLayoutFlow.value
        val opens = System.getenv("ZAYIT_E2E_OPENS")?.toIntOrNull() ?: 6
        val ui = withContext(Dispatchers.Main) { Thread.currentThread() }
        val sampler = StallSampler(ui)
        try {
            settings.setHomeWidgetsLayout(encodeLayout(homeWidgets))
            window.tabsViewModel.onEvent(TabsEvents.OnAdd)
            sc.step("t0-first-home", 10_000)
            sampler.start()
            val beat = scope.launch(Dispatchers.Main) { sampler.beat() }
            repeat(opens) { i ->
                sampler.label = "open ${i + 1}"
                window.tabsViewModel.onEvent(TabsEvents.OnAdd)
                if (i == 1 || i == opens - 1) {
                    // When the new Home's widgets show: exports at fixed times after the open
                    var waited = 0L
                    for (at in listOf(150L, 400L, 800L, 1500L, 2500L)) {
                        delay(at - waited)
                        waited = at
                        sc.step("t-open${i + 1}-${at}ms", 0)
                    }
                    delay(4000 - waited)
                } else {
                    delay(4000)
                }
                sc.note("open ${i + 1}: longest stall ${sampler.takeLongest()}ms")
            }
            sc.step("t1-after-opens", 1000)
            val count = window.tabsViewModel.state.value.tabs.size
            repeat(count) { i ->
                sampler.label = "select $i"
                window.tabsViewModel.onEvent(TabsEvents.OnSelect(i))
                delay(2500)
                sc.note("select $i: longest stall ${sampler.takeLongest()}ms")
            }
            sc.step("t2-after-switches", 1000)
            beat.cancel()
        } finally {
            sampler.stop()
            E2e.outDir?.let { File(it, "stalls.txt").writeText(sampler.report()) }
            settings.setHomeWidgetsLayout(saved)
        }
    }

    /**
     * Watches the UI thread from its own thread: a beat posted to the UI thread every [SAMPLE_MS] and, while it is
     * [STALL_MS] late, the UI thread's stack, counted by top frames.
     */
    private class StallSampler(
        private val ui: Thread,
    ) {
        @Volatile var label = ""

        @Volatile private var lastBeat = System.nanoTime()

        @Volatile private var longest = 0L

        @Volatile private var running = true
        private val stacks = LinkedHashMap<String, Int>()
        private var worker: Thread? = null

        fun start() {
            worker =
                thread(isDaemon = true, name = "e2e-stall-sampler") {
                    while (running) {
                        runCatching { Thread.sleep(SAMPLE_MS) }
                        val late = (System.nanoTime() - lastBeat) / 1_000_000
                        if (late > longest) longest = late
                        if (late >= STALL_MS) {
                            val top =
                                ui.stackTrace
                                    .take(14)
                                    .joinToString("\n") { "    at $it" }
                            synchronized(stacks) { stacks.merge("[$label]\n$top", 1, Int::plus) }
                        }
                    }
                }
        }

        /** Beats on the UI thread until stopped; run from the scenario's coroutine on the main dispatcher. */
        suspend fun beat() {
            while (running) {
                lastBeat = System.nanoTime()
                delay(SAMPLE_MS / 2)
            }
        }

        fun takeLongest(): Long = longest.also { longest = 0 }

        fun stop() {
            running = false
            worker?.interrupt()
        }

        fun report(): String =
            synchronized(stacks) {
                stacks.entries
                    .sortedByDescending { it.value }
                    .joinToString("\n\n") { (stack, n) -> "${n * SAMPLE_MS}ms ~ $stack" }
            }
    }
}
