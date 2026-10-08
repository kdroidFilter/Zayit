@file:OptIn(ExperimentalNucleusApi::class)

package io.github.kdroidfilter.seforimapp.core.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.tao.TabDragOrigin
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.ReaderPane
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellRect
import io.github.kdroidfilter.seforimapp.features.home.widgets.HOME_GRID_COLUMNS
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsLayout
import io.github.kdroidfilter.seforimapp.features.home.widgets.availableHomeWidgets
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * Random walk over everything a user can do to tabs, windows, desktops and panes, books, search, themes and the Home
 * widgets (seeded, so a failure replays), checking after every operation that no tab is lost or duplicated, that
 * every window shows a live group and that each window's tab list is its group's. Along the way it records every
 * uncaught exception and every stall of the main thread.
 *
 * Knobs: `ZAYIT_E2E_SEED`, `ZAYIT_E2E_OPS`, `ZAYIT_E2E_SETTLE` (ms between operations; 0 fires them back to back) and
 * `ZAYIT_E2E_BURST` (operations launched together, interleaving at their suspension points, to shake out races).
 */
object E2eTortureScenario {
    private val appSettings get() = E2eScenario.graph().appSettings
    private val homeLayout get() = HomeWidgetsLayout(appSettings)

    private const val OPS = 400
    private const val SETTLE_MS = 350L
    private const val CAPTURE_EVERY = 50
    private const val OPERATION_COUNT = 33
    private const val STALL_MS = 2000L
    private const val MAX_STALL_MS = 120_000L
    private const val BOOK_POOL = 300
    private const val SETTLED_CHECK_EVERY = 25
    private const val SETTLE_LIMIT_MS = 5000L
    private const val SETTLE_POLL_MS = 250L
    private const val RECENT_OPS = 12

    private lateinit var dm: DesktopManager
    private lateinit var sc: E2eScenario
    private val seed = System.getenv("ZAYIT_E2E_SEED")?.toIntOrNull() ?: 1
    private val random = Random(seed)
    private val ops = System.getenv("ZAYIT_E2E_OPS")?.toIntOrNull() ?: OPS
    private val settleMs = System.getenv("ZAYIT_E2E_SETTLE")?.toLongOrNull() ?: SETTLE_MS
    private val burst = (System.getenv("ZAYIT_E2E_BURST")?.toIntOrNull() ?: 1).coerceAtLeast(1)
    private val trace = System.getenv("ZAYIT_E2E_TRACE") == "1"
    private var bookId = -1L
    private var books: List<Long> = emptyList()
    private var failures = 0
    private var transients = 0

    /** The last operations, named in a STUCK report. */
    private val recent = ArrayDeque<String>()
    private val exceptions = AtomicInteger()
    private val stalls = AtomicInteger()

    @Volatile
    private var currentOp = "start"

    @Volatile
    private var lastBeat = System.currentTimeMillis()

    @Volatile
    private var watchdogPaused = false

    suspend fun run(scenario: E2eScenario) {
        if (E2e.scenario != "torture") return
        sc = scenario
        dm = scenario.graph().desktopManager
        bookId = scenario
            .graph()
            .repository
            .getBookByTitle("בראשית")
            ?.id ?: error("book not found")
        books = bookPool(scenario)
        dm.windows.value
            .drop(1)
            .forEach { dm.closeWindow(it.id) }
        delay(1000)
        val widgetsLayout = appSettings.homeWidgetsLayoutFlow.value
        sc.note("TORTURE seed=$seed ops=$ops settle=${settleMs}ms burst=$burst books=${books.size}")
        coroutineScope {
            val watchers = watch(this)
            try {
                var op = 1
                while (op <= ops) {
                    val batch = (op until minOf(op + burst, ops + 1)).toList()
                    removalTaken = false
                    // A burst: launched together, they interleave wherever an operation suspends
                    val names =
                        batch
                            .map { n ->
                                async {
                                    runCatching { operation(n) }.getOrElse { t ->
                                        exceptions.incrementAndGet()
                                        sc.note("op $n threw: $t\n${t.stackTraceToString().lines().take(12).joinToString("\n")}")
                                        "threw"
                                    }
                                }
                            }.awaitAll()
                    delay(settleMs)
                    batch.zip(names).forEach { (n, name) ->
                        check(n, name)
                        recent.addLast("$n:$name")
                        if (recent.size > RECENT_OPS) recent.removeFirst()
                    }
                    if (settleMs < SETTLE_MS && batch.any { it % SETTLED_CHECK_EVERY == 0 || it == ops }) settledCheck(batch.last())
                    if (batch.any { it % CAPTURE_EVERY == 0 }) {
                        // The capture and the GC run on the main thread: not a stall of the app's own
                        watchdogPaused = true
                        sc.step("t%03d".format(batch.last()), waitMs = 1500)
                        sc.note("heap after op ${batch.last()}: ${usedHeapMb()} MB")
                        lastBeat = System.currentTimeMillis()
                        watchdogPaused = false
                    }
                    op += batch.size
                }
            } finally {
                watchers.cancel()
                appSettings.setHomeWidgetsLayout(widgetsLayout)
            }
        }
        sc.note(
            "TORTURE done: $ops operations, $failures invariant failure(s), $transients transient(s), ${exceptions.get()} exception(s), " +
                "${stalls.get()} main-thread stall(s), heap ${usedHeapMb()} MB",
        )
    }

    /** Books to open at random: those under the first categories, so every kind of book shows up. */
    private suspend fun bookPool(scenario: E2eScenario): List<Long> {
        val repository = scenario.graph().repository
        return repository
            .getRootCategories()
            .flatMap { repository.getBooksUnderCategoryTree(it.id).take(BOOK_POOL / 10) }
            .map { it.id }
            .shuffled(random)
            .take(BOOK_POOL)
            .ifEmpty { listOf(bookId) }
    }

    /**
     * Uncaught exceptions on any thread, and the main thread stalling: a heartbeat on it, checked from a plain thread
     * that names the operation running when the beat stopped.
     */
    private fun watch(
        @StructuredScope scope: CoroutineScope,
    ): Job {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, t ->
            exceptions.incrementAndGet()
            sc.note("UNCAUGHT on ${thread.name} during $currentOp: $t\n${t.stackTraceToString().lines().take(12).joinToString("\n")}")
            previous?.uncaughtException(thread, t)
        }
        val beat =
            scope.launch(Dispatchers.Main) {
                while (isActive) {
                    lastBeat = System.currentTimeMillis()
                    delay(100)
                }
            }
        val watchdog =
            thread(isDaemon = true, name = "e2e-stall-watchdog") {
                var stalled = false
                while (!Thread.currentThread().isInterrupted) {
                    runCatching { Thread.sleep(250) }.onFailure { return@thread }
                    // A late beat longer than any run (the Mac slept) is the clock jumping, not the app stalling
                    val late = if (watchdogPaused) 0L else System.currentTimeMillis() - lastBeat
                    if (late in (STALL_MS + 1)..MAX_STALL_MS && !stalled) {
                        stalled = true
                        stalls.incrementAndGet()
                        sc.note("STALL: main thread silent for ${late}ms during $currentOp")
                    } else if (late <= STALL_MS || late > MAX_STALL_MS) {
                        stalled = false
                    }
                }
            }
        return scope.launch {
            try {
                awaitCancellation()
            } finally {
                beat.cancel()
                watchdog.interrupt()
                Thread.setDefaultUncaughtExceptionHandler(previous)
            }
        }
    }

    private fun usedHeapMb(): Long {
        System.gc()
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
    }

    private fun anyWindow(): OpenWindow = dm.windows.value.random(random)

    // One operation taking tabs away per burst: their guards (enough tabs left) read counts a burst doesn't refresh
    // until it recomposes, so several of them together could empty the last window, and the app rightly quits
    private var removalTaken = false

    private fun takeRemoval(): Boolean = !removalTaken.also { removalTaken = true }

    private fun tabsOf(w: OpenWindow): List<String> = w.group()?.ids.orEmpty()

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun operation(op: Int): String {
        val w = anyWindow()
        val tabs = tabsOf(w)
        val kind = random.nextInt(OPERATION_COUNT)
        currentOp = "op $op (#$kind)"
        if (trace) println("E2E > $currentOp on ${w.id.take(8)} tabs=${tabs.size} windows=${dm.windows.value.size}")
        return when (kind) {
            0 -> {
                w.tabsViewModel.openTab(
                    TabsDestination.BookContent(bookId = if (random.nextBoolean()) bookId else -1, tabId = UUID.randomUUID().toString()),
                )
                "open-tab"
            }
            1 -> {
                if (dm.windows.value.sumOf { tabsOf(it).size } > 2 && tabs.size > 1 && takeRemoval()) {
                    w.tabsViewModel.onEvent(TabsEvents.OnClose(random.nextInt(tabs.size)))
                }
                "close-tab"
            }
            2 -> {
                if (tabs.isNotEmpty()) w.tabsViewModel.onEvent(TabsEvents.OnSelect(random.nextInt(tabs.size)))
                "select"
            }
            3 -> {
                // Drag a tab out into empty space (tear-off when the window has several tabs).
                if (tabs.size > 1 && dm.windows.value.size < 5) {
                    drag(w, tabs.random(random), Offset(screen(w).x + 300f + random.nextInt(600), screen(w).y + 300f + random.nextInt(400)))
                }
                "tear-off"
            }
            4 -> {
                // Drag a tab onto another window's strip (merge).
                val other =
                    dm.windows.value
                        .filter { it !== w && it.session === w.session }
                        .randomOrNull(random)
                if (other != null && tabs.isNotEmpty() && takeRemoval()) drag(w, tabs.random(random), stripPoint(other))
                "merge"
            }
            5 -> {
                // Reorder inside the strip through the workspace's drag session.
                if (tabs.size > 2) {
                    val start = stripPoint(w)
                    drag(w, tabs.random(random), Offset(start.x + (random.nextInt(400) - 200), start.y))
                }
                "reorder"
            }
            6 -> {
                if (tabs.size > 1 && dm.windows.value.size < 5) dm.detachTabToNewWindow(tabs.random(random), w.id)
                "detach"
            }
            7 -> {
                if (dm.windows.value.size > 1 && takeRemoval()) closeWindowKeepingPins(op, w)
                "close-window"
            }
            8 -> {
                val event =
                    listOf(
                        BookContentEvent.ToggleCommentaries,
                        BookContentEvent.ToggleTargum,
                        BookContentEvent.ToggleToc,
                        BookContentEvent.ToggleBookTree,
                        BookContentEvent.ToggleNotes,
                        BookContentEvent.ToggleSources,
                    ).random(random)
                w
                    .group()
                    ?.selectedId
                    ?.let(E2e::bookViewModel)
                    ?.onEvent(event)
                "toggle-pane"
            }
            9 -> {
                val lines = w.session.panesOf(w.groupId, navigation = false)
                val pane = listOf(ReaderPane.Targum, ReaderPane.Comments, ReaderPane.Sources).random(random)
                val id = pane.idIn(w.groupId)
                val entry = lines.satellite(id)
                if (entry?.isOpen == true) {
                    if (entry.isDocked) lines.undock(id) else lines.dock(id, pane.home.side)
                }
                "float-or-dock"
            }
            10 -> {
                if (!w.isSwitching.value) {
                    if (dm.desktops.value.size < 3) dm.createDesktop(w.id, "T$op") else dm.switchToNext(w.id)
                }
                "desktop"
            }
            11 -> {
                w.windowState.placement = WindowPlacement.Floating
                w.windowState.size = DpSize((900 + random.nextInt(600)).dp, (600 + random.nextInt(300)).dp)
                "resize"
            }
            12 -> {
                w.windowState.placement = WindowPlacement.Maximized
                "maximize"
            }
            13 -> {
                val target =
                    dm.desktops.value
                        .filter { it.id != w.session.desktopId }
                        .randomOrNull(random)
                if (target != null && tabs.size > 1 && takeRemoval()) dm.moveTabToDesktop(tabs.random(random), w.id, target.id)
                "move-to-desktop"
            }
            14 -> {
                w.clearSwitching()
                w.tabsViewModel.onEvent(TabsEvents.OnAdd)
                "new-tab"
            }
            in PIN_OPERATIONS -> pinOperation(op, kind, w)
            else -> contentOperation(kind, w)
        }
    }

    private val PIN_OPERATIONS = 26..32

    /** Pinning: toggles, the closes that must spare pinned tabs, Ctrl+W's double press, reopen, and moves at the boundary. */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun pinOperation(
        op: Int,
        kind: Int,
        w: OpenWindow,
    ): String {
        val tabs = tabsOf(w)
        val session = w.session
        return when (kind) {
            26 -> {
                if (tabs.isNotEmpty()) {
                    val i = random.nextInt(tabs.size)
                    w.tabsViewModel.setPinned(i, !session.isPinned(tabs[i]))
                }
                "pin-toggle"
            }
            27 -> {
                // The bulk closes never take a pinned tab.
                val pinned = tabs.filter(session::isPinned)
                if (tabs.isNotEmpty() && dm.windows.value.sumOf { tabsOf(it).size } > 2 && takeRemoval()) {
                    val i = random.nextInt(tabs.size)
                    w.tabsViewModel.onEvent(
                        listOf(
                            TabsEvents.CloseAll,
                            TabsEvents.CloseOthers(i),
                            TabsEvents.CloseLeft(i),
                            TabsEvents.CloseRight(i),
                        ).random(random),
                    )
                    val gone = pinned.filter { session.item(it) == null }
                    if (gone.isNotEmpty()) fail(op, "a bulk close took pinned tab(s) ${gone.map { it.take(6) }}")
                }
                "bulk-close"
            }
            28 -> {
                // One Ctrl+W on a pinned tab only warns — a hint still up from an earlier op makes it the second.
                val selected = w.group()?.selectedId
                val hinted = w.tabsViewModel.closePinnedHint.value
                if (selected != null && session.isPinned(selected) && !hinted && takeRemoval()) {
                    w.tabsViewModel.closeSelectedTab()
                    if (session.item(selected) == null) fail(op, "a single Ctrl+W closed pinned tab ${selected.take(6)}")
                }
                "ctrl-w-once"
            }
            29 -> {
                // Two, close together: the second closes it — or, on an unpinned tab, the next one: keep a third.
                if (tabs.size > 2 && takeRemoval()) {
                    w.tabsViewModel.closeSelectedTab()
                    delay(random.nextLong(0, 120))
                    w.tabsViewModel.closeSelectedTab()
                }
                "ctrl-w-twice"
            }
            30 -> {
                dm.reopenClosedTab(w.id)
                "reopen-closed"
            }
            31 -> {
                // Straight through the workspace, anywhere: the constraint has to hold on every path.
                if (tabs.size > 1) session.workspace.reorder(tabs.random(random), random.nextInt(-1, tabs.size + 1))
                "reorder-anywhere"
            }
            else -> {
                // A drag across the boundary: a pinned tab to the far end, another one to the start.
                val pinned = tabs.filter(session::isPinned)
                val tab = if (pinned.isNotEmpty() && random.nextBoolean()) pinned.random(random) else tabs.randomOrNull(random)
                val b = w.boundsOnScreen()
                if (tab != null && b != null && tabs.size > 1) {
                    val s = scale(w)
                    val x = if (session.isPinned(tab)) b.x + b.width - 60f else b.x + 10f
                    drag(w, tab, Offset(x * s, (b.y + STRIP_Y_DP) * s))
                }
                "drag-across-pins"
            }
        }
    }

    /** Closes [w]; its pinned tabs must survive in another window of its desktop when it has one. */
    private fun closeWindowKeepingPins(
        op: Int,
        w: OpenWindow,
    ) {
        val session = w.session
        val pinned = tabsOf(w).filter(session::isPinned)
        val heir = dm.windowsOf(session.desktopId).any { it !== w && it.group() != null }
        dm.closeWindow(w.id)
        if (!heir || pinned.isEmpty()) return
        val lost = pinned.filter { id -> session.workspace.groups.none { id in it.ids } || !session.isPinned(id) }
        if (lost.isNotEmpty()) fail(op, "closing a window lost pinned tab(s) ${lost.map { it.take(6) }}")
    }

    private fun fail(
        op: Int,
        message: String,
    ) {
        failures++
        sc.note("op $op: $message")
    }

    /** What happens inside a tab: books, reading, search, themes and the Home widgets. */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun contentOperation(
        kind: Int,
        w: OpenWindow,
    ): String {
        val book = w.group()?.selectedId?.let(E2e::bookViewModel)
        val home = E2e.homeWidgets
        return when (kind) {
            15 -> {
                book?.onEvent(BookContentEvent.OpenBookById(books.random(random)))
                "open-random-book"
            }
            16 -> {
                w.tabsViewModel.openTab(TabsDestination.BookContent(bookId = books.random(random), tabId = UUID.randomUUID().toString()))
                "random-book-tab"
            }
            17 -> {
                repeat(1 + random.nextInt(20)) {
                    book?.onEvent(
                        if (random.nextBoolean()) BookContentEvent.NavigateToNextLine else BookContentEvent.NavigateToPreviousLine,
                    )
                }
                "navigate-lines"
            }
            18 -> {
                book?.onEvent(BookContentEvent.ContentScrollToLineIndex(random.nextInt(2000)))
                "jump-to-line"
            }
            19 -> {
                val query = listOf("בראשית", "שבת", "אמר רבי", "תפילה", "משה", "ויאמר").random(random)
                w.tabsViewModel.openTab(TabsDestination.Search(searchQuery = query, tabId = UUID.randomUUID().toString()))
                "search"
            }
            20 -> {
                val state = sc.graph().mainAppState
                state.setTheme(listOf(IntUiThemes.Light, IntUiThemes.Dark).random(random))
                "theme"
            }
            21 -> {
                appSettings.setTextSize(12f + random.nextInt(30))
                "text-size"
            }
            22 -> {
                home?.let { it.editingWidgets = !it.editingWidgets }
                "widgets-edit"
            }
            23 -> {
                val widget = availableHomeWidgets.random(random)
                val placed = homeLayout.current()
                when {
                    placed.none { it.widget.id == widget.id } -> homeLayout.add(widget)
                    random.nextBoolean() -> home?.removeWidget(widget) ?: homeLayout.remove(widget)
                    // Moved and resized anywhere, the others making room, as a drag would
                    else -> {
                        val w = random.nextInt(widget.minSpan.w, minOf(widget.maxSpan.w, HOME_GRID_COLUMNS) + 1)
                        val h = random.nextInt(widget.minSpan.h, widget.maxSpan.h + 1)
                        homeLayout.place(widget, CellRect(random.nextInt(0, HOME_GRID_COLUMNS - w + 1), random.nextInt(0, 12), w, h))
                    }
                }
                "widgets-layout"
            }
            24 -> {
                if (random.nextInt(8) ==
                    0
                ) {
                    homeLayout.reset()
                } else {
                    // The areas dealt out again: each widget on another's, the others making room
                    val placed = homeLayout.current()
                    placed.zip(placed.shuffled(random)).forEach { (widget, other) -> homeLayout.place(widget.widget, other.cell) }
                }
                "widgets-reorder"
            }
            else -> {
                when (random.nextInt(3)) {
                    0 -> home?.selectDate(LocalDate.now().plusDays(random.nextLong(-400, 400)))
                    1 -> home?.targetTime = Date(System.currentTimeMillis() + random.nextLong(-86_400_000L, 86_400_000L))
                    else -> home?.undoRemove()
                }
                "widgets-date"
            }
        }
    }

    /**
     * Right after an operation. Given time to settle (the default), a broken invariant is a failure; fired back to
     * back, the app is caught mid-way (a desktop still switching, a window still awaiting its group), so it only counts
     * as transient, and [settledCheck] decides.
     */
    private fun check(
        op: Int,
        name: String,
    ) {
        val problems = problems()
        if (problems.isEmpty()) return
        if (settleMs >= SETTLE_MS) {
            failures++
            sc.note("op $op $name: ${problems.joinToString("; ")}")
        } else {
            transients++
        }
    }

    /** Lets the app settle, up to [SETTLE_LIMIT_MS]: what stays broken then is a real failure, not a race in flight. */
    private suspend fun settledCheck(op: Int) {
        var problems = problems()
        var waited = 0L
        while (problems.isNotEmpty() && waited < SETTLE_LIMIT_MS) {
            delay(SETTLE_POLL_MS)
            waited += SETTLE_POLL_MS
            problems = problems()
        }
        if (problems.isNotEmpty()) {
            failures++
            sc.note("STUCK after op $op (settled ${waited}ms, last ops ${recent.joinToString()}): ${problems.joinToString("; ")}")
            sc.note("  state: ${describe()}")
        }
    }

    /** Every desktop's groups (their tabs) and windows (the group each shows), for a STUCK report. */
    private fun describe(): String =
        dm.sessions.value.joinToString(" | ") { session ->
            val groups = session.workspace.groups.joinToString { g -> "${g.id}=${g.ids.map { it.take(6) }}" }
            val windows = dm.windowsOf(session.desktopId).joinToString { w -> "${w.id.take(8)}->${w.groupId}" }
            "${session.desktopId}: groups[$groups] windows[$windows] awaiting=${
                dm.windowsOf(session.desktopId).filter { session.isAwaiting(it.groupId) }.map { it.id.take(8) }
            }"
        }

    private fun problems(): List<String> {
        val problems = mutableListOf<String>()
        for (session in dm.sessions.value) {
            val placed = session.workspace.groups.flatMap { it.ids }
            if (placed.size != placed.toSet().size) problems += "duplicated tab in ${session.desktopId}"
            val declared = session.tabIds().toSet()
            val lost = declared - placed.toSet()
            if (lost.isNotEmpty() && lost.none { session.initialGroupOf(it) != null }) problems += "tab(s) in no window: ${lost.size}"
            val shown = dm.windowsOf(session.desktopId).map { it.groupId }.toSet()
            val orphan =
                session.workspace.groups
                    .map { it.id }
                    .filter { it !in shown }
            if (orphan.isNotEmpty()) problems += "group(s) without a window: $orphan"
            for (group in session.workspace.groups) {
                val flags = group.ids.map(session::isPinned)
                if (flags != flags.sortedDescending()) problems += "pinned tab(s) not first in ${group.id}: $flags"
            }
        }
        for (w in dm.windows.value) {
            if (w.group() == null && !w.session.isAwaiting(w.groupId)) problems += "window ${w.id.take(8)} shows no group"
            if (tabsOf(w).isEmpty()) {
                problems +=
                    "window ${w.id.take(8)} has no tab (awaiting=${w.session.isAwaiting(w.groupId)} " +
                    "switching=${w.isSwitching.value} declared=${w.session.tabIds().size} groups=${w.session.workspace.groups.size})"
            }
            val listed =
                w.tabsViewModel.state.value.tabs
                    .map { it.destination.tabId }
            val ids = tabsOf(w)
            if (listed != ids.filter { w.session.item(it) != null }) problems += "window ${w.id.take(8)} tab list out of sync"
        }
        if (dm.windows.value.isEmpty()) problems += "no window left"
        return problems
    }

    private fun scale(w: OpenWindow): Float =
        w.nucleusWindow
            ?.unsafe
            ?.taoWindow
            ?.scaleFactor
            ?.takeIf { it > 0f } ?: 1f

    private fun screen(w: OpenWindow): Offset {
        val b = w.boundsOnScreen() ?: return Offset.Zero
        return Offset(b.x * scale(w), b.y * scale(w))
    }

    private fun stripPoint(w: OpenWindow): Offset {
        val b = w.boundsOnScreen() ?: return Offset.Zero
        val s = scale(w)
        return Offset((b.x + b.width / 2f) * s, (b.y + STRIP_Y_DP) * s)
    }

    private suspend fun drag(
        from: OpenWindow,
        tabId: String,
        to: Offset,
    ) {
        val window = from.nucleusWindow?.unsafe?.taoWindow ?: return
        val start = stripPoint(from)
        val session = from.session.workspace.beginDrag(tabId, TabDragOrigin.Strip(window), start) ?: return
        val steps = 8
        for (i in 1..steps) {
            session.update(Offset(start.x + (to.x - start.x) * i / steps, start.y + (to.y - start.y) * i / steps))
            delay(16)
        }
        session.end(to)
    }

    private const val STRIP_Y_DP = 20f
}
