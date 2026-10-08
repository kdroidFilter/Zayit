@file:OptIn(ExperimentalNucleusApi::class)

package io.github.kdroidfilter.seforimapp.framework.desktop

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.tao.SatelliteWorkspace
import dev.nucleusframework.window.tao.TabGroupSnapshot
import dev.nucleusframework.window.tao.TabLayoutSnapshot
import dev.nucleusframework.window.tao.TabWindowGroup
import dev.nucleusframework.window.tao.TabWorkspace
import io.github.kdroidfilter.seforim.tabs.TabItem
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.SimpleTabViewModelOwner
import io.github.kdroidfilter.seforimapp.framework.session.SerializableTabTitle
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.WindowSnapshot
import java.util.UUID

/** A tab the user closed, with its reading state, kept so it can be reopened. */
class ClosedTab(
    val item: TabItem,
    val state: TabPersistedState?,
)

/** The measured docks of a window, in px: the outer one's width, the inner one's width and height. */
data class DockSizes(
    val outerWidthPx: Int,
    val innerWidthPx: Int,
    val innerHeightPx: Int,
    val density: Float,
)

/**
 * One OPEN virtual desktop: its tabs, spread by the [workspace] over however many windows the user
 * pulled them into (Chrome tab model — the workspace owns windows, order and selection).
 *
 * [tabs] is what the app declares against the workspace, one `Tab` each; per-tab ViewModels live in
 * [ownerOf] for as long as the tab exists, so moving a tab to another window keeps them hot. Each
 * group — one window of the app — also gets its own pane docks ([panesOf]).
 */
@Stable
class DesktopSession internal constructor(
    val desktopId: String,
) {
    val workspace = TabWorkspace(defaultWindowSize = DpSize(DEFAULT_WIDTH_DP.dp, DEFAULT_HEIGHT_DP.dp))

    /** Every tab of the desktop, in declaration order. */
    val tabs = mutableStateListOf<TabItem>()

    // Plain maps: a workspace is created on first lookup, which may happen while a snapshot flow
    // reads — a state write there would throw.
    private val navPaneWorkspaces = HashMap<String, SatelliteWorkspace>()
    private val linePaneWorkspaces = HashMap<String, SatelliteWorkspace>()
    private val owners = HashMap<String, SimpleTabViewModelOwner>()

    /** Tabs waiting for their first declaration: where they land, and the tab they replace. */
    private val pending = HashMap<String, Placement>()

    /** Recently closed tabs, the last one on top. */
    private val closedTabs = ArrayDeque<ClosedTab>()

    /** Tabs leaving the workspace without being closed by the user (moved, replaced): not reopenable. */
    private val discarded = HashSet<String>()

    private class Placement(
        val groupId: String,
        val index: Int,
        val replacing: String?,
        // The tab to keep selected instead of the new one; null selects the new one.
        val keepSelected: String?,
    )

    fun item(tabId: String): TabItem? = tabs.firstOrNull { it.destination.tabId == tabId }

    fun group(groupId: String): TabWindowGroup? = workspace.group(groupId)

    /** The group that should receive a tab opened without a window in mind. */
    fun activeGroupId(): String? = workspace.activeGroup?.id

    /**
     * The pane docks of window [groupId]. [navigation]: the outer dock (book tree, contents, notes)
     * running the window's full height beside the text; otherwise the inner one (links,
     * commentaries, sources) around the text itself, above its breadcrumb.
     */
    fun panesOf(
        groupId: String,
        navigation: Boolean,
    ): SatelliteWorkspace = (if (navigation) navPaneWorkspaces else linePaneWorkspaces).getOrPut(groupId) { SatelliteWorkspace() }

    fun ownerOf(tabId: String): SimpleTabViewModelOwner = owners.getOrPut(tabId) { SimpleTabViewModelOwner(tabId) }

    /**
     * Adds a tab to [groupId] (a new window when null and nothing is open) at [index], selected.
     * [replacing] is closed once the new tab is in, so a window never goes empty in between.
     */
    fun addTab(
        destination: TabsDestination,
        groupId: String?,
        index: Int = 0,
        replacing: String? = null,
        title: String = titleFor(destination),
        tabType: TabType = tabTypeFor(destination),
        select: Boolean = true,
        pinned: Boolean = false,
        shortTitle: String = "",
    ) {
        val target = groupId ?: activeGroupId() ?: newGroupId()
        pending[destination.tabId] = Placement(target, index, replacing, if (select) null else group(target)?.selectedId)
        tabs +=
            TabItem(
                id = nextItemId++,
                title = title,
                destination = destination,
                tabType = tabType,
                pinned = pinned,
                shortTitle = shortTitle,
            )
    }

    /** Group a tab joins on its first declaration; null once it is placed. */
    fun initialGroupOf(tabId: String): String? = pending[tabId]?.groupId

    /** Called once the tab is registered with the workspace: applies its pending placement. */
    fun onDeclared(tabId: String) {
        val placement = pending.remove(tabId) ?: return
        workspace.reorder(tabId, placement.index)
        workspace.select(placement.keepSelected ?: tabId)
        placement.replacing?.let(::discard)
    }

    /** Replaces the destination of [tabId] in place (same id, same window, same slot). */
    fun updateDestination(
        tabId: String,
        destination: TabsDestination,
    ) {
        val index = tabs.indexOfFirst { it.destination.tabId == tabId }
        if (index < 0) return
        tabs[index] =
            tabs[index].copy(destination = destination, title = titleFor(destination), tabType = tabTypeFor(destination), shortTitle = "")
    }

    fun updateTitle(
        tabId: String,
        title: String,
        tabType: TabType,
        shortTitle: String = "",
    ): Boolean {
        val index = tabs.indexOfFirst { it.destination.tabId == tabId }
        if (index < 0) return false
        val current = tabs[index]
        if (current.title != title || current.tabType != tabType || current.shortTitle != shortTitle) {
            tabs[index] = current.copy(title = title, tabType = tabType, shortTitle = shortTitle)
        }
        return true
    }

    /**
     * Pins or unpins [tabId]: a pinned tab joins the end of the pinned run at the start of its
     * strip, an unpinned one the start of the others (Chromium's SetTabPinned).
     */
    fun setPinned(
        tabId: String,
        pinned: Boolean,
    ) {
        val index = tabs.indexOfFirst { it.destination.tabId == tabId }
        if (index < 0 || tabs[index].pinned == pinned) return
        tabs[index] = tabs[index].copy(pinned = pinned)
        // Clamped by pinConstrained to the boundary of the two runs.
        workspace.reorder(tabId, if (pinned) Int.MAX_VALUE else 0)
    }

    fun isPinned(tabId: String): Boolean = item(tabId)?.pinned == true

    /**
     * Where a tab may stand in [group] of this workspace: pinned tabs ahead of the others
     * (Chromium's ConstrainMoveIndex). [pinned] is the tab's own state, which a tab dragged in
     * from another desktop does not have here.
     */
    fun pinConstrained(
        tabId: String,
        pinned: Boolean,
        group: TabWindowGroup,
        index: Int,
    ): Int {
        val pinnedCount = group.ids.count { it != tabId && isPinned(it) }
        return if (pinned) index.coerceAtMost(pinnedCount) else index.coerceAtLeast(pinnedCount)
    }

    /** Closes [tabId] without making it reopenable (the tab lives on elsewhere, or was replaced). */
    fun discard(tabId: String) {
        // Only a tab still in: one already closed would stay marked and never be reopenable again.
        if (workspace.tab(tabId) == null) return
        discarded += tabId
        workspace.close(tabId)
    }

    /**
     * Drops a tab the workspace closed, with its ViewModels. Unless [discard]ed, it is remembered
     * with [state] for [popClosedTab]; a Home tab (no title yet) is not worth reopening.
     */
    fun forget(
        tabId: String,
        state: TabPersistedState?,
    ) {
        val item = item(tabId)
        if (!discarded.remove(tabId) && item != null && item.title.isNotEmpty()) {
            closedTabs.addLast(ClosedTab(item, state))
            if (closedTabs.size > MAX_CLOSED_TABS) closedTabs.removeFirst()
        }
        tabs.removeAll { it.destination.tabId == tabId }
        pending.remove(tabId)
        owners.remove(tabId)?.clear()
    }

    internal fun forgetWindow(groupId: String) {
        restoredByGroup.remove(groupId)
        navPaneWorkspaces.remove(groupId)
        linePaneWorkspaces.remove(groupId)
    }

    /** The last tab the user closed, taken off the stack; null when there is none. */
    fun popClosedTab(): ClosedTab? = closedTabs.removeLastOrNull()

    /** Closes every tab (the windows follow); used when the desktop goes dormant. */
    internal fun closeAll() {
        workspace.tabs.map { it.id }.forEach(::discard)
    }

    internal fun dispose() {
        owners.values.forEach { it.clear() }
        owners.clear()
    }

    // ---- Persistence ----

    /**
     * What [restore] laid out for each group, until the workspace has placed it: restored tabs only
     * reach their group once `Tab` declares them, and a save before that must not lose them.
     */
    private val restoredByGroup = HashMap<String, WindowSnapshot>()

    /**
     * Lays [snapshots] out as groups of the workspace — one per window, in order — and returns
     * their ids (fresh UUIDs: the workspace's own tear-offs are named "group-N"). Geometry stays
     * with the app's windows.
     */
    fun restore(snapshots: List<WindowSnapshot>): List<String> {
        val groups =
            snapshots.map { saved ->
                // Pinned tabs first, whatever the file says: one moved into a dormant desktop was appended.
                val selectedId = saved.destinations.getOrNull(saved.selectedIndex)?.tabId
                val destinations = saved.destinations.sortedByDescending { saved.titles[it.tabId]?.pinned == true }
                val selectedIndex = destinations.indexOfFirst { it.tabId == selectedId }.coerceAtLeast(0)
                val snapshot = saved.copy(destinations = destinations, selectedIndex = selectedIndex)
                val groupId = newGroupId()
                snapshot.destinations.forEach { destination ->
                    val savedTitle = snapshot.titles[destination.tabId]
                    tabs +=
                        TabItem(
                            id = nextItemId++,
                            title = savedTitle?.title ?: titleFor(destination),
                            destination = destination,
                            tabType = savedTitle?.tabType ?: tabTypeFor(destination),
                            pinned = savedTitle?.pinned == true,
                            shortTitle = savedTitle?.shortTitle.orEmpty(),
                        )
                }
                restoredByGroup[groupId] = snapshot
                TabGroupSnapshot(
                    id = groupId,
                    tabIds = snapshot.destinations.map { it.tabId },
                    selectedId = snapshot.destinations.getOrNull(snapshot.selectedIndex)?.tabId,
                    position = null,
                    size = workspace.defaultWindowSize,
                )
            }
        if (groups.isNotEmpty()) workspace.restore(TabLayoutSnapshot(groups))
        return groups.map { it.id }
    }

    /** [groupId] now exists in the workspace: it waits for nothing any more. */
    internal fun onGroupPlaced(groupId: String) {
        restoredByGroup.remove(groupId)
    }

    /** True while [groupId] was restored and is waiting for its tabs to be declared. */
    fun isAwaiting(groupId: String): Boolean = workspace.group(groupId) == null && groupId in restoredByGroup

    /** The tabs of window [groupId] for the session file: its group, or what was restored into it. */
    fun windowSnapshot(groupId: String): WindowSnapshot? {
        val group = workspace.group(groupId)
        if (group == null) {
            return restoredByGroup[groupId]?.takeIf { w -> w.destinations.any { item(it.tabId) != null } }
        }
        restoredByGroup.remove(groupId)
        val items = group.ids.mapNotNull(::item)
        return WindowSnapshot(
            destinations = items.map { stripEphemeral(it.destination) },
            selectedIndex = items.indexOfFirst { it.destination.tabId == group.selectedId }.coerceAtLeast(0),
            titles = items.associate { it.destination.tabId to SerializableTabTitle(it.title, it.tabType, it.pinned, it.shortTitle) },
        )
    }

    /** Ids of every tab, placed or still pending. */
    fun tabIds(): List<String> = tabs.map { it.destination.tabId }

    private var nextItemId = 1

    companion object {
        private const val DEFAULT_WIDTH_DP = 1280
        private const val DEFAULT_HEIGHT_DP = 800
        private const val MAX_CLOSED_TABS = 25

        // The workspace names tear-off groups "group-N" from a counter that restarts with the
        // process; restored and app-created groups use UUIDs so the two can never collide.
        fun newGroupId(): String = "w-" + UUID.randomUUID().toString()

        fun titleFor(destination: TabsDestination): String =
            when (destination) {
                is TabsDestination.Search -> destination.searchQuery
                is TabsDestination.BookContent -> if (destination.bookId > 0) "${destination.bookId}" else ""
                else -> ""
            }

        fun tabTypeFor(destination: TabsDestination): TabType =
            when (destination) {
                is TabsDestination.Home, is TabsDestination.Search -> TabType.SEARCH
                is TabsDestination.BookContent -> if (destination.bookId > 0) TabType.BOOK else TabType.SEARCH
                is TabsDestination.History -> TabType.HISTORY
                is TabsDestination.Favorites -> TabType.FAVORITES
                is TabsDestination.Notes -> TabType.NOTES
                is TabsDestination.Siddur -> TabType.SIDDUR
            }

        fun stripEphemeral(destination: TabsDestination): TabsDestination =
            when (destination) {
                is TabsDestination.BookContent -> destination.copy(lineId = null, openNotes = false)
                else -> destination
            }
    }
}
