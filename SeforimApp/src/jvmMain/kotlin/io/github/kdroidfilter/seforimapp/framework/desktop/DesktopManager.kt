@file:OptIn(ExperimentalNucleusApi::class)

package io.github.kdroidfilter.seforimapp.framework.desktop

import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import dev.nucleusframework.window.ExperimentalNucleusApi
import io.github.kdroidfilter.seforim.desktop.VirtualDesktop
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import io.github.kdroidfilter.seforimapp.framework.session.DesktopTabsSnapshot
import io.github.kdroidfilter.seforimapp.framework.session.DesktopsState
import io.github.kdroidfilter.seforimapp.framework.session.SavedGeometry
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimapp.framework.session.WindowSnapshot
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Manages virtual desktops and the OS windows that display them.
 *
 * Model: a desktop is a user-curated set of tabs laid out in 1..n windows. A desktop is either
 * OPEN (a [DesktopSession]: a `TabWorkspace` whose groups are its windows) or DORMANT (a
 * serializable [DesktopTabsSnapshot]). Several desktops can be open at once, each in its own
 * window(s), but a desktop is never open twice. Windows follow the tabs: the workspace opens one
 * per group and closes it with the group's last tab; desktops are only ever created/deleted
 * explicitly by the user.
 *
 * Per-tab UI state lives in the app-wide [TabPersistedStateStore] (tabIds are UUIDs, so entries
 * from different windows/desktops never collide); opening/closing a desktop loads/unloads its
 * entries instead of wiping the store.
 *
 * Every member is meant for the UI thread, like the workspaces it drives.
 */
class DesktopManager(
    private val tabPersistedStateStore: TabPersistedStateStore,
    titleUpdateManager: TabTitleUpdateManager,
    private val searchHomeViewModelFactory: () -> SearchHomeViewModel,
    defaultDesktopName: String,
    // The saved session, restored before the first frame (an app composing no window is closed).
    bootState: DesktopsState? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val defaultDesktopId = UUID.randomUUID().toString()

    private val _desktops =
        MutableStateFlow(
            persistentListOf(VirtualDesktop(id = defaultDesktopId, name = defaultDesktopName)),
        )
    val desktops: StateFlow<ImmutableList<VirtualDesktop>> = _desktops.asStateFlow()

    private val _sessions = MutableStateFlow(persistentListOf<DesktopSession>())

    /** The open desktops; main.kt composes one `TabWindows` per session. */
    val sessions: StateFlow<ImmutableList<DesktopSession>> = _sessions.asStateFlow()

    private val _windows = MutableStateFlow(persistentListOf<OpenWindow>())
    val windows: StateFlow<ImmutableList<OpenWindow>> = _windows.asStateFlow()

    private val _focusedWindowId = MutableStateFlow("")
    val focusedWindowId: StateFlow<String> = _focusedWindowId.asStateFlow()

    /** Desktop of the focused window. Kept for consumers that need "the" current desktop. */
    private val _activeDesktopId = MutableStateFlow(defaultDesktopId)
    val activeDesktopId: StateFlow<String> = _activeDesktopId.asStateFlow()

    /** Snapshots of desktops that are not currently open in any window. */
    private val dormantSnapshots = mutableMapOf<String, DesktopTabsSnapshot>()

    /**
     * App-level quit path (persist session, apply pending updates, exit). Wired by main.kt;
     * invoked when the last window closes or the last tab of the last window is closed.
     */
    var onQuitRequest: (() -> Unit)? = null

    init {
        if (bootState != null) restoreFromDesktopsState(bootState) else openDesktop(defaultDesktopId)
        collectTitles(scope, titleUpdateManager)
    }

    private fun collectTitles(
        @StructuredScope scope: CoroutineScope,
        titleUpdateManager: TabTitleUpdateManager,
    ) {
        scope.launch {
            titleUpdateManager.titleUpdates.collect { update ->
                _sessions.value.firstOrNull { it.updateTitle(update.tabId, update.newTitle, update.tabType) }
            }
        }
    }

    // ---- Lookups ----

    fun window(windowId: String): OpenWindow? = _windows.value.find { it.id == windowId }

    fun focusedWindow(): OpenWindow? = window(_focusedWindowId.value) ?: _windows.value.firstOrNull()

    fun windowsOf(desktopId: String): List<OpenWindow> = _windows.value.filter { it.session.desktopId == desktopId }

    fun isDesktopOpen(desktopId: String): Boolean = session(desktopId) != null

    /** Ordered list of desktops currently open in windows. */
    fun openDesktopIds(): List<String> = _sessions.value.map { it.desktopId }

    private fun session(desktopId: String): DesktopSession? = _sessions.value.firstOrNull { it.desktopId == desktopId }

    private fun sessionOf(tabId: String): DesktopSession? = _sessions.value.firstOrNull { it.item(tabId) != null }

    /** True while [tabId] is open in any window. */
    fun isTabOpen(tabId: String): Boolean = sessionOf(tabId) != null

    /**
     * The [TabsViewModel] of the window currently hosting [tabId]. Per-tab ViewModels navigate
     * through this instead of a fixed window reference, so a tab dragged to another window keeps
     * opening its results in whatever window it lives in now.
     */
    fun tabsViewModelFor(tabId: String): TabsViewModel? {
        val session = sessionOf(tabId) ?: return null
        val groupId =
            session.workspace
                .tab(tabId)
                ?.group
                ?.id ?: session.initialGroupOf(tabId) ?: return null
        return session.windows[groupId]?.tabsViewModel
    }

    /** Emits whether [tabId] is open in any window; used to cancel work when a tab closes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun tabExistsFlow(tabId: String): Flow<Boolean> =
        _sessions
            .flatMapLatest { sessions -> snapshotFlow { sessions.any { it.item(tabId) != null } } }
            .distinctUntilChanged()

    fun onWindowFocused(windowId: String) {
        if (window(windowId) == null) return
        _focusedWindowId.value = windowId
        refreshActiveDesktop()
    }

    // ---- Windows (driven by the window composables) ----

    /** The [OpenWindow] of [groupId], created when its window first composes. */
    internal fun attachWindow(
        session: DesktopSession,
        groupId: String,
    ): OpenWindow {
        session.windows[groupId]?.let { return it }
        val w =
            OpenWindow(
                id = groupId,
                session = session,
                tabsViewModel = TabsViewModel(session, groupId),
                searchHomeViewModel = searchHomeViewModelFactory(),
            )
        session.windows[groupId] = w
        _windows.update { it.add(w) }
        if (window(_focusedWindowId.value) == null) _focusedWindowId.value = groupId
        refreshActiveDesktop()
        return w
    }

    internal fun detachWindow(w: OpenWindow) {
        if (w.session.windows[w.id] === w) w.session.windows.remove(w.id)
        w.session.forgetWindow(w.id)
        removeWindow(w)
    }

    /**
     * The user asked to close [w] (title-bar X, Alt+F4, Cmd+W on the window). The app's last window
     * quits with the session intact; a desktop's last window puts the desktop to sleep with its
     * content; any other window discards its tabs (Chrome-like).
     */
    fun onWindowCloseRequest(w: OpenWindow) {
        if (_windows.value.size <= 1) {
            onQuitRequest?.invoke()
            return
        }
        if (w.session.windows.size <= 1) {
            putDormant(w.session)
            return
        }
        val ids =
            w.session
                .group(w.id)
                ?.ids
                .orEmpty()
        ids.forEach(w.session.workspace::close)
    }

    /** A tab the workspace closed (×, window close): forget it and its state. */
    fun onTabClosed(
        session: DesktopSession,
        tabId: String,
    ) {
        session.forget(tabId)
        tabPersistedStateStore.remove(tabId)
    }

    /** Every window of [session] is gone because its last tab was closed. */
    fun onSessionEmptied(session: DesktopSession) {
        if (session !in _sessions.value) return
        dormantSnapshots[session.desktopId] = DesktopTabsSnapshot()
        closeSession(session)
        if (_sessions.value.isEmpty()) onQuitRequest?.invoke()
    }

    // ---- Desktop switching ----

    /** Switches the focused window to [desktopId] (launcher/dock-menu entry point). */
    fun switchTo(desktopId: String) {
        val focused = focusedWindow()
        if (focused == null) {
            openInNewWindow(desktopId)
        } else {
            switchTo(focused.id, desktopId)
        }
    }

    /**
     * Shows [desktopId] where the given window is. If the desktop is already open in another
     * window, that window is focused instead (a desktop is never open twice). Otherwise the
     * window's desktop goes dormant as a whole (all its windows) and the target opens with its
     * first window on the same frame.
     */
    fun switchTo(
        windowId: String,
        desktopId: String,
    ) {
        val win = window(windowId) ?: return
        if (win.session.desktopId == desktopId) return
        if (_desktops.value.none { it.id == desktopId }) return

        windowsOf(desktopId).firstOrNull()?.let { other ->
            other.requestFocus()
            onWindowFocused(other.id)
            return
        }
        val frame = geometryOf(win)
        putDormant(win.session)
        openDesktop(desktopId, firstWindowGeometry = frame)
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
        val index = current.indexOfFirst { it.id == win.session.desktopId }
        if (index < 0) return
        val target = current[(index + direction + current.size) % current.size]
        switchTo(windowId, target.id)
    }

    /** Opens a dormant desktop in a new window (or focuses it if already open). */
    fun openInNewWindow(desktopId: String) {
        windowsOf(desktopId).firstOrNull()?.let { other ->
            other.requestFocus()
            onWindowFocused(other.id)
            return
        }
        if (_desktops.value.none { it.id == desktopId }) return
        val saved =
            dormantSnapshots[desktopId]
                ?.effectiveWindows()
                ?.firstOrNull()
                ?.geometry
        openDesktop(desktopId, firstWindowGeometry = cascadedFloatingGeometry(saved))
    }

    /**
     * Detaches a tab into a new window of the SAME desktop ("open in new window"). Returns false
     * when the tab is its window's only tab (dragging the whole window around covers that case).
     */
    fun detachTabToNewWindow(
        tabId: String,
        fromWindowId: String,
    ): Boolean {
        val from = window(fromWindowId) ?: return false
        val group = from.session.group(from.id) ?: return false
        if (group.ids.size <= 1 || tabId !in group.ids) return false
        val geometry = cascadedFloatingGeometry(null)
        val rect =
            Rect(
                left = geometry.x.toFloat(),
                top = geometry.y.toFloat(),
                right = (geometry.x + geometry.width).toFloat(),
                bottom = (geometry.y + geometry.height).toFloat(),
            )
        // Rect in dp with a 1:1 scale: the workspace places windows in dp.
        return from.session.workspace.tearOff(tabId, rect, scaleFactor = 1f) != null
    }

    // ---- Desktop CRUD ----

    /** Creates a desktop and switches the focused window to it. */
    fun createDesktop(name: String): String = createDesktop(focusedWindow()?.id.orEmpty(), name)

    fun createDesktop(
        windowId: String,
        name: String,
    ): String {
        val id = UUID.randomUUID().toString()
        _desktops.update { (it + VirtualDesktop(id = id, name = name)).toPersistentList() }
        val win = window(windowId)
        if (win == null) {
            openDesktop(id)
        } else {
            switchTo(win.id, id)
        }
        return id
    }

    /** Creates a desktop and opens it in a brand-new window, keeping the others as they are. */
    fun createDesktopInNewWindow(name: String): String {
        val id = UUID.randomUUID().toString()
        _desktops.update { (it + VirtualDesktop(id = id, name = name)).toPersistentList() }
        openDesktop(id, firstWindowGeometry = cascadedFloatingGeometry(null))
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

        session(id)?.let { doomed ->
            if (_sessions.value.size == 1) {
                // The desktop being deleted owns every window: show a neighbor in its place.
                val neighbor = current[if (index > 0) index - 1 else index + 1]
                openDesktop(
                    neighbor.id,
                    firstWindowGeometry =
                        doomed.windows.values
                            .firstOrNull()
                            ?.let(::geometryOf),
                )
            }
            tabPersistedStateStore.removeAll(doomed.tabIds())
            closeSession(doomed)
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
        val focusedDesktop = focusedWindow()?.session?.desktopId ?: open.firstOrNull().orEmpty()
        return DesktopsState(
            desktops = _desktops.value,
            activeDesktopId = focusedDesktop,
            snapshots = allSnapshots,
            openDesktopIds = open,
            focusedDesktopId = focusedDesktop,
        )
    }

    /** Restores the persisted state at boot: reopens every previously open desktop with its windows. */
    fun restoreFromDesktopsState(state: DesktopsState) {
        if (state.desktops.isEmpty()) {
            ensureWindow()
            return
        }
        _sessions.value.forEach(::closeSession)
        tabPersistedStateStore.clearAll()
        _desktops.value = state.desktops.toPersistentList()
        dormantSnapshots.clear()
        dormantSnapshots.putAll(state.snapshots)

        val openIds = state.effectiveOpenDesktopIds().ifEmpty { listOf(state.desktops.first().id) }
        val focused = state.focusedDesktopId.takeIf { it in openIds } ?: openIds.first()
        // The focused desktop first: its windows are created first and take focus.
        (listOf(focused) + openIds.filter { it != focused }).forEach { openDesktop(it) }
        refreshActiveDesktop()
    }

    /** Opens a fresh Home window when nothing is open (boot without a usable saved session). */
    fun ensureWindow() {
        if (_sessions.value.isEmpty()) openDesktop(_desktops.value.first().id)
    }

    /** Serializes an OPEN desktop: all its windows (tabs + geometry) and their persisted states. */
    fun snapshotOpenDesktop(desktopId: String): DesktopTabsSnapshot {
        val session = session(desktopId) ?: return dormantSnapshots[desktopId] ?: DesktopTabsSnapshot()
        val windows = session.snapshotWindows()
        val storeSnapshot = tabPersistedStateStore.snapshot()
        val tabIds = windows.flatMap { snapshot -> snapshot.destinations.map { it.tabId } }
        return DesktopTabsSnapshot(
            tabStates = tabIds.associateWith { storeSnapshot[it] ?: TabPersistedState() },
            windows = windows,
        )
    }

    // ---- Internals ----

    /** Opens [desktopId] from its dormant snapshot (or a fresh Home window). */
    private fun openDesktop(
        desktopId: String,
        firstWindowGeometry: SavedGeometry? = null,
    ): DesktopSession {
        session(desktopId)?.let { return it }
        val snapshot = dormantSnapshots.remove(desktopId)
        tabPersistedStateStore.putAll(snapshot?.tabStates.orEmpty())
        val windows =
            snapshot
                ?.effectiveWindows()
                .orEmpty()
                .filter { it.destinations.isNotEmpty() }
                .ifEmpty { listOf(WindowSnapshot(destinations = listOf(freshHomeDestination()))) }
                .toMutableList()
        if (firstWindowGeometry != null) windows[0] = windows[0].copy(geometry = firstWindowGeometry)
        val session = DesktopSession(desktopId)
        session.restore(windows)
        _sessions.update { it.add(session) }
        refreshActiveDesktop()
        return session
    }

    /** Snapshots [session] to dormant and closes all its windows. */
    private fun putDormant(session: DesktopSession) {
        val snapshot = snapshotOpenDesktop(session.desktopId)
        dormantSnapshots[session.desktopId] = snapshot
        tabPersistedStateStore.removeAll(snapshot.tabStates.keys)
        closeSession(session)
    }

    private fun closeSession(session: DesktopSession) {
        _sessions.update { it.remove(session) }
        session.windows.values
            .toList()
            .forEach(::removeWindow)
        session.dispose()
        refreshActiveDesktop()
    }

    private fun removeWindow(w: OpenWindow) {
        if (w !in _windows.value) return
        _windows.update { it.remove(w) }
        w.dispose()
        if (_focusedWindowId.value == w.id) {
            _focusedWindowId.value =
                _windows.value
                    .firstOrNull()
                    ?.id
                    .orEmpty()
        }
        refreshActiveDesktop()
    }

    private fun geometryOf(w: OpenWindow): SavedGeometry? =
        w.session
            .snapshotWindows()
            .firstOrNull { snapshot ->
                snapshot.destinations.any {
                    it.tabId in
                        w.session
                            .group(w.id)
                            ?.ids
                            .orEmpty()
                }
            }?.geometry

    /**
     * Geometry for a window opened FROM an existing one ("open desktop in new window", Cmd+N): it
     * must never land exactly on top of the current window. A saved floating frame is kept as long
     * as it doesn't collide with an open window's origin; otherwise the window floats at 3/4 of
     * the reference window, cascaded down-right macOS-style.
     */
    private fun cascadedFloatingGeometry(saved: SavedGeometry?): SavedGeometry {
        val referenceBounds = focusedWindow()?.boundsOnScreen()

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
            width = ((referenceBounds?.width?.roundToInt() ?: DEFAULT_W) * 3 / 4).coerceIn(MIN_W, MAX_W)
            height = ((referenceBounds?.height?.roundToInt() ?: DEFAULT_H) * 3 / 4).coerceIn(MIN_H, MAX_H)
            x = referenceBounds?.x?.roundToInt()?.plus(CASCADE_OFFSET) ?: SavedGeometry.UNSPECIFIED
            y = referenceBounds?.y?.roundToInt()?.plus(CASCADE_OFFSET) ?: SavedGeometry.UNSPECIFIED
        }

        if (x != SavedGeometry.UNSPECIFIED) {
            val origins = _windows.value.mapNotNull { w -> w.boundsOnScreen()?.let { it.x to it.y } }
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

    private fun refreshActiveDesktop() {
        _activeDesktopId.value =
            focusedWindow()?.session?.desktopId
                ?: _sessions.value.firstOrNull()?.desktopId
                ?: _desktops.value
                    .firstOrNull()
                    ?.id
                    .orEmpty()
    }

    private companion object {
        const val CASCADE_OFFSET = 32
        const val CASCADE_MIN_DISTANCE = 24
        const val MAX_CASCADE_STEPS = 10
        const val DEFAULT_W = 1280
        const val DEFAULT_H = 800
        const val MIN_W = 640
        const val MAX_W = 1200
        const val MIN_H = 480
        const val MAX_H = 840
    }
}
