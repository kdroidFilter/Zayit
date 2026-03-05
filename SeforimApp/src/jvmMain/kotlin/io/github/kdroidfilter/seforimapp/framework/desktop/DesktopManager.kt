package io.github.kdroidfilter.seforimapp.framework.desktop

import io.github.kdroidfilter.seforim.desktop.VirtualDesktop
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.framework.session.DesktopTabsSnapshot
import io.github.kdroidfilter.seforimapp.framework.session.DesktopsState
import io.github.kdroidfilter.seforimapp.framework.session.SerializableTabTitle
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Manages virtual desktops by snapshotting/restoring the [TabsViewModel] state
 * when switching between desktops. Only one desktop is "active" at a time.
 */
class DesktopManager(
    private val tabsViewModel: TabsViewModel,
    private val tabPersistedStateStore: TabPersistedStateStore,
) {
    private val defaultDesktopId = UUID.randomUUID().toString()

    private val _desktops =
        MutableStateFlow(
            listOf(VirtualDesktop(id = defaultDesktopId, name = "1")),
        )
    val desktops: StateFlow<List<VirtualDesktop>> = _desktops.asStateFlow()

    private val _activeDesktopId = MutableStateFlow(defaultDesktopId)
    val activeDesktopId: StateFlow<String> = _activeDesktopId.asStateFlow()

    private val snapshots = mutableMapOf<String, DesktopTabsSnapshot>()

    fun switchTo(desktopId: String) {
        if (desktopId == _activeDesktopId.value) return
        val target = _desktops.value.find { it.id == desktopId } ?: return

        // Snapshot current desktop
        snapshots[_activeDesktopId.value] = snapshotCurrentDesktop()

        // Restore target desktop
        val targetSnapshot = snapshots[target.id]
        if (targetSnapshot != null) {
            restoreDesktop(targetSnapshot)
        } else {
            // Empty desktop: single Home tab
            val tabId = UUID.randomUUID().toString()
            tabPersistedStateStore.restore(emptyMap())
            tabsViewModel.restoreTabs(
                destinations = listOf(TabsDestination.BookContent(bookId = -1, tabId = tabId)),
                selectedIndex = 0,
            )
        }

        _activeDesktopId.value = desktopId
    }

    fun createDesktop(name: String): String {
        val id = UUID.randomUUID().toString()
        val desktop = VirtualDesktop(id = id, name = name)
        _desktops.update { it + desktop }

        // Snapshot current before switching
        snapshots[_activeDesktopId.value] = snapshotCurrentDesktop()

        // Switch to new empty desktop
        val tabId = UUID.randomUUID().toString()
        tabPersistedStateStore.restore(emptyMap())
        tabsViewModel.restoreTabs(
            destinations = listOf(TabsDestination.BookContent(bookId = -1, tabId = tabId)),
            selectedIndex = 0,
        )

        _activeDesktopId.value = id
        return id
    }

    fun renameDesktop(
        id: String,
        newName: String,
    ) {
        _desktops.update { desktops ->
            desktops.map { if (it.id == id) it.copy(name = newName) else it }
        }
    }

    fun deleteDesktop(id: String) {
        val current = _desktops.value
        if (current.size <= 1) return

        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return

        if (id == _activeDesktopId.value) {
            // Switch to neighbor first
            val neighborIndex = if (index > 0) index - 1 else index + 1
            switchTo(current[neighborIndex].id)
        }

        snapshots.remove(id)
        _desktops.update { desktops -> desktops.filter { it.id != id } }
    }

    fun switchToNext() {
        val current = _desktops.value
        if (current.size <= 1) return
        val index = current.indexOfFirst { it.id == _activeDesktopId.value }
        val nextIndex = (index + 1) % current.size
        switchTo(current[nextIndex].id)
    }

    fun switchToPrevious() {
        val current = _desktops.value
        if (current.size <= 1) return
        val index = current.indexOfFirst { it.id == _activeDesktopId.value }
        val prevIndex = (index - 1 + current.size) % current.size
        switchTo(current[prevIndex].id)
    }

    fun snapshotCurrentDesktop(): DesktopTabsSnapshot {
        val tabsState = tabsViewModel.state.value
        val tabs = tabsState.tabs

        // Strip ephemeral lineId from BookContent destinations
        val destinations =
            tabs.map { tabItem ->
                when (val dest = tabItem.destination) {
                    is TabsDestination.BookContent -> dest.copy(lineId = null)
                    else -> dest
                }
            }

        val storeSnapshot = tabPersistedStateStore.snapshot()
        val tabStates =
            destinations.associate { dest ->
                dest.tabId to (storeSnapshot[dest.tabId] ?: TabPersistedState())
            }

        val titles =
            tabs.associate { tabItem ->
                tabItem.destination.tabId to
                    SerializableTabTitle(
                        title = tabItem.title,
                        tabType = tabItem.tabType,
                    )
            }

        return DesktopTabsSnapshot(
            destinations = destinations,
            selectedIndex = tabsState.selectedTabIndex.coerceIn(0, destinations.lastIndex.coerceAtLeast(0)),
            titles = titles,
            tabStates = tabStates,
        )
    }

    private fun restoreDesktop(snapshot: DesktopTabsSnapshot) {
        if (snapshot.destinations.isEmpty()) return

        tabPersistedStateStore.restore(snapshot.tabStates)

        val titles =
            snapshot.titles.mapValues { (_, v) ->
                v.title to v.tabType
            }

        tabsViewModel.restoreTabs(
            destinations = snapshot.destinations,
            selectedIndex = snapshot.selectedIndex,
            titles = titles,
        )
    }

    /** Build the full [DesktopsState] for persistence (snapshots current desktop first). */
    fun buildDesktopsState(): DesktopsState {
        val allSnapshots = snapshots.toMutableMap()
        allSnapshots[_activeDesktopId.value] = snapshotCurrentDesktop()

        return DesktopsState(
            desktops = _desktops.value,
            activeDesktopId = _activeDesktopId.value,
            snapshots = allSnapshots,
        )
    }

    /** Restore full state from a persisted [DesktopsState]. Only restores the active desktop into TabsViewModel. */
    fun restoreFromDesktopsState(state: DesktopsState) {
        if (state.desktops.isEmpty()) return

        _desktops.value = state.desktops
        _activeDesktopId.value = state.activeDesktopId

        // Store all non-active snapshots
        snapshots.clear()
        state.snapshots.forEach { (id, snapshot) ->
            if (id != state.activeDesktopId) {
                snapshots[id] = snapshot
            }
        }

        // Restore active desktop into TabsViewModel
        val activeSnapshot = state.snapshots[state.activeDesktopId]
        if (activeSnapshot != null) {
            restoreDesktop(activeSnapshot)
        }
    }
}
