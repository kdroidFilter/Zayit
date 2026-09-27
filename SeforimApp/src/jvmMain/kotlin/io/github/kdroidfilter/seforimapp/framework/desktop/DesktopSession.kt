@file:OptIn(ExperimentalNucleusApi::class)

package io.github.kdroidfilter.seforimapp.framework.desktop

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.unit.DpOffset
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
import io.github.kdroidfilter.seforimapp.framework.session.SavedGeometry
import io.github.kdroidfilter.seforimapp.framework.session.SerializableTabTitle
import io.github.kdroidfilter.seforimapp.framework.session.WindowSnapshot
import java.util.UUID

/** The measured docks of a window: the outer one's width, the inner one's width and height. */
data class DockSizes(
    val outerWidth: Float,
    val innerWidth: Float,
    val innerHeight: Float,
)

/**
 * One OPEN virtual desktop: its tabs, spread by the [workspace] over however many windows the user
 * pulled them into (Chrome tab model — the workspace owns windows, order and selection).
 *
 * [tabs] is what the app declares against the workspace, one `Tab` each; per-tab ViewModels live in
 * [ownerOf] for as long as the tab exists, so moving a tab to another window keeps them hot. Each
 * window also gets its own pane dock ([panesOf]).
 */
@Stable
class DesktopSession internal constructor(
    val desktopId: String,
) {
    val workspace = TabWorkspace(defaultWindowSize = DpSize(DEFAULT_WIDTH_DP.dp, DEFAULT_HEIGHT_DP.dp))

    /** Every tab of the desktop, in declaration order. */
    val tabs = mutableStateListOf<TabItem>()

    /** Live windows of this desktop, keyed by workspace group id. */
    internal val windows = mutableStateMapOf<String, OpenWindow>()

    private val navPaneWorkspaces = mutableStateMapOf<String, SatelliteWorkspace>()
    private val linePaneWorkspaces = mutableStateMapOf<String, SatelliteWorkspace>()
    private val owners = HashMap<String, SimpleTabViewModelOwner>()

    /** Tabs waiting for their first declaration: where they land, and the tab they replace. */
    private val pending = HashMap<String, Placement>()

    /** Measured size of each window's docks (outer width, inner width and height), in dp. */
    val dockSizes = mutableStateMapOf<String, DockSizes>()

    /** Groups restored from a maximized window; maximized once when their window opens. */
    internal val maximizeOnOpen = HashSet<String>()

    private class Placement(
        val groupId: String,
        val index: Int,
        val replacing: String?,
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
    ) {
        val target = groupId ?: activeGroupId() ?: newGroupId()
        pending[destination.tabId] = Placement(target, index, replacing)
        tabs += TabItem(id = nextItemId++, title = title, destination = destination, tabType = tabType)
    }

    /** Group a tab joins on its first declaration; null once it is placed. */
    fun initialGroupOf(tabId: String): String? = pending[tabId]?.groupId

    /** Called once the tab is registered with the workspace: applies its pending placement. */
    fun onDeclared(tabId: String) {
        val placement = pending.remove(tabId) ?: return
        workspace.reorder(tabId, placement.index)
        workspace.select(tabId)
        placement.replacing?.let(workspace::close)
    }

    /** Replaces the destination of [tabId] in place (same id, same window, same slot). */
    fun updateDestination(
        tabId: String,
        destination: TabsDestination,
    ) {
        val index = tabs.indexOfFirst { it.destination.tabId == tabId }
        if (index < 0) return
        tabs[index] = tabs[index].copy(destination = destination, title = titleFor(destination), tabType = tabTypeFor(destination))
    }

    fun updateTitle(
        tabId: String,
        title: String,
        tabType: TabType,
    ): Boolean {
        val index = tabs.indexOfFirst { it.destination.tabId == tabId }
        if (index < 0) return false
        val current = tabs[index]
        if (current.title != title || current.tabType != tabType) tabs[index] = current.copy(title = title, tabType = tabType)
        return true
    }

    /** Drops a tab the workspace closed, with its ViewModels. */
    fun forget(tabId: String) {
        tabs.removeAll { it.destination.tabId == tabId }
        pending.remove(tabId)
        owners.remove(tabId)?.clear()
    }

    internal fun forgetWindow(groupId: String) {
        dockSizes.remove(groupId)
        navPaneWorkspaces.remove(groupId)
        linePaneWorkspaces.remove(groupId)
        maximizeOnOpen.remove(groupId)
    }

    /** Closes every tab (the windows follow); used when the desktop goes dormant. */
    internal fun closeAll() {
        workspace.tabs.map { it.id }.forEach(workspace::close)
    }

    internal fun dispose() {
        owners.values.forEach { it.clear() }
        owners.clear()
        windows.values.forEach { it.dispose() }
        windows.clear()
    }

    // ---- Persistence ----

    /** Lays [snapshots] out as windows; each gets a fresh group id (never "group-N", see [newGroupId]). */
    fun restore(snapshots: List<WindowSnapshot>) {
        restoredWindows = snapshots.filter { it.destinations.isNotEmpty() }
        val groups =
            snapshots.filter { it.destinations.isNotEmpty() }.map { snapshot ->
                val groupId = newGroupId()
                snapshot.destinations.forEach { destination ->
                    val saved = snapshot.titles[destination.tabId]
                    tabs +=
                        TabItem(
                            id = nextItemId++,
                            title = saved?.title ?: titleFor(destination),
                            destination = destination,
                            tabType = saved?.tabType ?: tabTypeFor(destination),
                        )
                }
                val geometry = snapshot.geometry
                if (geometry == null || geometry.placement == "Maximized") maximizeOnOpen += groupId
                TabGroupSnapshot(
                    id = groupId,
                    tabIds = snapshot.destinations.map { it.tabId },
                    selectedId = snapshot.destinations.getOrNull(snapshot.selectedIndex)?.tabId,
                    position = geometry?.visiblePosition(),
                    size =
                        geometry?.let { DpSize(it.width.coerceIn(400, 10_000).dp, it.height.coerceIn(300, 10_000).dp) }
                            ?: workspace.defaultWindowSize,
                )
            }
        if (groups.isNotEmpty()) workspace.restore(TabLayoutSnapshot(groups))
    }

    /**
     * What [restore] laid out, until the workspace has placed it: the restored tabs only reach
     * their groups once `Tab` declares them, and a save before that must not lose them.
     */
    private var restoredWindows: List<WindowSnapshot> = emptyList()

    fun snapshotWindows(): List<WindowSnapshot> {
        if (workspace.groups.isEmpty()) return restoredWindows.filter { w -> w.destinations.any { item(it.tabId) != null } }
        restoredWindows = emptyList()
        return workspace.snapshot().groups.map { group ->
            val items = group.tabIds.mapNotNull(::item)
            val maximized = windows[group.id]?.nucleusWindow?.isMaximized ?: (group.id in maximizeOnOpen)
            WindowSnapshot(
                destinations = items.map { stripEphemeral(it.destination) },
                selectedIndex = items.indexOfFirst { it.destination.tabId == group.selectedId }.coerceAtLeast(0),
                titles = items.associate { it.destination.tabId to SerializableTabTitle(it.title, it.tabType) },
                geometry =
                    SavedGeometry(
                        x =
                            group.position
                                ?.x
                                ?.value
                                ?.toInt() ?: SavedGeometry.UNSPECIFIED,
                        y =
                            group.position
                                ?.y
                                ?.value
                                ?.toInt() ?: SavedGeometry.UNSPECIFIED,
                        width =
                            group.size.width.value
                                .toInt(),
                        height =
                            group.size.height.value
                                .toInt(),
                        placement = if (maximized) "Maximized" else "Floating",
                    ),
            )
        }
    }

    /** Ids of every tab, placed or still pending. */
    fun tabIds(): List<String> = tabs.map { it.destination.tabId }

    private var nextItemId = 1

    companion object {
        private const val DEFAULT_WIDTH_DP = 1280
        private const val DEFAULT_HEIGHT_DP = 800

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
            }

        fun stripEphemeral(destination: TabsDestination): TabsDestination =
            when (destination) {
                is TabsDestination.BookContent -> destination.copy(lineId = null)
                else -> destination
            }

        private fun SavedGeometry.visiblePosition(): DpOffset? =
            if (x == SavedGeometry.UNSPECIFIED || !isVisibleOnAnyScreen(x, y, width, height)) null else DpOffset(x.dp, y.dp)
    }
}
