// tabs/TabsViewModel.kt
package io.github.kdroidfilter.seforim.tabs

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.max

@Immutable
data class TabItem(
    val id: Int,
    val title: String = "Default Tab",
    val destination: TabsDestination = TabsDestination.Home(UUID.randomUUID().toString()),
    val tabType: TabType = TabType.SEARCH,
)

@Stable
class TabsViewModel(
    private val titleUpdateManager: TabTitleUpdateManager,
    startDestination: TabsDestination,
) : ViewModel() {
    private var _nextTabId = 2

    private val _state =
        MutableStateFlow(
            TabsState(
                tabs =
                    listOf(
                        TabItem(
                            id = 1,
                            title = getTabTitle(startDestination),
                            destination = startDestination,
                        ),
                    ),
                selectedTabIndex = 0,
            ),
        )
    val state: StateFlow<TabsState> = _state.asStateFlow()

    // Backward-compatible derived flows for consumers that only need one field
    val tabs: StateFlow<List<TabItem>> =
        _state
            .map { it.tabs }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, _state.value.tabs)

    val selectedTabIndex: StateFlow<Int> =
        _state
            .map { it.selectedTabIndex }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, _state.value.selectedTabIndex)

    init {
        viewModelScope.launch {
            titleUpdateManager.titleUpdates.collect { update ->
                updateTabTitle(update.tabId, update.newTitle, update.tabType)
            }
        }
    }

    fun onEvent(event: TabsEvents) {
        when (event) {
            is TabsEvents.OnClose -> closeTab(event.index)
            is TabsEvents.OnSelect -> selectTab(event.index)
            TabsEvents.OnAdd -> addTab()
            is TabsEvents.OnReorder -> reorderTabs(event.fromIndex, event.toIndex)
            TabsEvents.CloseAll -> closeAllTabs()
            is TabsEvents.CloseOthers -> closeOthers(event.index)
            is TabsEvents.CloseLeft -> closeLeft(event.index)
            is TabsEvents.CloseRight -> closeRight(event.index)
        }
    }

    private fun closeTab(index: Int) {
        val currentState = _state.value
        val currentTabs = currentState.tabs

        if (index < 0 || index >= currentTabs.size) return

        if (currentTabs.size == 1) {
            replaceCurrentTabWithNewTabId(
                TabsDestination.BookContent(bookId = -1, tabId = UUID.randomUUID().toString()),
            )
            _state.update { it.copy(selectedTabIndex = 0) }
            return
        }

        val newTabs = currentTabs.toMutableList().apply { removeAt(index) }
        val currentSelectedIndex = currentState.selectedTabIndex
        val newSelectedIndex =
            when {
                index == currentSelectedIndex -> {
                    if (index == newTabs.size) {
                        max(0, index - 1)
                    } else {
                        index.coerceIn(0, newTabs.lastIndex)
                    }
                }
                index < currentSelectedIndex -> currentSelectedIndex - 1
                else -> currentSelectedIndex
            }

        _state.value = TabsState(tabs = newTabs.toList(), selectedTabIndex = newSelectedIndex)
    }

    private fun selectTab(index: Int) {
        _state.update { current ->
            if (index in 0..current.tabs.lastIndex && index != current.selectedTabIndex) {
                current.copy(selectedTabIndex = index)
            } else {
                current
            }
        }
    }

    private fun reorderTabs(
        fromIndex: Int,
        toIndex: Int,
    ) {
        _state.update { current ->
            val currentTabs = current.tabs
            if (fromIndex !in 0..currentTabs.lastIndex || toIndex !in 0..currentTabs.lastIndex) return@update current
            if (fromIndex == toIndex) return@update current

            val newTabs = currentTabs.toMutableList()
            val movedTab = newTabs.removeAt(fromIndex)
            newTabs.add(toIndex, movedTab)

            val selectedTab = currentTabs.getOrNull(current.selectedTabIndex)
            val newSelectedIndex =
                if (selectedTab != null) {
                    newTabs.indexOfFirst { it.id == selectedTab.id }.takeIf { it != -1 } ?: current.selectedTabIndex
                } else {
                    current.selectedTabIndex
                }

            TabsState(tabs = newTabs.toList(), selectedTabIndex = newSelectedIndex)
        }
    }

    private fun addTab() {
        val destination = TabsDestination.BookContent(bookId = -1, tabId = UUID.randomUUID().toString())
        val newTab =
            TabItem(
                id = _nextTabId++,
                title = getTabTitle(destination),
                destination = destination,
                tabType = tabTypeFor(destination),
            )
        _state.update { current ->
            TabsState(tabs = listOf(newTab) + current.tabs, selectedTabIndex = 0)
        }
        System.gc()
    }

    private fun addTabWithDestination(destination: TabsDestination) {
        val newDestination =
            when (destination) {
                is TabsDestination.Home -> TabsDestination.Home(destination.tabId, destination.version)
                is TabsDestination.Search -> TabsDestination.Search(destination.searchQuery, destination.tabId)
                is TabsDestination.BookContent -> TabsDestination.BookContent(destination.bookId, destination.tabId, destination.lineId)
            }

        val newTab =
            TabItem(
                id = _nextTabId++,
                title = getTabTitle(newDestination),
                destination = newDestination,
                tabType = tabTypeFor(newDestination),
            )
        _state.update { current ->
            TabsState(tabs = listOf(newTab) + current.tabs, selectedTabIndex = 0)
        }
    }

    private fun closeAllTabs() {
        val destination = TabsDestination.BookContent(bookId = -1, tabId = UUID.randomUUID().toString())
        val newTab =
            TabItem(
                id = _nextTabId++,
                title = getTabTitle(destination),
                destination = destination,
                tabType = TabType.SEARCH,
            )
        _state.value = TabsState(tabs = listOf(newTab), selectedTabIndex = 0)
        System.gc()
    }

    private fun closeOthers(index: Int) {
        _state.update { current ->
            if (index !in 0..current.tabs.lastIndex) return@update current
            TabsState(tabs = listOf(current.tabs[index]), selectedTabIndex = 0)
        }
        System.gc()
    }

    private fun closeLeft(index: Int) {
        _state.update { current ->
            if (index !in 0..current.tabs.lastIndex) return@update current
            val newTabs = current.tabs.drop(index)
            val newSelected =
                if (current.selectedTabIndex >= index) {
                    (current.selectedTabIndex - index).coerceIn(0, newTabs.lastIndex)
                } else {
                    0
                }
            TabsState(tabs = newTabs, selectedTabIndex = newSelected)
        }
        System.gc()
    }

    private fun closeRight(index: Int) {
        _state.update { current ->
            if (index !in 0..current.tabs.lastIndex) return@update current
            val newTabs = current.tabs.take(index + 1)
            val newSelected = if (current.selectedTabIndex <= index) current.selectedTabIndex else newTabs.lastIndex
            TabsState(tabs = newTabs, selectedTabIndex = newSelected)
        }
        System.gc()
    }

    fun openTab(destination: TabsDestination) {
        addTabWithDestination(destination)
    }

    fun replaceCurrentTabDestination(destination: TabsDestination) {
        _state.update { current ->
            val index = current.selectedTabIndex
            if (index !in 0..current.tabs.lastIndex) return@update current

            val tab = current.tabs[index]
            val newDestination =
                when (destination) {
                    is TabsDestination.Home ->
                        TabsDestination.Home(
                            tabId = tab.destination.tabId,
                            version = System.currentTimeMillis(),
                        )
                    is TabsDestination.Search ->
                        TabsDestination.Search(
                            searchQuery = destination.searchQuery,
                            tabId = tab.destination.tabId,
                        )
                    is TabsDestination.BookContent ->
                        TabsDestination.BookContent(
                            bookId = destination.bookId,
                            tabId = tab.destination.tabId,
                            lineId = destination.lineId,
                        )
                }

            val updated =
                tab.copy(
                    title = getTabTitle(newDestination),
                    destination = newDestination,
                    tabType = tabTypeFor(newDestination),
                )
            current.copy(
                tabs =
                    current.tabs
                        .toMutableList()
                        .apply { set(index, updated) }
                        .toList(),
            )
        }
    }

    fun replaceCurrentTabWithNewTabId(destination: TabsDestination) {
        _state.update { current ->
            val index = current.selectedTabIndex
            if (index !in 0..current.tabs.lastIndex) return@update current

            val newTabId = UUID.randomUUID().toString()
            val newDestination =
                when (destination) {
                    is TabsDestination.Home ->
                        TabsDestination.Home(
                            tabId = newTabId,
                            version = System.currentTimeMillis(),
                        )
                    is TabsDestination.Search ->
                        TabsDestination.Search(
                            searchQuery = destination.searchQuery,
                            tabId = newTabId,
                        )
                    is TabsDestination.BookContent ->
                        TabsDestination.BookContent(
                            bookId = destination.bookId,
                            tabId = newTabId,
                            lineId = destination.lineId,
                        )
                }

            val updated =
                current.tabs[index].copy(
                    title = getTabTitle(newDestination),
                    destination = newDestination,
                    tabType = tabTypeFor(newDestination),
                )
            current.copy(
                tabs =
                    current.tabs
                        .toMutableList()
                        .apply { set(index, updated) }
                        .toList(),
            )
        }
    }

    fun restoreTabs(
        destinations: List<TabsDestination>,
        selectedIndex: Int,
    ) {
        if (destinations.isEmpty()) return

        val restoredTabs =
            destinations.mapIndexed { index, destination ->
                TabItem(
                    id = index + 1,
                    title = getTabTitle(destination),
                    destination = destination,
                    tabType = tabTypeFor(destination),
                )
            }

        _state.value =
            TabsState(
                tabs = restoredTabs,
                selectedTabIndex = selectedIndex.coerceIn(0, restoredTabs.lastIndex),
            )
        _nextTabId = (restoredTabs.maxOfOrNull { it.id } ?: 0) + 1
    }

    private fun tabTypeFor(destination: TabsDestination): TabType =
        when (destination) {
            is TabsDestination.Home -> TabType.SEARCH
            is TabsDestination.Search -> TabType.SEARCH
            is TabsDestination.BookContent -> if (destination.bookId > 0) TabType.BOOK else TabType.SEARCH
        }

    private fun getTabTitle(destination: TabsDestination): String =
        when (destination) {
            is TabsDestination.Home -> ""
            is TabsDestination.Search -> destination.searchQuery
            is TabsDestination.BookContent -> if (destination.bookId > 0) "${destination.bookId}" else ""
        }

    private fun updateTabTitle(
        tabId: String,
        newTitle: String,
        tabType: TabType = TabType.SEARCH,
    ) {
        _state.update { current ->
            val updatedTabs =
                current.tabs.map { tab ->
                    if (tab.destination.tabId == tabId) {
                        tab.copy(title = newTitle, tabType = tabType)
                    } else {
                        tab
                    }
                }
            if (updatedTabs != current.tabs) current.copy(tabs = updatedTabs) else current
        }
    }
}
