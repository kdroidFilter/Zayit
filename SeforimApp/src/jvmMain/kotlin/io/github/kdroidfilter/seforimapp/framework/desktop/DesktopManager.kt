package io.github.kdroidfilter.seforimapp.framework.desktop

import io.github.kdroidfilter.seforim.desktop.VirtualDesktop
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import io.github.kdroidfilter.seforimapp.framework.session.DesktopTabsSnapshot
import io.github.kdroidfilter.seforimapp.framework.session.DesktopsState
import io.github.kdroidfilter.seforimapp.framework.session.SavedGeometry
import io.github.kdroidfilter.seforimapp.framework.session.SerializableTabTitle
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimapp.framework.session.WindowSnapshot
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Manages virtual desktops and the OS windows that display them.
 *
 * Model: a desktop is a user-curated set of tabs laid out in 1..n windows. A desktop is either
 * OPEN (all its windows live, each with its own [TabsViewModel]) or DORMANT (a serializable
 * [DesktopTabsSnapshot]). Several desktops can be open at once, each in its own window(s), but a
 * desktop is never open twice. Windows are ephemeral screen real estate; desktops are only ever
 * created/deleted explicitly by the user.
 *
 * Per-tab UI state lives in the app-wide [TabPersistedStateStore] (tabIds are UUIDs, so entries
 * from different windows/desktops never collide); opening/closing a desktop loads/unloads its
 * entries instead of wiping the store.
 */
class DesktopManager(
    private val tabPersistedStateStore: TabPersistedStateStore,
    private val titleUpdateManager: TabTitleUpdateManager,
    private val searchHomeViewModelFactory: () -> SearchHomeViewModel,
    defaultDesktopName: String,
    // Saved geometry of the focused window, peeked at boot so the first window is created with
    // the right placement instead of flashing maximized before the async session restore.
    initialWindowGeometry: SavedGeometry? = null,
) {
    private val defaultDesktopId = UUID.randomUUID().toString()

    private val _desktops =
        MutableStateFlow(
            persistentListOf(VirtualDesktop(id = defaultDesktopId, name = defaultDesktopName)),
        )
    val desktops: StateFlow<ImmutableList<VirtualDesktop>> = _desktops.asStateFlow()

    private val _windows =
        MutableStateFlow(
            persistentListOf(newWindow(defaultDesktopId, snapshot = WindowSnapshot(geometry = initialWindowGeometry))),
        )
    val windows: StateFlow<ImmutableList<OpenWindow>> = _windows.asStateFlow()

    private val _focusedWindowId = MutableStateFlow(_windows.value.first().id)
    val focusedWindowId: StateFlow<String> = _focusedWindowId.asStateFlow()

    /** Desktop of the focused window. Kept for consumers that need "the" current desktop. */
    private val _activeDesktopId = MutableStateFlow(defaultDesktopId)
    val activeDesktopId: StateFlow<String> = _activeDesktopId.asStateFlow()

    /** Snapshots of desktops that are not currently open in any window. */
    private val dormantSnapshots = mutableMapOf<String, DesktopTabsSnapshot>()

    // ---- Lookups ----

    fun window(windowId: String): OpenWindow? = _windows.value.find { it.id == windowId }

    fun focusedWindow(): OpenWindow? = window(_focusedWindowId.value) ?: _windows.value.firstOrNull()

    fun windowsOf(desktopId: String): List<OpenWindow> = _windows.value.filter { it.desktopId.value == desktopId }

    fun isDesktopOpen(desktopId: String): Boolean = _windows.value.any { it.desktopId.value == desktopId }

    /** Ordered list of desktops currently open in windows (window order, distinct). */
    fun openDesktopIds(): List<String> = _windows.value.map { it.desktopId.value }.distinct()

    fun isTabOpenInAnotherWindow(
        tabId: String,
        windowId: String,
    ): Boolean =
        _windows.value.any { w ->
            w.id != windowId &&
                w.tabsViewModel.state.value.tabs
                    .any { it.destination.tabId == tabId }
        }

    /** True while [tabId] is open in any window. */
    fun isTabOpen(tabId: String): Boolean =
        _windows.value.any { w ->
            w.tabsViewModel.state.value.tabs
                .any { it.destination.tabId == tabId }
        }

    /**
     * The [TabsViewModel] of the window currently hosting [tabId]. Per-tab ViewModels navigate
     * through this instead of a fixed window reference, so a tab dragged to another window keeps
     * opening its results in whatever window it lives in now.
     */
    fun tabsViewModelFor(tabId: String): TabsViewModel? =
        _windows.value
            .find { w ->
                w.tabsViewModel.state.value.tabs
                    .any { it.destination.tabId == tabId }
            }?.tabsViewModel

    /** Emits whether [tabId] is open in any window; used to cancel work when a tab closes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun tabExistsFlow(tabId: String): Flow<Boolean> =
        windows
            .flatMapLatest { ws ->
                if (ws.isEmpty()) {
                    flowOf(false)
                } else {
                    combine(ws.map { it.tabsViewModel.state }) { states ->
                        states.any { st -> st.tabs.any { it.destination.tabId == tabId } }
                    }
                }
            }.distinctUntilChanged()

    fun onWindowFocused(windowId: String) {
        if (window(windowId) == null) return
        _focusedWindowId.value = windowId
        refreshActiveDesktop()
    }

    // ---- Desktop switching ----

    /** Switches the focused window to [desktopId] (launcher/dock-menu entry point). */
    fun switchTo(desktopId: String) {
        focusedWindow()?.let { switchTo(it.id, desktopId) }
    }

    /**
     * Shows [desktopId] in the given window. If the desktop is already open in another window,
     * that window is focused instead (a desktop is never open twice). Otherwise the window's
     * current desktop goes dormant as a whole (all its windows) and the target is restored here.
     */
    fun switchTo(
        windowId: String,
        desktopId: String,
    ) {
        val win = window(windowId) ?: return
        if (win.isSwitching.value) return
        if (win.desktopId.value == desktopId) return
        if (_desktops.value.none { it.id == desktopId }) return

        windowsOf(desktopId).firstOrNull()?.let { other ->
            other.requestFocus()
            onWindowFocused(other.id)
            return
        }

        win.markSwitching()
        putDesktopDormant(win.desktopId.value, keepWindow = win)
        openDesktopInto(win, desktopId)
        refreshActiveDesktop()
    }

    fun switchToNext(windowId: String) = switchRelative(windowId, +1)

    fun switchToPrevious(windowId: String) = switchRelative(windowId, -1)

    private fun switchRelative(
        windowId: String,
        direction: Int,
    ) {
        val current = _desktops.value
        if (current.size <= 1) return
        val win = window(windowId) ?: return
        val index = current.indexOfFirst { it.id == win.desktopId.value }
        if (index < 0) return
        val target = current[(index + direction + current.size) % current.size]
        switchTo(windowId, target.id)
    }

    // ---- Windows ----

    /** Opens a dormant desktop in a new window (or focuses it if already open). */
    fun openInNewWindow(desktopId: String) {
        windowsOf(desktopId).firstOrNull()?.let { other ->
            other.requestFocus()
            onWindowFocused(other.id)
            return
        }
        if (_desktops.value.none { it.id == desktopId }) return

        val snapshot = dormantSnapshots.remove(desktopId)
        tabPersistedStateStore.putAll(snapshot?.tabStates.orEmpty())
        val windowSnapshots = snapshot?.effectiveWindows().orEmpty()
        val spawned =
            if (windowSnapshots.isEmpty()) {
                listOf(spawnWindow(desktopId, WindowSnapshot(geometry = cascadedFloatingGeometry(null))))
            } else {
                windowSnapshots.map {
                    spawnWindow(desktopId, it.copy(geometry = cascadedFloatingGeometry(it.geometry)))
                }
            }
        _focusedWindowId.value = spawned.first().id
        refreshActiveDesktop()
    }

    /**
     * Closes a window. If it was its desktop's last window, the desktop goes dormant (content
     * preserved); otherwise the window's tabs are discarded (Chrome-like). The last window of the
     * app is never closed here — that's the quit path, handled by the caller.
     */
    fun closeWindow(windowId: String) {
        val win = window(windowId) ?: return
        if (_windows.value.size <= 1) return
        val desktopId = win.desktopId.value
        if (windowsOf(desktopId).size == 1) {
            dormantSnapshots[desktopId] = snapshotOpenDesktop(desktopId)
        }
        val tabIds = windowTabIds(win)
        removeWindow(win)
        tabPersistedStateStore.removeAll(tabIds)
        refreshActiveDesktop()
    }

    // ---- Tab movement between windows (model layer for drag & drop) ----

    /** Moves a tab to another window (possibly another desktop); closes the source if emptied. */
    fun moveTabToWindow(
        tabId: String,
        fromWindowId: String,
        toWindowId: String,
        index: Int = Int.MAX_VALUE,
    ) {
        if (fromWindowId == toWindowId) return
        val from = window(fromWindowId) ?: return
        val to = window(toWindowId) ?: return
        val item = from.tabsViewModel.takeTab(tabId) ?: return
        to.tabsViewModel.insertTab(item.destination, item.title, item.tabType, index, select = true)
        to.requestFocus()
        _focusedWindowId.value = to.id
        if (from.tabsViewModel.state.value.tabs
                .isEmpty()
        ) {
            closeWindow(from.id)
        }
        refreshActiveDesktop()
    }

    /**
     * Detaches a tab into a new window of the SAME desktop (Chrome-style drag-out). No desktop is
     * ever created implicitly. Returns the new window, or null when the tab is the window's only
     * tab (dragging the whole window around covers that case).
     */
    fun detachTabToNewWindow(
        tabId: String,
        fromWindowId: String,
        screenX: Int? = null,
        screenY: Int? = null,
    ): OpenWindow? {
        val from = window(fromWindowId) ?: return null
        if (from.tabsViewModel.state.value.tabs.size <= 1) return null
        val item = from.tabsViewModel.takeTab(tabId) ?: return null
        val desktopId = from.desktopId.value
        // Chrome-like: the detached window floats noticeably smaller than the (often maximized)
        // source window — it must never inherit a maximized footprint. With pointer coordinates
        // (drag & drop) it lands under the cursor; without (context-menu action) it cascades
        // from the source window.
        val sourceGeometry = from.windowState.toSavedGeometry()
        val geometry =
            if (screenX != null && screenY != null) {
                SavedGeometry(
                    x = screenX,
                    y = screenY,
                    width = (sourceGeometry.width * 3 / 4).coerceIn(640, 1200),
                    height = (sourceGeometry.height * 3 / 4).coerceIn(480, 840),
                    placement = "Floating",
                )
            } else {
                cascadedFloatingGeometry(null)
            }
        val snapshot =
            WindowSnapshot(
                destinations = listOf(item.destination),
                selectedIndex = 0,
                titles = mapOf(tabId to SerializableTabTitle(item.title, item.tabType)),
                geometry = geometry,
            )
        val spawned = spawnWindow(desktopId, snapshot)
        _focusedWindowId.value = spawned.id
        refreshActiveDesktop()
        return spawned
    }

    // ---- Desktop CRUD ----

    /** Creates a desktop and switches the focused window to it (legacy single-window behavior). */
    fun createDesktop(name: String): String = createDesktop(focusedWindow()?.id.orEmpty(), name)

    fun createDesktop(
        windowId: String,
        name: String,
    ): String {
        val id = UUID.randomUUID().toString()
        _desktops.update { (it + VirtualDesktop(id = id, name = name)).toPersistentList() }
        val win = window(windowId) ?: return id
        if (win.isSwitching.value) return id
        win.markSwitching()
        putDesktopDormant(win.desktopId.value, keepWindow = win)
        freshHomeInto(win)
        win.setDesktop(id)
        refreshActiveDesktop()
        return id
    }

    /** Creates a desktop and opens it in a brand-new window, keeping the others as they are. */
    fun createDesktopInNewWindow(name: String): String {
        val id = UUID.randomUUID().toString()
        _desktops.update { (it + VirtualDesktop(id = id, name = name)).toPersistentList() }
        val spawned = spawnWindow(id, WindowSnapshot(geometry = cascadedFloatingGeometry(null)))
        _focusedWindowId.value = spawned.id
        refreshActiveDesktop()
        return id
    }

    fun renameDesktop(
        id: String,
        newName: String,
    ) {
        _desktops.update { desktops ->
            desktops.map { if (it.id == id) it.copy(name = newName) else it }.toPersistentList()
        }
    }

    fun deleteDesktop(id: String) {
        val current = _desktops.value
        if (current.size <= 1) return
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return

        val wins = windowsOf(id)
        if (wins.isNotEmpty() && wins.size == _windows.value.size) {
            // The desktop being deleted owns every window: keep one alive on a neighbor desktop.
            val neighbor = current[if (index > 0) index - 1 else index + 1]
            val keep = wins.first()
            wins.drop(1).forEach { w ->
                tabPersistedStateStore.removeAll(windowTabIds(w))
                removeWindow(w)
            }
            keep.markSwitching()
            tabPersistedStateStore.removeAll(windowTabIds(keep))
            openDesktopInto(keep, neighbor.id)
        } else {
            wins.forEach { w ->
                tabPersistedStateStore.removeAll(windowTabIds(w))
                removeWindow(w)
            }
        }

        dormantSnapshots.remove(id)
        _desktops.update { desktops -> desktops.filter { it.id != id }.toPersistentList() }
        refreshActiveDesktop()
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
            list.toPersistentList()
        }
    }

    // ---- Persistence ----

    /** Builds the full [DesktopsState] for disk persistence (open desktops snapshotted live). */
    fun buildDesktopsState(): DesktopsState {
        val open = openDesktopIds()
        val allSnapshots = dormantSnapshots.toMutableMap()
        open.forEach { allSnapshots[it] = snapshotOpenDesktop(it) }
        val focusedDesktop = focusedWindow()?.desktopId?.value ?: open.firstOrNull().orEmpty()
        return DesktopsState(
            desktops = _desktops.value,
            activeDesktopId = focusedDesktop,
            snapshots = allSnapshots,
            openDesktopIds = open,
            focusedDesktopId = focusedDesktop,
        )
    }

    /**
     * Restores the persisted state at boot: reopens every previously open desktop with its
     * window geometry (clamped to the current screens). The single boot window is reused as the
     * focused desktop's first window to avoid a close/reopen flash.
     */
    fun restoreFromDesktopsState(state: DesktopsState) {
        if (state.desktops.isEmpty()) return
        _desktops.value = state.desktops.toPersistentList()

        val openIds = state.effectiveOpenDesktopIds().ifEmpty { listOf(state.desktops.first().id) }
        dormantSnapshots.clear()
        state.snapshots.forEach { (id, snapshot) ->
            if (id !in openIds) dormantSnapshots[id] = snapshot
        }

        val primary = _windows.value.first()
        _windows.value.drop(1).forEach { removeWindow(it) }
        tabPersistedStateStore.clearAll()
        openIds.forEach { id -> state.snapshots[id]?.let { tabPersistedStateStore.putAll(it.tabStates) } }

        val primaryDesktopId = state.focusedDesktopId.takeIf { it in openIds } ?: openIds.first()
        val primarySnapshots = state.snapshots[primaryDesktopId]?.effectiveWindows().orEmpty()
        val primaryFirst = primarySnapshots.firstOrNull()
        if (primaryFirst != null && primaryFirst.destinations.isNotEmpty()) {
            restoreInto(primary, primaryFirst)
        } else {
            freshHomeInto(primary)
        }
        primaryFirst?.geometry?.let { applyGeometry(primary.windowState, it) }
        primary.setDesktop(primaryDesktopId)
        primarySnapshots.drop(1).forEach { spawnWindow(primaryDesktopId, it) }

        openIds.filter { it != primaryDesktopId }.forEach { id ->
            val windowSnapshots = state.snapshots[id]?.effectiveWindows().orEmpty()
            if (windowSnapshots.isEmpty()) {
                spawnWindow(id, null)
            } else {
                windowSnapshots.forEach { spawnWindow(id, it) }
            }
        }

        _focusedWindowId.value = primary.id
        refreshActiveDesktop()
    }

    /** Serializes an OPEN desktop: all its windows (tabs + geometry) and their persisted states. */
    fun snapshotOpenDesktop(desktopId: String): DesktopTabsSnapshot {
        val wins = windowsOf(desktopId)
        val storeSnapshot = tabPersistedStateStore.snapshot()
        val windowSnapshots =
            wins.map { w ->
                val tabsState = w.tabsViewModel.state.value
                val destinations = tabsState.tabs.map { stripEphemeral(it.destination) }
                WindowSnapshot(
                    destinations = destinations,
                    selectedIndex = tabsState.selectedTabIndex.coerceIn(0, destinations.lastIndex.coerceAtLeast(0)),
                    titles =
                        tabsState.tabs.associate {
                            it.destination.tabId to SerializableTabTitle(title = it.title, tabType = it.tabType)
                        },
                    geometry = w.windowState.toSavedGeometry(),
                )
            }
        val tabIds = windowSnapshots.flatMap { snapshot -> snapshot.destinations.map { it.tabId } }
        return DesktopTabsSnapshot(
            tabStates = tabIds.associateWith { storeSnapshot[it] ?: TabPersistedState() },
            windows = windowSnapshots,
        )
    }

    // ---- Internals ----

    private fun newWindow(
        desktopId: String,
        snapshot: WindowSnapshot?,
    ): OpenWindow {
        val tabsViewModel = TabsViewModel(titleUpdateManager, freshHomeDestination())
        if (snapshot != null && snapshot.destinations.isNotEmpty()) {
            tabsViewModel.restoreTabs(
                destinations = snapshot.destinations,
                selectedIndex = snapshot.selectedIndex,
                titles = snapshot.titles.mapValues { (_, t) -> t.title to t.tabType },
                skipAnimation = true,
            )
        }
        return OpenWindow(
            id = UUID.randomUUID().toString(),
            desktopId = desktopId,
            tabsViewModel = tabsViewModel,
            searchHomeViewModel = searchHomeViewModelFactory(),
            windowState = snapshot?.geometry.toWindowState(),
        )
    }

    private fun spawnWindow(
        desktopId: String,
        snapshot: WindowSnapshot?,
    ): OpenWindow {
        val w = newWindow(desktopId, snapshot)
        _windows.update { it.add(w) }
        return w
    }

    private fun removeWindow(w: OpenWindow) {
        _windows.update { it.remove(w) }
        w.dispose()
        if (_focusedWindowId.value == w.id) {
            _focusedWindowId.value =
                _windows.value
                    .firstOrNull()
                    ?.id
                    .orEmpty()
        }
    }

    /** Snapshots [desktopId] to dormant and closes all its windows except [keepWindow]. */
    private fun putDesktopDormant(
        desktopId: String,
        keepWindow: OpenWindow?,
    ) {
        val snapshot = snapshotOpenDesktop(desktopId)
        dormantSnapshots[desktopId] = snapshot
        windowsOf(desktopId).filter { it !== keepWindow }.forEach { removeWindow(it) }
        tabPersistedStateStore.removeAll(snapshot.tabStates.keys)
    }

    /** Restores a dormant desktop into [win] (first window) and spawns its remaining windows. */
    private fun openDesktopInto(
        win: OpenWindow,
        desktopId: String,
    ) {
        val snapshot = dormantSnapshots.remove(desktopId)
        tabPersistedStateStore.putAll(snapshot?.tabStates.orEmpty())
        val windowSnapshots = snapshot?.effectiveWindows().orEmpty()
        val first = windowSnapshots.firstOrNull()
        if (first != null && first.destinations.isNotEmpty()) {
            restoreInto(win, first)
        } else {
            freshHomeInto(win)
        }
        // The window keeps its current frame on an in-place switch; only extra windows get geometry.
        windowSnapshots.drop(1).forEach { spawnWindow(desktopId, it) }
        win.setDesktop(desktopId)
    }

    private fun restoreInto(
        win: OpenWindow,
        snapshot: WindowSnapshot,
    ) {
        win.tabsViewModel.restoreTabs(
            destinations = snapshot.destinations,
            selectedIndex = snapshot.selectedIndex,
            titles = snapshot.titles.mapValues { (_, t) -> t.title to t.tabType },
            skipAnimation = true,
        )
    }

    private fun freshHomeInto(win: OpenWindow) {
        win.tabsViewModel.restoreTabs(
            destinations = listOf(freshHomeDestination()),
            selectedIndex = 0,
            skipAnimation = true,
        )
    }

    /**
     * Geometry for a window opened FROM an existing one ("open desktop in new window", Cmd+N):
     * it must never land exactly on top of the current window. A saved floating frame is kept as
     * long as it doesn't collide with an open window's origin; otherwise (or for maximized /
     * position-less snapshots — typical for desktops only ever used via in-place switching) the
     * window floats at 3/4 of the reference window, cascaded down-right macOS-style.
     */
    private fun cascadedFloatingGeometry(saved: SavedGeometry?): SavedGeometry {
        val referenceWindow = focusedWindow()
        val referenceBounds = referenceWindow?.boundsOnScreen()
        val referenceGeometry = referenceWindow?.windowState?.toSavedGeometry()

        var x: Int
        var y: Int
        val width: Int
        val height: Int
        if (saved != null && saved.placement == "Floating" && saved.x != SavedGeometry.UNSPECIFIED) {
            x = saved.x
            y = saved.y
            width = saved.width
            height = saved.height
        } else {
            width = ((saved?.takeIf { it.placement == "Floating" }?.width ?: referenceGeometry?.width ?: 1280) * 3 / 4).coerceIn(640, 1200)
            height = ((saved?.takeIf { it.placement == "Floating" }?.height ?: referenceGeometry?.height ?: 800) * 3 / 4).coerceIn(480, 840)
            x = referenceBounds?.x?.roundToInt()?.plus(CASCADE_OFFSET) ?: SavedGeometry.UNSPECIFIED
            y = referenceBounds?.y?.roundToInt()?.plus(CASCADE_OFFSET) ?: SavedGeometry.UNSPECIFIED
        }

        if (x != SavedGeometry.UNSPECIFIED) {
            val origins =
                _windows.value.mapNotNull { w ->
                    w.boundsOnScreen()?.let { it.x to it.y }
                        ?: w.windowState
                            .toSavedGeometry()
                            .takeIf { it.x != SavedGeometry.UNSPECIFIED }
                            ?.let { it.x.toFloat() to it.y.toFloat() }
                }
            var guard = 0
            while (guard++ < MAX_CASCADE_STEPS &&
                origins.any { (ox, oy) -> abs(ox - x) < CASCADE_MIN_DISTANCE && abs(oy - y) < CASCADE_MIN_DISTANCE }
            ) {
                x += CASCADE_OFFSET
                y += CASCADE_OFFSET
            }
        }
        return SavedGeometry(x = x, y = y, width = width, height = height, placement = "Floating")
    }

    private fun freshHomeDestination(): TabsDestination = TabsDestination.BookContent(bookId = -1, tabId = UUID.randomUUID().toString())

    private fun windowTabIds(w: OpenWindow): List<String> =
        w.tabsViewModel.state.value.tabs
            .map { it.destination.tabId }

    private fun stripEphemeral(destination: TabsDestination): TabsDestination =
        when (destination) {
            is TabsDestination.BookContent -> destination.copy(lineId = null)
            else -> destination
        }

    private fun refreshActiveDesktop() {
        _activeDesktopId.value =
            focusedWindow()?.desktopId?.value
                ?: _desktops.value
                    .firstOrNull()
                    ?.id
                    .orEmpty()
    }

    private companion object {
        const val CASCADE_OFFSET = 32
        const val CASCADE_MIN_DISTANCE = 24
        const val MAX_CASCADE_STEPS = 10
    }
}
