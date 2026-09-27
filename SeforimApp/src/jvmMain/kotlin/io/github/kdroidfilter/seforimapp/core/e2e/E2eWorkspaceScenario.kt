@file:OptIn(ExperimentalNucleusApi::class)

package io.github.kdroidfilter.seforimapp.core.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.tao.TabDragOrigin
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.ReaderPane
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * What only the tab workspace does, after [E2eScenario]'s common part: tab drags between windows
 * (through the workspace's drag sessions, at real screen positions), tear-off and merge, a pane
 * floated into its own window and docked back, and every way a window closes.
 */
object E2eWorkspaceScenario {
    private lateinit var dm: DesktopManager

    suspend fun run(sc: E2eScenario) {
        if (E2e.scenario != "workspace") return
        dm = sc.graphDesktopManager()
        // Desktop "C" (a new window) from the common part: close it, back to one window.
        dm.windows.value
            .drop(1)
            .forEach { dm.closeWindow(it.id) }
        sc.step("20-one-window")
        dm.windows.value
            .single()
            .windowState.placement = androidx.compose.ui.window.WindowPlacement.Floating
        dm.windows.value
            .single()
            .windowState.size =
            androidx.compose.ui.unit
                .DpSize(1100.dp, 760.dp)
        sc.step("20b-resized")
        check(dm.windows.value.size == 1) { "expected one window, got ${dm.windows.value.size}" }

        val main = dm.windows.value.single()
        repeat(2) { main.tabsViewModel.openTab(TabsDestination.BookContent(bookId = -1, tabId = UUID.randomUUID().toString())) }
        sc.step("21-four-tabs")

        // Drag a tab out of the strip into empty space: a new window where it is released.
        val torn =
            main.tabsViewModel.state.value.tabs[1]
                .destination.tabId
        dragTab(main, torn, to = Offset(screenX(main) + 900f, screenY(main) + 500f))
        sc.step("22-torn-off")
        check(dm.windows.value.size == 2) { "tear-off: ${dm.windows.value.size} windows" }
        val second = dm.windows.value.first { it !== main }
        check(
            second.tabsViewModel.state.value.tabs
                .map { it.destination.tabId } == listOf(torn),
        ) { "tear-off: wrong tabs" }

        // Drag it back onto the first window's strip: merged, and the emptied window closes.
        dragTab(second, torn, to = stripPoint(main))
        sc.step("23-merged-back")
        check(dm.windows.value.size == 1) { "merge: ${dm.windows.value.size} windows" }
        check(
            main.tabsViewModel.state.value.tabs
                .any { it.destination.tabId == torn },
        ) { "merge: tab lost" }

        // Context menu "open in new window", then close that window: its tab is discarded.
        dm.detachTabToNewWindow(torn, main.id)
        sc.step("24-detached")
        check(dm.windows.value.size == 2) { "detach: ${dm.windows.value.size} windows" }
        dm.closeWindow(
            dm.windows.value
                .first { it !== main }
                .id,
        )
        sc.step("25-detached-closed")
        check(dm.windows.value.size == 1 && !dm.isTabOpen(torn)) { "close window: tab kept" }

        // A pane floated into a window of its own, then docked back where it was.
        val book =
            main.tabsViewModel.state.value.tabs.indexOfFirst {
                (it.destination as? TabsDestination.BookContent)?.bookId?.let { id ->
                    id >
                        0
                } ==
                    true
            }
        main.tabsViewModel.onEvent(TabsEvents.OnSelect(book))
        sc.step("26-book-again")
        val lines = main.session.panesOf(main.groupId, navigation = false)
        val commentaries = ReaderPane.Comments.idIn(main.groupId)
        if (lines.satellite(commentaries)?.isOpen != true) sc.note("26: commentaries pane closed")
        lines.undock(commentaries)
        sc.step("27-commentaries-floating")
        check(lines.satellite(commentaries)?.isDocked == false) { "undock failed" }
        lines.dock(commentaries, ReaderPane.Comments.home.side)
        sc.step("28-commentaries-docked")
        check(lines.satellite(commentaries)?.isDocked == true) { "dock failed" }

        // The hover card's picture: the window as the selected tab showed it.
        delay(1500)
        val shown = main.group()?.selectedId ?: error("no selection")
        val picture =
            main.session.workspace
                .tab(shown)
                ?.thumbnail
        check(picture != null) { "no thumbnail for the selected tab" }
        E2e.save("30-thumbnail", picture)
        sc.note("thumbnail ${picture.width}x${picture.height}")

        // Move a background tab to a dormant desktop: gone here, there on switching to it.
        val others = dm.desktops.value.filter { it.id != main.session.desktopId && !dm.isDesktopOpen(it.id) }
        val dormant = others.firstOrNull() ?: error("no dormant desktop")
        val moving = main.group()!!.ids.first { it != shown }
        val movingBook = (main.session.item(moving)?.destination as? TabsDestination.BookContent)?.bookId
        check(dm.moveTabToDesktop(moving, main.id, dormant.id)) { "move to dormant refused" }
        sc.step("31-moved-to-dormant")
        check(moving !in main.group()!!.ids) { "moved tab still here" }
        val landed =
            dm
                .snapshotOpenDesktop(dormant.id)
                .effectiveWindows()
                .first()
                .destinations
                .last()
        check((landed as? TabsDestination.BookContent)?.bookId == movingBook) { "moved tab not in dormant snapshot" }
        val home = main.session.desktopId
        dm.switchTo(main.id, dormant.id)
        sc.step("32-dormant-opened", waitMs = 2500)
        check(main.group()!!.ids.any { it == landed.tabId }) { "moved tab not restored" }
        dm.switchTo(main.id, home)
        sc.step("33-back-home", waitMs = 2500)

        // Move a tab to a desktop open in another window: added there, its selection kept.
        repeat(2) { main.tabsViewModel.openTab(TabsDestination.BookContent(bookId = -1, tabId = UUID.randomUUID().toString())) }
        val target = dm.createDesktopInNewWindow("M")
        sc.step("34-target-open")
        val targetWindow = dm.windowsOf(target).single()
        val targetSelected = targetWindow.group()?.selectedId
        val moving2 = main.group()!!.ids.last()
        check(dm.moveTabToDesktop(moving2, main.id, target)) { "move to open refused" }
        sc.step("35-moved-to-open")
        check(moving2 !in main.group()!!.ids) { "moved tab still in source" }
        check(targetWindow.group()!!.ids.size == 2) { "target has ${targetWindow.group()!!.ids.size} tabs" }
        check(targetWindow.group()?.selectedId == targetSelected) { "target selection changed" }
        dm.closeWindow(targetWindow.id)
        sc.step("36-target-closed")

        // Closing the other tabs with ×: the window stays with its last tab.
        val keep = main.tabsViewModel.state.value.selectedTabIndex
        main.tabsViewModel.onEvent(TabsEvents.CloseOthers(keep))
        sc.step("29-close-others")
        check(main.tabsViewModel.state.value.tabs.size == 1) { "close others: ${main.tabsViewModel.state.value.tabs.size}" }
    }

    private fun E2eScenario.graphDesktopManager(): DesktopManager = graph().desktopManager

    private fun scale(w: OpenWindow): Float =
        w.nucleusWindow
            ?.unsafe
            ?.taoWindow
            ?.scaleFactor
            ?.takeIf { it > 0f } ?: 1f

    private fun screenX(w: OpenWindow): Float = (w.boundsOnScreen()?.x ?: 0f) * scale(w)

    private fun screenY(w: OpenWindow): Float = (w.boundsOnScreen()?.y ?: 0f) * scale(w)

    /** A point on [w]'s tab strip, in physical screen px: its middle, just under the frame top. */
    private fun stripPoint(w: OpenWindow): Offset {
        val bounds = w.boundsOnScreen() ?: error("window not on screen")
        val s = scale(w)
        return Offset((bounds.x + bounds.width / 2f) * s, (bounds.y + STRIP_Y_DP) * s)
    }

    /** Drags [tabId] from [from]'s strip to [to] (physical screen px) through the workspace's drag session. */
    private suspend fun dragTab(
        from: OpenWindow,
        tabId: String,
        to: Offset,
    ) {
        val window = from.nucleusWindow?.unsafe?.taoWindow ?: error("no native window")
        val start = stripPoint(from)
        val session = from.session.workspace.beginDrag(tabId, TabDragOrigin.Strip(window), start) ?: error("drag refused")
        val steps = 20
        for (i in 1..steps) {
            session.update(Offset(start.x + (to.x - start.x) * i / steps, start.y + (to.y - start.y) * i / steps))
            delay(16)
        }
        session.end(to)
    }

    private const val STRIP_Y_DP = 20f
}
