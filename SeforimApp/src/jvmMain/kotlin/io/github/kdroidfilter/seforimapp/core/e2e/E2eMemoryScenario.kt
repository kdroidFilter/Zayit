package io.github.kdroidfilter.seforimapp.core.e2e

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.features.home.widgets.decodeLayout
import io.github.kdroidfilter.seforimapp.features.home.widgets.encodeLayout
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.management.ManagementFactory
import java.util.Base64

/**
 * The memory the Home costs over a book (`ZAYIT_E2E_SCENARIO=memory`, with `ZAYIT_E2E_QUICK=1`): the process' private
 * working set (Task Manager's "Memory" column) and private bytes on the user's own Home, then on Bereshit, then back
 * and forth, each after `ZAYIT_E2E_SETTLE_MS` (`ZAYIT_E2E_ROUND_TRIPS` of them); written to `memory.txt`. With `ZAYIT_E2E_START_BOOK=1` the run starts on
 * the book instead, and only measures it: the cold-book baseline. `ZAYIT_E2E_WITHOUT` (widget ids, comma-separated)
 * drops widgets from the Home for the run, to see what each costs. Windows only.
 */
object E2eMemoryScenario {
    /** Drops the widgets `ZAYIT_E2E_WITHOUT` lists from the Home before the run opens it; returns the undo. */
    fun withoutWidgets(graph: AppGraph): () -> Unit {
        val ids =
            System
                .getenv("ZAYIT_E2E_WITHOUT")
                ?.split(",")
                ?.map(String::trim)
                ?.toSet() ?: return {}
        val settings = graph.appSettings
        val saved = settings.homeWidgetsLayoutFlow.value
        // Kept on disk too: a run killed before its undo must not lose the user's layout
        E2e.outDir?.let { File(it.apply { mkdirs() }, "layout-backup.txt").writeText(saved.orEmpty()) }
        // ZAYIT_E2E_SPAN (`earth:4x3`) resizes one of the remaining widgets, to see what its size costs
        val span =
            System.getenv("ZAYIT_E2E_SPAN")?.split(":")?.let { (id, size) ->
                id to size.split("x").map(String::toInt)
            }
        val layout =
            decodeLayout(saved)
                .filterNot { it.widget.id in ids }
                .map { placement ->
                    if (placement.widget.id != span?.first) return@map placement
                    val (w, h) = span.second
                    placement.copy(cell = placement.cell.copy(w = w, h = h))
                }
        settings.setHomeWidgetsLayout(encodeLayout(layout))
        return { settings.setHomeWidgetsLayout(saved) }
    }

    suspend fun run(sc: E2eScenario) {
        if (E2e.scenario != "memory") return
        val settle = System.getenv("ZAYIT_E2E_SETTLE_MS")?.toLongOrNull() ?: 20_000L
        val bereshit =
            sc
                .graph()
                .repository
                .getBookByTitle("בראשית")
                ?.id ?: error("book not found")
        val tabs =
            sc
                .graph()
                .desktopManager.windows.value
                .first()
                .tabsViewModel
        val report = StringBuilder()

        suspend fun measure(label: String) {
            delay(settle)
            report.appendLine("$label: ${sample()}")
        }

        suspend fun show(bookId: Long) {
            val tabId = tabs.state.value.let { it.tabs[it.selectedTabIndex].destination.tabId }
            tabs.replaceCurrentTabDestination(TabsDestination.BookContent(bookId = bookId, tabId = tabId))
        }

        if (E2eScenario.startsOnBook) {
            measure("book-cold")
        } else {
            measure("home")
            val roundTrips = System.getenv("ZAYIT_E2E_ROUND_TRIPS")?.toIntOrNull() ?: ROUND_TRIPS
            repeat(roundTrips) { round ->
                show(bereshit)
                measure("book-$round")
                show(HOME)
                measure("home-$round")
            }
        }
        sc.note(report.toString().trim())
        E2e.outDir?.let { File(it, "memory.txt").writeText(report.toString()) }
    }

    /** Private working set, private bytes and thread count from Windows' performance counters, and the Java heap in use, in MB. */
    private suspend fun sample(): String =
        withContext(Dispatchers.IO) {
            val pid = ProcessHandle.current().pid()
            val query =
                "\$ProgressPreference = 'SilentlyContinue'; " +
                    "\$p = Get-CimInstance Win32_PerfFormattedData_PerfProc_Process -Filter 'IDProcess=$pid'; " +
                    "'{0} {1} {2}' -f \$p.WorkingSetPrivate, \$p.PrivateBytes, \$p.ThreadCount"
            // Encoded: Java's Windows command line would mangle the script's quotes
            val encoded = Base64.getEncoder().encodeToString(query.toByteArray(Charsets.UTF_16LE))
            val out =
                ProcessBuilder("powershell", "-NoProfile", "-EncodedCommand", encoded)
                    .redirectErrorStream(true)
                    .start()
                    .inputStream
                    .bufferedReader()
                    .readLines()
                    .last { it.isNotBlank() }
                    .trim()
            val (privateWs, privateBytes, nativeThreads) = out.split(" ").map { it.toLong() }
            val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage
            "privateWS=${privateWs / MB}MB privateBytes=${privateBytes / MB}MB heapUsed=${heap.used / MB}MB " +
                "heapCommitted=${heap.committed / MB}MB threads=${Thread.getAllStackTraces().size} nativeThreads=$nativeThreads"
        }

    private const val ROUND_TRIPS = 3
    private const val HOME = -1L
    private const val MB = 1024 * 1024
}
