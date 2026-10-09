@file:OptIn(ExperimentalNucleusApi::class)

package io.github.kdroidfilter.seforimapp.framework.desktop

import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.tao.TabDropTarget
import dev.nucleusframework.window.tao.TabWorkspace
import io.github.kdroidfilter.seforim.desktop.VirtualDesktop
import io.github.kdroidfilter.seforim.tabs.TabItem
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforim.tabs.withTabId
import io.github.kdroidfilter.seforimapp.core.e2e.E2e
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import io.github.kdroidfilter.seforimapp.framework.session.DesktopTabsSnapshot
import io.github.kdroidfilter.seforimapp.framework.session.DesktopsState
import io.github.kdroidfilter.seforimapp.framework.session.SavedGeometry
import io.github.kdroidfilter.seforimapp.framework.session.SerializableTabTitle
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimapp.framework.session.TabThumbnailStore
import io.github.kdroidfilter.seforimapp.framework.session.WindowSnapshot
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
 * OPEN (a [DesktopSession]: a `TabWorkspace` whose groups are its windows' tab sets) or DORMANT (a
 * serializable [DesktopTabsSnapshot]). Several desktops can be open at once, each in its own
 * window(s), but a desktop is never open twice. The app owns its windows ([windows]): one per
 * group, created for a restored window, a new desktop or a tab the user tore off, and closed with
 * the group's last tab. Desktops are only ever created/deleted explicitly by the user.
 *
 * Per-tab UI state lives in the app-wide [TabPersistedStateStore] (tabIds are UUIDs, so entries
 * from different windows/desktops never collide); opening/closing a desktop loads/unloads its
 * entries instead of wiping the store.
 *
 * Every member is meant for the UI thread, like the workspaces it drives.
 */
class DesktopManager(
    private val tabPersistedStateStore: TabPersistedStateStore,
    private val thumbnails: TabThumbnailStore,
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

    /** The open desktops; main.kt declares every session's tabs and panes. */
    val sessions: StateFlow<ImmutableList<DesktopSession>> = _sessions.asStateFlow()

    private val _windows = MutableStateFlow(persistentListOf<OpenWindow>())
    val windows: StateFlow<ImmutableList<OpenWindow>> = _windows.asStateFlow()

    private val _focusedWindowId = MutableStateFlow("")
    val focusedWindowId: StateFlow<String> = _focusedWindowId.asStateFlow()

    // Window ids, the one focused last at the end: where a closed window's pinned tabs go.
    private val focusHistory = ArrayDeque<String>()

    /** Desktop of the focused window. Kept for consumers that need "the" current desktop. */
    private val _activeDesktopId = MutableStateFlow(defaultDesktopId)
    val activeDesktopId: StateFlow<String> = _activeDesktopId.asStateFlow()

    /** Snapshots of desktops that are not currently open in any window. */
    private val dormantSnapshots = mutableMapOf<String, DesktopTabsSnapshot>()

    /** The coroutine following each open session's groups (tear-offs, emptied windows). */
    private val watchers = HashMap<DesktopSession, Job>()

    /**
     * App-level quit path (persist session, apply pending updates, exit). Wired by main.kt;
     * invoked when the last tab of the last window is closed (Chrome-like).
     */
    var onQuitRequest: (() -> Unit)? = null

    init {
        if (bootState != null) restoreFromDesktopsState(bootState) else openDesktop(defaultDesktopId)
        collectTitles(scope, titleUpdateManager)
        linkWorkspaces(scope)
    }

    private fun collectTitles(
        @StructuredScope scope: CoroutineScope,
        titleUpdateManager: TabTitleUpdateManager,
    ) {
        scope.launch {
            titleUpdateManager.titleUpdates.collect { update ->
                _sessions.value.firstOrNull { it.updateTitle(update.tabId, update.newTitle, update.tabType, update.shortTitle) }
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

    /** A search bar's state of its own, for a tab that has its bar (the search results'). */
    fun newSearchBarViewModel(): SearchHomeViewModel = searchHomeViewModelFactory()

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
        return _windows.value.firstOrNull { it.session === session && it.groupId == groupId }?.tabsViewModel
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
        focusHistory.remove(windowId)
        focusHistory.addLast(windowId)
        refreshActiveDesktop()
    }

    // ---- Tabs (driven by the tab declarations) ----

    /** A tab the workspace closed (×, window close): forget it and its state, kept for a reopen. */
    fun onTabClosed(
        session: DesktopSession,
        tabId: String,
    ) {
        session.forget(tabId, tabPersistedStateStore.get(tabId))
        tabPersistedStateStore.remove(tabId)
    }

    /** Brings back the last tab closed on [windowId]'s desktop into that window, with its reading state. */
    fun reopenClosedTab(windowId: String) {
        val win = window(windowId) ?: return
        val closed = win.session.popClosedTab() ?: return
        val destination = closed.item.destination
        closed.state?.let { tabPersistedStateStore.set(destination.tabId, it) }
        val index = win.group()?.let { it.ids.indexOf(it.selectedId) }?.coerceAtLeast(0) ?: 0
        win.session.addTab(
            destination,
            win.groupId,
            index,
            title = closed.item.title,
            tabType = closed.item.tabType,
            pinned = closed.item.pinned,
            shortTitle = closed.item.shortTitle,
        )
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
        if (win.session.desktopId == desktopId) return
        if (_desktops.value.none { it.id == desktopId }) return

        windowsOf(desktopId).firstOrNull()?.let { other ->
            other.requestFocus()
            onWindowFocused(other.id)
            return
        }

        win.markSwitching()
        putDesktopDormant(win.session, keepWindow = win)
        openDesktop(desktopId, keepWindow = win)
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
        val index = current.indexOfFirst { it.id == win.session.desktopId }
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
        openDesktop(desktopId, cascade = true)
        refreshActiveDesktop()
    }

    /**
     * Closes a window. If it was its desktop's last window, the desktop goes dormant (content
     * preserved); otherwise the window's tabs are discarded (Chrome-like), except its pinned ones:
     * like Arc's, a pinned tab belongs to the desktop, so they join the desktop's window focused
     * last. The last window of the app is never closed here — that's the quit path, handled by the
     * caller.
     */
    fun closeWindow(windowId: String) {
        val win = window(windowId) ?: return
        if (_windows.value.size <= 1) return
        val session = win.session
        if (windowsOf(session.desktopId).size == 1) {
            putDesktopDormant(session, keepWindow = null)
        } else {
            val (pinned, others) =
                session
                    .group(win.groupId)
                    ?.ids
                    .orEmpty()
                    .partition(session::isPinned)
            val heir = windowsOf(session.desktopId).filter { it !== win }.maxByOrNull { focusHistory.indexOf(it.id) }
            removeWindow(win)
            val heirGroup = heir?.group()
            if (heirGroup != null) {
                val selected = heirGroup.selectedId
                // Same ids: their ViewModels and states move along; constrained to the end of the pinned run.
                pinned.forEach { session.workspace.move(it, heirGroup) }
                selected?.let(session.workspace::select)
            }
            // Their states go with onTabClosed, which keeps them for a reopen. With no group to take them
            // (the heir still awaiting its restore), the pinned ones go too rather than stay windowless.
            (if (heirGroup != null) others else others + pinned).forEach(session.workspace::close)
        }
        refreshActiveDesktop()
    }

    /**
     * Detaches a tab into a new window of the SAME desktop ("open in new window"). No desktop is
     * ever created implicitly. Returns false when the tab is the window's only tab (dragging the
     * whole window around covers that case).
     */
    fun detachTabToNewWindow(
        tabId: String,
        fromWindowId: String,
    ): Boolean {
        val from = window(fromWindowId) ?: return false
        val group = from.group() ?: return false
        if (group.ids.size <= 1 || tabId !in group.ids) return false
        // Chrome-like: the detached window floats noticeably smaller than the (often maximized)
        // source window and cascades from it.
        val cascaded = cascadedFloatingGeometry(null)
        // Nothing to cascade from on screen yet (the focused window closing, or not shown): the
        // source window's place, else the main screen's centre. Never the UNSPECIFIED sentinel,
        // Int.MIN_VALUE: AppKit refuses it as a window frame and the exception aborts the app.
        val geometry =
            if (cascaded.x != SavedGeometry.UNSPECIFIED) {
                cascaded
            } else {
                val (x, y) =
                    from.boundsOnScreen()?.let { it.x.roundToInt() + CASCADE_OFFSET to it.y.roundToInt() + CASCADE_OFFSET }
                        ?: from.windowState
                            .toSavedGeometry()
                            .takeIf { it.x != SavedGeometry.UNSPECIFIED }
                            ?.let { it.x + CASCADE_OFFSET to it.y + CASCADE_OFFSET }
                        ?: centeredOnMainScreen(cascaded.width, cascaded.height)
                cascaded.copy(x = x, y = y)
            }
        val rect =
            Rect(
                left = geometry.x.toFloat(),
                top = geometry.y.toFloat(),
                right = (geometry.x + geometry.width).toFloat(),
                bottom = (geometry.y + geometry.height).toFloat(),
            )
        // In dp with a 1:1 scale: the workspace places windows in dp. The new group's window is
        // opened by the session watcher.
        return from.session.workspace.tearOff(tabId, rect, scaleFactor = 1f) != null
    }

    /**
     * Moves a tab to the end of another desktop's first window, open or dormant, with its reading
     * state. The window's last tab stays (as for [detachTabToNewWindow]: closing it would close
     * the window, or quit the app).
     */
    fun moveTabToDesktop(
        tabId: String,
        fromWindowId: String,
        desktopId: String,
    ): Boolean {
        val from = window(fromWindowId) ?: return false
        val source = from.session
        val group = from.group() ?: return false
        val item = source.item(tabId) ?: return false
        if (group.ids.size <= 1 || desktopId == source.desktopId || _desktops.value.none { it.id == desktopId }) return false
        // A new id: the source's cleanup of the closed tab (its state, its ViewModels) must not
        // reach the moved one.
        val newId = UUID.randomUUID().toString()
        val destination = item.destination.withTabId(newId)
        val state = tabPersistedStateStore.get(tabId) ?: TabPersistedState()
        val target = windowsOf(desktopId).firstOrNull()
        if (target != null) {
            // Added, not opened: the target window keeps its selection.
            addMovedTab(item, newId, state, target, index = target.group()?.ids?.size ?: 0, select = false)
        } else {
            thumbnails.copy(tabId, newId)
            val snapshot = dormantSnapshots[desktopId] ?: DesktopTabsSnapshot()
            val windows = snapshot.effectiveWindows().filter { it.destinations.isNotEmpty() }
            val first = windows.firstOrNull() ?: WindowSnapshot()
            val moved =
                first.copy(
                    destinations = first.destinations + destination,
                    titles = first.titles + (newId to SerializableTabTitle(item.title, item.tabType, item.pinned, item.shortTitle)),
                )
            dormantSnapshots[desktopId] =
                snapshot.copy(
                    destinations = emptyList(),
                    titles = emptyMap(),
                    tabStates = snapshot.tabStates + (newId to state),
                    windows = listOf(moved) + windows.drop(1),
                )
        }
        source.discard(tabId)
        return true
    }

    private fun addMovedTab(
        item: TabItem,
        newId: String,
        state: TabPersistedState,
        target: OpenWindow,
        index: Int,
        select: Boolean,
    ) {
        tabPersistedStateStore.putAll(mapOf(newId to state))
        thumbnails.copy(item.destination.tabId, newId)
        target.session.addTab(
            item.destination.withTabId(newId),
            target.groupId,
            index,
            title = item.title,
            tabType = item.tabType,
            select = select,
            pinned = item.pinned,
            shortTitle = item.shortTitle,
        )
    }

    /**
     * A tab of [source] dragged onto the strip of another open desktop's window ([target] in
     * [into]): it joins that window where it was dropped, selected, with its reading state — a
     * window left empty closes, as after any drag.
     */
    private fun onForeignDrop(
        source: DesktopSession,
        tabId: String,
        into: TabWorkspace,
        target: TabDropTarget,
    ) {
        val item = source.item(tabId) ?: return
        val window = _windows.value.firstOrNull { it.session.workspace === into && it.groupId == target.group.id } ?: return
        val newId = UUID.randomUUID().toString()
        addMovedTab(item, newId, tabPersistedStateStore.get(tabId) ?: TabPersistedState(), window, target.index, select = true)
        source.discard(tabId)
        window.requestFocus()
        onWindowFocused(window.id)
    }

    /** Every open desktop's strips take the others' tab drags (see [onForeignDrop]). */
    private fun linkWorkspaces(
        @StructuredScope scope: CoroutineScope,
    ) {
        scope.launch {
            _sessions.collect { open ->
                for (session in open) {
                    session.workspace.linkedWorkspaces = open.filter { it !== session }.map { it.workspace }
                    session.workspace.onForeignDrop = { tab, into, target -> onForeignDrop(session, tab.id, into, target) }
                    // A tab dragged in from another desktop is looked up there for its pin.
                    session.workspace.constrainIndex = { tabId, group, index ->
                        session.pinConstrained(tabId, open.any { it.isPinned(tabId) }, group, index)
                    }
                }
            }
        }
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
        putDesktopDormant(win.session, keepWindow = win)
        openDesktop(id, keepWindow = win)
        refreshActiveDesktop()
        return id
    }

    /** Creates a desktop and opens it in a brand-new window, keeping the others as they are. */
    fun createDesktopInNewWindow(name: String): String {
        val id = UUID.randomUUID().toString()
        _desktops.update { (it + VirtualDesktop(id = id, name = name)).toPersistentList() }
        openDesktop(id, cascade = true)
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

        session(id)?.let { doomed ->
            val wins = windowsOf(id)
            tabPersistedStateStore.removeAll(doomed.tabIds())
            if (wins.isNotEmpty() && wins.size == _windows.value.size) {
                // The desktop being deleted owns every window: keep one alive on a neighbor desktop.
                val neighbor = current[if (index > 0) index - 1 else index + 1]
                val keep = wins.first()
                keep.markSwitching()
                closeSession(doomed, keepWindow = keep)
                openDesktop(neighbor.id, keepWindow = keep)
            } else {
                closeSession(doomed, keepWindow = null)
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
        val focusedDesktop = focusedWindow()?.session?.desktopId ?: open.firstOrNull().orEmpty()
        return DesktopsState(
            desktops = _desktops.value,
            activeDesktopId = focusedDesktop,
            snapshots = allSnapshots,
            openDesktopIds = open,
            focusedDesktopId = focusedDesktop,
        )
    }

    /**
     * Restores the persisted state at boot: reopens every previously open desktop with its window
     * geometry (clamped to the current screens). The focused desktop's windows come first.
     */
    fun restoreFromDesktopsState(state: DesktopsState) {
        if (state.desktops.isEmpty()) {
            ensureWindow()
            return
        }
        _sessions.value.forEach { closeSession(it, keepWindow = null) }
        tabPersistedStateStore.clearAll()
        _desktops.value = state.desktops.toPersistentList()
        dormantSnapshots.clear()
        dormantSnapshots.putAll(state.snapshots)

        val openIds = state.effectiveOpenDesktopIds().ifEmpty { listOf(state.desktops.first().id) }
        val focused = state.focusedDesktopId.takeIf { it in openIds } ?: openIds.first()
        (listOf(focused) + openIds.filter { it != focused }).forEach { openDesktop(it) }
        _windows.value.firstOrNull()?.let { _focusedWindowId.value = it.id }
        refreshActiveDesktop()
    }

    /** Opens a fresh Home window when nothing is open. */
    fun ensureWindow() {
        if (_sessions.value.isEmpty()) openDesktop(_desktops.value.first().id)
    }

    /** Serializes an OPEN desktop: all its windows (tabs + geometry) and their persisted states. */
    fun snapshotOpenDesktop(desktopId: String): DesktopTabsSnapshot {
        val session = session(desktopId) ?: return dormantSnapshots[desktopId] ?: DesktopTabsSnapshot()
        val windowSnapshots =
            windowsOf(desktopId).mapNotNull { w ->
                session.windowSnapshot(w.groupId)?.copy(geometry = w.savedGeometry())
            }
        val storeSnapshot = tabPersistedStateStore.snapshot()
        val tabIds = windowSnapshots.flatMap { snapshot -> snapshot.destinations.map { it.tabId } }
        return DesktopTabsSnapshot(
            tabStates = tabIds.associateWith { storeSnapshot[it] ?: TabPersistedState() },
            windows = windowSnapshots,
        )
    }

    // ---- Internals ----

    /**
     * Opens [desktopId] from its dormant snapshot (or a fresh Home window). [keepWindow] is rebound
     * to the first window in place (a desktop switch); otherwise every window is new, cascading
     * from the focused one when [cascade].
     */
    private fun openDesktop(
        desktopId: String,
        keepWindow: OpenWindow? = null,
        cascade: Boolean = false,
    ): DesktopSession {
        session(desktopId)?.let { return it }
        val snapshot = dormantSnapshots.remove(desktopId)
        tabPersistedStateStore.putAll(snapshot?.tabStates.orEmpty())
        val windowSnapshots =
            snapshot
                ?.effectiveWindows()
                .orEmpty()
                .filter { it.destinations.isNotEmpty() }
                .ifEmpty { listOf(WindowSnapshot(destinations = listOf(freshHomeDestination()))) }
        val session = DesktopSession(desktopId)
        val groupIds = session.restore(windowSnapshots)
        _sessions.update { it.adding(session) }
        groupIds.forEachIndexed { index, groupId ->
            val saved = windowSnapshots[index].geometry
            if (index == 0 && keepWindow != null) {
                // The window keeps its current frame on an in-place switch.
                keepWindow.bind(session, groupId)
            } else {
                val geometry = if (cascade) cascadedFloatingGeometry(saved) else saved
                spawnWindow(session, groupId, geometry.toWindowState())
            }
        }
        watch(session)
        refreshActiveDesktop()
        return session
    }

    /** Snapshots [session] to dormant and closes all its windows except [keepWindow]. */
    private fun putDesktopDormant(
        session: DesktopSession,
        keepWindow: OpenWindow?,
    ) {
        val snapshot = snapshotOpenDesktop(session.desktopId)
        dormantSnapshots[session.desktopId] = snapshot
        tabPersistedStateStore.removeAll(snapshot.tabStates.keys)
        closeSession(session, keepWindow)
    }

    private fun closeSession(
        session: DesktopSession,
        keepWindow: OpenWindow?,
    ) {
        watchers.remove(session)?.cancel()
        _sessions.update { it.removing(session) }
        windowsOf(session.desktopId).filter { it !== keepWindow }.forEach(::removeWindow)
        session.dispose()
    }

    /**
     * Follows [session]'s groups: a group the user tore off gets a window where the workspace put
     * it; a window whose group lost its last tab closes (Chrome-like: the last window of a desktop
     * puts it to sleep, the last window of the app quits).
     *
     * Reconciles on window changes too, reading the groups live: a window closed while a tab was
     * being opened in it sees its group go and come back under the same id, which the group ids
     * alone (conflated, equal before and after) never report — that group then had no window.
     */
    private fun watch(session: DesktopSession) {
        watchers[session]?.cancel()
        watchers[session] = launchWatch(scope, session)
    }

    private fun launchWatch(
        @StructuredScope scope: CoroutineScope,
        session: DesktopSession,
    ): Job =
        scope.launch {
            combine(snapshotFlow { session.workspace.groups.map { it.id } }, _windows) { _, _ -> }.collect {
                val groupIds = session.workspace.groups.map { it.id }
                groupIds.forEach(session::onGroupPlaced)
                val shown = windowsOf(session.desktopId).map { it.groupId }.toSet()
                for (groupId in groupIds) {
                    if (groupId in shown) continue
                    val group = session.group(groupId) ?: continue
                    val position = group.position?.let { WindowPosition.Absolute(it.x, it.y) } ?: WindowPosition.PlatformDefault
                    spawnWindow(session, groupId, WindowState(WindowPlacement.Floating, position = position, size = group.size))
                }
                val emptied = windowsOf(session.desktopId).filter { it.groupId !in groupIds && !session.isAwaiting(it.groupId) }
                for (w in emptied) onWindowEmptied(w)
            }
        }

    private fun onWindowEmptied(w: OpenWindow) {
        when {
            _windows.value.size <= 1 -> {
                if (E2e.enabled) {
                    val groups =
                        w.session.workspace.groups
                            .map { it.id to it.ids.size }
                    println("E2E QUIT: window ${w.id.take(8)} of ${w.session.desktopId} emptied (group ${w.groupId}, groups=$groups)")
                }
                onQuitRequest?.invoke()
            }
            windowsOf(w.session.desktopId).size == 1 -> {
                dormantSnapshots[w.session.desktopId] = DesktopTabsSnapshot()
                closeSession(w.session, keepWindow = null)
            }
            else -> removeWindow(w)
        }
        refreshActiveDesktop()
    }

    private fun spawnWindow(
        session: DesktopSession,
        groupId: String,
        state: WindowState,
    ): OpenWindow {
        val w =
            OpenWindow(
                id = UUID.randomUUID().toString(),
                session = session,
                groupId = groupId,
                searchHomeViewModel = searchHomeViewModelFactory(),
                windowState = state,
            )
        _windows.update { it.adding(w) }
        if (window(_focusedWindowId.value) == null) _focusedWindowId.value = w.id
        return w
    }

    private fun removeWindow(w: OpenWindow) {
        if (w !in _windows.value) return
        _windows.update { it.removing(w) }
        focusHistory.remove(w.id)
        w.session.forgetWindow(w.groupId)
        w.dispose()
        if (_focusedWindowId.value == w.id) {
            _focusedWindowId.value =
                _windows.value
                    .firstOrNull()
                    ?.id
                    .orEmpty()
        }
    }

    private fun cascadedFloatingGeometry(saved: SavedGeometry?): SavedGeometry {
        val referenceWindow = focusedWindow()
        val referenceBounds = referenceWindow?.boundsOnScreen()
        val referenceGeometry = referenceWindow?.savedGeometry()

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
    }
}
