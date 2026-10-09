@file:OptIn(ExperimentalNucleusApi::class)

package io.github.kdroidfilter.seforim.tabs

import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import dev.nucleusframework.window.ExperimentalNucleusApi
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopSession
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The tabs of one window, as the rest of the app sees them.
 *
 * The desktop's `TabWorkspace` owns which tabs this window holds, their order and the selection
 * (the stock strip drives it directly: click, ×, drag, tear-off); this class mirrors that into
 * [state] and turns the app's navigation calls into workspace operations. Every call must happen on
 * the UI thread, like every workspace call.
 */
@Stable
class TabsViewModel internal constructor(
    private val sessionOf: () -> DesktopSession,
    private val groupIdOf: () -> String,
) {
    private val session: DesktopSession get() = sessionOf()
    private val groupId: String get() = groupIdOf()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(compute())
    val state: StateFlow<TabsState> = _state.asStateFlow()

    val tabs: StateFlow<List<TabItem>> =
        _state.map { it.tabs }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, _state.value.tabs)

    val selectedTabIndex: StateFlow<Int> =
        _state.map { it.selectedTabIndex }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, _state.value.selectedTabIndex)

    /** The tabs a window opens with are shown at once, not animated in (reset by the strip). */
    private val _skipNextAnimation = MutableStateFlow(true)
    val skipNextAnimation: StateFlow<Boolean> = _skipNextAnimation.asStateFlow()

    /** True while the hint asking for a second Ctrl+W on a pinned tab is shown. */
    private val _closePinnedHint = MutableStateFlow(false)
    val closePinnedHint: StateFlow<Boolean> = _closePinnedHint.asStateFlow()
    private var hintJob: Job? = null

    // The pinned tab the hint was shown for: a second press only closes that one.
    private var hintedTabId: String? = null

    fun consumeSkipAnimation() {
        _skipNextAnimation.value = false
    }

    init {
        mirror(scope)
    }

    private fun mirror(
        @StructuredScope scope: CoroutineScope,
    ) {
        scope.launch { snapshotFlow { compute() }.collect { _state.value = it } }
    }

    private fun ids(): List<String> = session.group(groupId)?.ids.orEmpty()

    private fun compute(): TabsState {
        val group = session.group(groupId) ?: return TabsState(emptyList(), 0)
        val ids = group.ids
        return TabsState(
            tabs = ids.mapNotNull(session::item),
            selectedTabIndex = ids.indexOf(group.selectedId).coerceAtLeast(0),
        )
    }

    private fun currentId(): String? = session.group(groupId)?.selectedId

    fun onEvent(event: TabsEvents) {
        val ids = ids()
        when (event) {
            is TabsEvents.OnClose -> ids.getOrNull(event.index)?.let(session.workspace::close)
            is TabsEvents.OnSelect -> ids.getOrNull(event.index)?.let(session.workspace::select)
            TabsEvents.OnAdd -> session.addTab(freshHome(), groupId, index = 0)
            is TabsEvents.OnReorder -> ids.getOrNull(event.fromIndex)?.let { session.workspace.reorder(it, event.toIndex) }
            // Pinned tabs stay. Chrome-like: with none, the window keeps one fresh Home tab, which
            // replaces the others once it is in.
            TabsEvents.CloseAll -> {
                if (ids.any(session::isPinned)) {
                    ids.filterNot(session::isPinned).forEach(session.workspace::close)
                } else {
                    val home = freshHome()
                    session.addTab(home, groupId, index = 0)
                    closeWhenDeclared(home.tabId, ids)
                }
            }
            is TabsEvents.CloseOthers -> ids.filterIndexed { i, _ -> i != event.index }.closeUnpinned()
            is TabsEvents.CloseLeft -> ids.take(event.index).closeUnpinned()
            is TabsEvents.CloseRight -> ids.drop(event.index + 1).closeUnpinned()
        }
    }

    // Chromium's GetTabsClosedByCommand: the bulk closes skip pinned tabs.
    private fun List<String>.closeUnpinned() = filterNot(session::isPinned).forEach(session.workspace::close)

    fun setPinned(
        index: Int,
        pinned: Boolean,
    ) {
        ids().getOrNull(index)?.let { session.setPinned(it, pinned) }
    }

    /**
     * Ctrl+W (or the menu's Close Tab) on the selected tab. Chromium-like, a pinned tab only
     * closes on a second press while the hint the first one showed is still up. A [repeat] of a
     * held key closes the unpinned ones in a row but is never that second press.
     */
    fun closeSelectedTab(repeat: Boolean = false) {
        val tabId = currentId() ?: return
        if (session.isPinned(tabId)) {
            val hinted = _closePinnedHint.value && hintedTabId == tabId
            if (!hinted) showClosePinnedHint(tabId)
            if (!hinted || repeat) return
        }
        hideClosePinnedHint()
        session.workspace.close(tabId)
    }

    private fun showClosePinnedHint(
        tabId: String,
        @StructuredScope scope: CoroutineScope = this.scope,
    ) {
        hintJob?.cancel()
        hintedTabId = tabId
        _closePinnedHint.value = true
        hintJob =
            scope.launch {
                delay(CLOSE_PINNED_HINT_MS)
                _closePinnedHint.value = false
            }
    }

    private fun hideClosePinnedHint() {
        hintJob?.cancel()
        _closePinnedHint.value = false
    }

    private fun closeWhenDeclared(
        tabId: String,
        others: List<String>,
        @StructuredScope scope: CoroutineScope = this.scope,
    ) {
        scope.launch {
            snapshotFlow { session.workspace.tab(tabId) != null }.first { it }
            others.forEach(session.workspace::close)
        }
    }

    /** A tab opened from another one lands next to it, on the + side of the strip (the current one moves over). */
    fun openTab(destination: TabsDestination) {
        session.addTab(destination, groupId, index = ids().indexOf(currentId()).coerceAtLeast(0))
    }

    /** Navigates the selected tab to [destination], keeping the tab (and its ViewModels). */
    fun replaceCurrentTabDestination(destination: TabsDestination) {
        val tabId = currentId() ?: return
        session.updateDestination(tabId, destination.withTabId(tabId))
    }

    /** Swaps the selected tab for a new one (fresh ViewModels) at the same place. */
    fun replaceCurrentTabWithNewTabId(destination: TabsDestination) {
        val tabId = currentId() ?: return
        val index = ids().indexOf(tabId).coerceAtLeast(0)
        // The pin goes with the tab, as through any navigation (Chrome).
        session.addTab(
            destination.withTabId(UUID.randomUUID().toString()),
            groupId,
            index = index,
            replacing = tabId,
            pinned = session.isPinned(tabId),
        )
    }

    fun dispose() {
        scope.cancel()
    }

    private fun freshHome(): TabsDestination = TabsDestination.BookContent(bookId = -1, tabId = UUID.randomUUID().toString())

    private companion object {
        const val CLOSE_PINNED_HINT_MS = 3000L
    }
}

internal fun TabsDestination.withTabId(tabId: String): TabsDestination =
    when (this) {
        is TabsDestination.Home -> TabsDestination.Home(tabId = tabId, version = System.currentTimeMillis())
        is TabsDestination.Search -> copy(tabId = tabId)
        is TabsDestination.BookContent -> copy(tabId = tabId)
        is TabsDestination.History -> copy(tabId = tabId)
        is TabsDestination.Favorites -> copy(tabId = tabId)
        is TabsDestination.Notes -> copy(tabId = tabId)
        is TabsDestination.Siddur -> copy(tabId = tabId)
        is TabsDestination.Author -> copy(tabId = tabId)
    }
