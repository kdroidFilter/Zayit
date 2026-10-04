package io.github.kdroidfilter.seforimapp.core.e2e

import androidx.compose.foundation.gestures.scrollBy
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random

/**
 * A fast scroll through a book, a big wheel step every frame, down then up: the book scrollbar's thumb must
 * follow the text, never going back nor leaping ahead. Knobs: `ZAYIT_E2E_BOOK`, `ZAYIT_E2E_STEP_PX`.
 */
object E2eScrollbarScenario {
    private const val FRAMES = 600
    private const val FRAME_MS = 16L
    private const val LOAD_LIMIT_MS = 20_000L

    // A frame moves the thumb by about step / book height; ten times the median is a jump.
    private const val JUMP_FACTOR = 10f
    private const val BACK_TOLERANCE = 0.002f
    private const val EDGE = 0.99f
    private const val JUMPS = 12
    private const val NEAR = 15
    private const val QUERIES = 200
    private const val SETTLE_MS = 1000L

    suspend fun run(sc: E2eScenario) {
        if (E2e.scenario != "scrollbar") return
        val graph = sc.graph()
        val title = System.getenv("ZAYIT_E2E_BOOK") ?: "בראשית"
        val book = graph.repository.getBookByTitle(title)?.id ?: error("book $title not found")
        val step = System.getenv("ZAYIT_E2E_STEP_PX")?.toFloatOrNull() ?: 3000f
        openBook(sc, book)
        var jumps = 0
        val perf = StringBuilder()
        for (dir in listOf(1f, -1f)) {
            val list = E2e.bookScrollbar?.first ?: error("no book scrollbar")
            E2e.bookScrollbarTrace.clear()
            perf.appendLine(
                E2ePerf.measure(if (dir > 0) "scroll-down" else "scroll-up") {
                    for (f in 0 until FRAMES) {
                        list.scrollBy(dir * step)
                        delay(FRAME_MS)
                        // The wheel goes on past the loaded pages, as a user's would: stop at the book's end only
                        val pos = E2e.bookScrollbar?.second ?: 0f
                        if (if (dir > 0) pos >= 1f else pos <= 0f) break
                    }
                },
            )
            delay(300)
            // Up to the book's edge: there the list settles on the last lines' real heights, a separate matter
            val all = synchronized(E2e.bookScrollbarTrace) { E2e.bookScrollbarTrace.toList() }
            val edge = all.indexOfFirst { if (dir > 0) it >= EDGE else it <= 1f - EDGE }
            val trace = if (edge < 0) all else all.take(edge + 1)
            val deltas = trace.zipWithNext { a, b -> (b - a) * dir }
            val typical = deltas.filter { it > 0f }.sorted().let { if (it.isEmpty()) 0f else it[it.size / 2] }
            deltas.forEachIndexed { i, d ->
                if (d < -BACK_TOLERANCE || d > typical * JUMP_FACTOR) {
                    jumps++
                    sc.note("JUMP dir=$dir sample $i: ${trace[i]} -> ${trace[i + 1]} (typical step $typical)")
                }
            }
            sc.note("dir=$dir samples=${trace.size} typical=$typical end=${trace.lastOrNull()}")
            sc.step(if (dir > 0) "s1-down" else "s2-up", 300)
        }
        sc.note("SCROLLBAR jumps=$jumps")
        // In a fresh tab, where only the pages around the start are loaded: a far jump rebuilds the pager
        openBook(sc, book)
        jumpPerf(sc, book, perf)
        sc.note("PERF\n$perf")
    }

    private suspend fun openBook(
        sc: E2eScenario,
        book: Long,
    ) {
        sc
            .graph()
            .desktopManager.windows.value
            .first()
            .tabsViewModel
            .openTab(TabsDestination.BookContent(bookId = book, tabId = UUID.randomUUID().toString()))
        E2e.bookScrollbar = null
        withTimeoutOrNull(LOAD_LIMIT_MS) { while (E2e.bookScrollbar == null) delay(100) }
        sc.step("s0-book", 1000)
    }

    /** Jumps across the book as the scrollbar's tap does: time to the target line on screen, then the settle. */
    private suspend fun jumpPerf(
        sc: E2eScenario,
        book: Long,
        perf: StringBuilder,
    ) {
        val graph = sc.graph()
        val tabId =
            graph.desktopManager.windows.value
                .first()
                .tabsViewModel.state.value
                .let { it.tabs[it.selectedTabIndex].destination.tabId }
        val vm = E2e.bookViewModel(tabId) ?: return sc.note("no book ViewModel")
        val lines = graph.repository.getLines(book, 0, Int.MAX_VALUE)
        val indexOf = lines.associate { it.id to it.lineIndex }
        val reach = ArrayList<Long>()
        for (pageSize in listOf(10, 50)) {
            val t0 = System.nanoTime()
            repeat(QUERIES) { graph.repository.getLines(book, (it * 37) % lines.size, (it * 37) % lines.size + pageSize) }
            perf.appendLine("getLines($pageSize): %.3fms".format((System.nanoTime() - t0) / 1e6 / QUERIES))
        }
        perf.appendLine(
            E2ePerf.measure("jumps") {
                lines.shuffled(Random(1)).take(JUMPS).forEach { target ->
                    val t0 = System.nanoTime()
                    vm.onEvent(BookContentEvent.ContentScrollToLineIndex(target.lineIndex))
                    withTimeoutOrNull(LOAD_LIMIT_MS) { while (!nearOnScreen(target.lineIndex, indexOf)) delay(1) }
                    reach += (System.nanoTime() - t0) / 1_000_000
                    delay(SETTLE_MS)
                }
            },
        )
        perf.appendLine("jump to its lines on screen (ms): ${reach.sorted()}")
    }

    /** Whether lines within [NEAR] of [lineIndex] are on screen. */
    private fun nearOnScreen(
        lineIndex: Int,
        indexOf: Map<Long, Int>,
    ): Boolean {
        val visible =
            E2e.bookScrollbar
                ?.first
                ?.layoutInfo
                ?.visibleItemsInfo ?: return false
        return visible.any { item ->
            val index = (item.key as? Long)?.let(indexOf::get) ?: return@any false
            abs(index - lineIndex) <= NEAR
        }
    }
}
