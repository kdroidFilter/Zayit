package io.github.kdroidfilter.seforimapp.core.e2e

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** What the reader drew, for [E2eReaderPerfScenario]: lines and commentaries shown with their text, and the book list. */
object E2eReader {
    val readyLines: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    val readyCommentaries: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    /** Lines drawn as a blank placeholder (text still parsing), counted per frame by the scenario. */
    val blankLines: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    private val marks = ArrayList<Pair<String, Long>>()

    /** Records the first time [label] happens since the last [timeline]. */
    fun mark(label: String) {
        if (!E2e.enabled) return
        val now = System.nanoTime()
        synchronized(marks) { if (marks.none { it.first == label }) marks.add(label to now) }
    }

    /** The marks since [start], relative to it, and clears them. */
    fun timeline(start: Long): String =
        synchronized(marks) {
            marks.sortedBy { it.second }.joinToString(" ") { "${it.first}@${(it.second - start) / 100_000 / 10.0}" }.also { marks.clear() }
        }

    @Volatile
    var book: Triple<Long, LazyListState, Boolean>? = null

    fun lineDrawn(
        id: Long,
        ready: Boolean,
    ) {
        mark(if (ready) "line.ready" else "line.blank")
        if (ready) {
            readyLines.add(id)
            blankLines.remove(id)
        } else {
            blankLines.add(id)
        }
    }

    fun commentaryDrawn(
        id: Long,
        ready: Boolean,
    ) {
        mark(if (ready) "com.ready" else "com.blank")
        if (ready) readyCommentaries.add(id)
    }

    fun bookShown(
        bookId: Long,
        list: LazyListState,
        visible: Boolean,
    ) {
        mark("book.items=${list.layoutInfo.totalItemsCount}.visible=$visible")
        book = Triple(bookId, list, visible)
    }
}

/**
 * The reader's latencies (`ZAYIT_E2E_SCENARIO=reader`): a book opened in a new tab until its text is on screen, a
 * line selected until its commentaries are drawn, and a fast scroll (blank lines and frame times). Knobs:
 * `ZAYIT_E2E_BOOKS` (titles, `|`-separated).
 */
object E2eReaderPerfScenario {
    private const val LIMIT_MS = 20_000L
    private val SELECTIONS = System.getenv("ZAYIT_E2E_SELECTIONS")?.toIntOrNull() ?: 8
    private const val SCROLL_FRAMES = 300
    private const val FRAME_MS = 16L

    suspend fun run(sc: E2eScenario) {
        if (E2e.scenario != "reader") return
        val graph = sc.graph()
        val titles = System.getenv("ZAYIT_E2E_BOOKS")?.split('|') ?: listOf("בראשית", "ברכות", "שולחן ערוך, אורח חיים")
        val report = StringBuilder()
        System.getenv("ZAYIT_E2E_OPENS")?.toIntOrNull()?.let { rounds ->
            openLoop(sc, titles, rounds)
            return
        }
        for (title in titles) {
            val book = graph.repository.getBookByTitle(title) ?: error("book $title not found")
            val tabs =
                graph.desktopManager.windows.value
                    .first()
                    .tabsViewModel

            // --- open
            E2eReader.book = null
            E2eReader.readyLines.clear()
            val t0 = System.nanoTime()
            E2eReader.timeline(t0)
            tabs.openTab(TabsDestination.BookContent(bookId = book.id, tabId = UUID.randomUUID().toString()))
            val opened = waitFor { textOnScreen(book.id) }
            val tOpen = ms(t0)
            delay(300)
            sc.note("READER open timeline: ${E2eReader.timeline(t0)}")
            delay(1500)

            // --- commentaries: open the pane, then select lines with commentaries
            val tabId = tabs.state.value.let { it.tabs[it.selectedTabIndex].destination.tabId }
            val vm = E2e.bookViewModel(tabId) ?: error("no book ViewModel")
            val lines = graph.repository.getLines(book.id, 0, minOf(book.totalLines, 400))
            val withComments =
                lines
                    .filter { line ->
                        graph.repository.getCommentarySummariesForLines(listOf(line.id)).any {
                            it.link.connectionType == ConnectionType.COMMENTARY
                        }
                    }.take(SELECTIONS + 1)
            vm.onEvent(BookContentEvent.LineSelected(withComments.first()))
            vm.onEvent(BookContentEvent.ToggleCommentaries)
            waitFor { E2eReader.readyCommentaries.isNotEmpty() }
            delay(1500)
            val selectTimes = ArrayList<Long>()
            val hoverTimes = ArrayList<Long>()
            for ((i, line) in withComments.drop(1).withIndex()) {
                // Every other line is hovered first, as a pointer rests on a line before clicking it
                val hovered = i % 2 == 1
                if (hovered) {
                    val state = vm.uiState.value
                    val ids =
                        state.content.selectedCommentatorsByBook[book.id]
                            .orEmpty()
                            .ifEmpty { state.content.selectedCommentatorIds }
                    state.providers?.prefetchCommentaries?.invoke(line.id, ids)
                    delay(150)
                }
                E2eReader.readyCommentaries.clear()
                val t = System.nanoTime()
                E2eReader.timeline(t)
                vm.onEvent(BookContentEvent.LineSelected(line))
                val ok = waitFor { E2eReader.readyCommentaries.isNotEmpty() }
                (if (hovered) hoverTimes else selectTimes) += if (ok) ms(t) else -1
                delay(700)
                sc.note("READER select timeline: ${E2eReader.timeline(t)}")
            }
            sc.step("r-${book.id}-commentaries", 300)
            vm.onEvent(BookContentEvent.ToggleCommentaries)
            delay(1000)

            // --- fast scroll: wheel steps every frame, counting frames with blank (still parsing) lines on screen
            val list = E2eReader.book?.second ?: error("no book list")
            var blankFrames = 0
            var frames = 0
            val perf =
                E2ePerf.measure("scroll") {
                    repeat(SCROLL_FRAMES) {
                        list.scrollBy(400f)
                        delay(FRAME_MS)
                        frames++
                        val visible = list.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Long }
                        if (visible.any { it in E2eReader.blankLines } || visible.isEmpty()) blankFrames++
                    }
                }
            val line =
                "%-26s open=%5dms%s | select→commentaries ms=%s hovered=%s | scroll blank frames=%d/%d | %s".format(
                    title.take(26),
                    tOpen,
                    if (opened) "" else "(TIMEOUT)",
                    selectTimes,
                    hoverTimes,
                    blankFrames,
                    frames,
                    perf,
                )
            sc.note("READER $line")
            report.appendLine(line)
        }
        sc.note("READER REPORT\n$report")
    }

    /** Opens each book [rounds] times in a new tab, closed after, for a profile of the open. */
    private suspend fun openLoop(
        sc: E2eScenario,
        titles: List<String>,
        rounds: Int,
    ) {
        val graph = sc.graph()
        val tabs =
            graph.desktopManager.windows.value
                .first()
                .tabsViewModel
        val books = titles.map { graph.repository.getBookByTitle(it) ?: error("book $it not found") }
        val times = books.associate { it.title to ArrayList<Long>() }
        repeat(rounds) {
            for (book in books) {
                E2eReader.book = null
                E2eReader.readyLines.clear()
                val t0 = System.nanoTime()
                tabs.openTab(TabsDestination.BookContent(bookId = book.id, tabId = UUID.randomUUID().toString()))
                val ok = waitFor { textOnScreen(book.id) }
                times.getValue(book.title) += if (ok) ms(t0) else -1
                delay(400)
                tabs.onEvent(TabsEvents.OnClose(tabs.state.value.selectedTabIndex))
                delay(400)
            }
        }
        times.forEach { (title, t) -> sc.note("READER OPENS %-26s median=%d %s".format(title.take(26), t.sorted()[t.size / 2], t)) }
    }

    private fun textOnScreen(bookId: Long): Boolean {
        val (id, list, visible) = E2eReader.book ?: return false
        if (id != bookId || !visible) return false
        val keys = list.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Long }
        return keys.isNotEmpty() && keys.all { it in E2eReader.readyLines }
    }

    private suspend fun waitFor(condition: () -> Boolean): Boolean =
        withTimeoutOrNull(LIMIT_MS) {
            while (!condition()) delay(1)
            true
        } ?: false

    private fun ms(start: Long) = (System.nanoTime() - start) / 1_000_000
}
