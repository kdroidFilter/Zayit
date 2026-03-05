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
            listOf(VirtualDesktop(id = defaultDesktopId, name = "\u05E9\u05F4\u05E2 1")),
        )
    val desktops: StateFlow<List<VirtualDesktop>> = _desktops.asStateFlow()

    private val _activeDesktopId = MutableStateFlow(defaultDesktopId)
    val activeDesktopId: StateFlow<String> = _activeDesktopId.asStateFlow()

    private val snapshots = mutableMapOf<String, DesktopTabsSnapshot>()

    private val _isSwitching = MutableStateFlow(false)
    val isSwitching: StateFlow<Boolean> = _isSwitching.asStateFlow()

    fun switchTo(desktopId: String) {
        if (_isSwitching.value) return
        if (desktopId == _activeDesktopId.value) return
        val target = _desktops.value.find { it.id == desktopId } ?: return

        _isSwitching.value = true
        try {
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
                    skipAnimation = true,
                )
            }

            _activeDesktopId.value = desktopId
        } finally {
            // Keep isSwitching true — cleared by UI after first frame renders
        }
    }

    fun clearSwitching() {
        _isSwitching.value = false
    }

    fun createDesktop(): String = createDesktop("\u05E9\u05F4\u05E2 ${_desktops.value.size + 1}")

    fun createDesktop(name: String): String {
        if (_isSwitching.value) return _activeDesktopId.value

        _isSwitching.value = true
        try {
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
                skipAnimation = true,
            )

            _activeDesktopId.value = id
            return id
        } finally {
            // Keep isSwitching true — cleared by UI after first frame renders
        }
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

    fun moveDesktop(
        fromIndex: Int,
        toIndex: Int,
    ) {
        _desktops.update { current ->
            if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return@update current
            val list = current.toMutableList()
            val moved = list.removeAt(fromIndex)
            list.add(toIndex, moved)
            list
        }
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
            skipAnimation = true,
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
