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
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.ReaderPane
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.random.Random

/**
 * Random walk over everything a user can do to tabs, windows, desktops and panes (seeded, so a
 * failure replays), checking after every operation that no tab is lost or duplicated, that every
 * window shows a live group and that each window's tab list is its group's.
 */
object E2eTortureScenario {
    private const val OPS = 400
    private const val SETTLE_MS = 350L
    private const val CAPTURE_EVERY = 50

    private lateinit var dm: DesktopManager
    private lateinit var sc: E2eScenario
    private val random = Random(System.getenv("ZAYIT_E2E_SEED")?.toIntOrNull() ?: 1)
    private val ops = System.getenv("ZAYIT_E2E_OPS")?.toIntOrNull() ?: OPS
    private var bookId = -1L
    private var failures = 0

    suspend fun run(scenario: E2eScenario) {
        if (E2e.scenario != "torture") return
        sc = scenario
        dm = scenario.graph().desktopManager
        bookId = scenario
            .graph()
            .repository
            .getBookByTitle("בראשית")
            ?.id ?: error("book not found")
        dm.windows.value
            .drop(1)
            .forEach { dm.closeWindow(it.id) }
        delay(1000)
        for (op in 1..ops) {
            val name = operation(op)
            delay(SETTLE_MS)
            check(op, name)
            if (op % CAPTURE_EVERY == 0) sc.step("t%03d".format(op), waitMs = 1500)
        }
        sc.note("TORTURE done: $ops operations, $failures invariant failure(s)")
    }

    private fun anyWindow(): OpenWindow = dm.windows.value.random(random)

    private fun tabsOf(w: OpenWindow): List<String> = w.group()?.ids.orEmpty()

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun operation(op: Int): String {
        val w = anyWindow()
        val tabs = tabsOf(w)
        return when (random.nextInt(14)) {
            0 -> {
                w.tabsViewModel.openTab(
                    TabsDestination.BookContent(bookId = if (random.nextBoolean()) bookId else -1, tabId = UUID.randomUUID().toString()),
                )
                "open-tab"
            }
            1 -> {
                if (dm.windows.value.sumOf { tabsOf(it).size } > 2 && tabs.size > 1) {
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
                if (other != null && tabs.isNotEmpty()) drag(w, tabs.random(random), stripPoint(other))
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
                if (dm.windows.value.size > 1) dm.closeWindow(w.id)
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
            else -> {
                w.clearSwitching()
                w.tabsViewModel.onEvent(TabsEvents.OnAdd)
                "new-tab"
            }
        }
    }

    private fun check(
        op: Int,
        name: String,
    ) {
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
        if (problems.isNotEmpty()) {
            failures++
            sc.note("op $op $name: ${problems.joinToString("; ")}")
        }
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
