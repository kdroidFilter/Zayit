// tabs/TabsViewModel.kt
package io.github.kdroidfilter.seforim.tabs

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private var _nextTabId = 2 // Commence à 2 car on a déjà un onglet par défaut
    private val _tabs =
        MutableStateFlow(
            listOf(
                TabItem(
                    id = 1,
                    title = getTabTitle(startDestination),
                    destination = startDestination,
                ),
            ),
        )
    val tabs = _tabs.asStateFlow()

    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex = _selectedTabIndex.asStateFlow()

    init {
        // Écouter les mises à jour de titre
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
        val currentTabs = _tabs.value

        if (index < 0 || index >= currentTabs.size) return // Index invalide

        // If it's the last remaining tab, reset it to a fresh one instead of removing it
        if (currentTabs.size == 1) {
            // Replace the current (and only) tab with a brand‑new tabId and default destination
            // This clears the previous tab state and avoids leaving the UI without any tab
            replaceCurrentTabWithNewTabId(
                TabsDestination.BookContent(bookId = -1, tabId = UUID.randomUUID().toString()),
            )
            _selectedTabIndex.value = 0
            return
        }

        // Capture tabId to clear any per-tab cached state

        // Supprimer l'onglet à l'index donné
        val newTabs = currentTabs.toMutableList().apply { removeAt(index) }
        _tabs.value = newTabs.toList()

        // Ajuster l'index sélectionné
        val currentSelectedIndex = _selectedTabIndex.value
        val newSelectedIndex =
            when {
                // Si on ferme l'onglet sélectionné
                index == currentSelectedIndex -> {
                    // Si on ferme le dernier onglet, sélectionner le précédent
                    if (index == newTabs.size) {
                        max(0, index - 1)
                    } else {
                        // Sinon, garder le même index (qui pointera vers l'onglet suivant)
                        index.coerceIn(0, newTabs.lastIndex)
                    }
                }
                // Si on ferme un onglet avant celui sélectionné
                index < currentSelectedIndex -> currentSelectedIndex - 1
                // Si on ferme un onglet après celui sélectionné
                else -> currentSelectedIndex
            }

        _selectedTabIndex.value = newSelectedIndex
    }

    private fun selectTab(index: Int) {
        val currentTabs = _tabs.value
        if (index in 0..currentTabs.lastIndex && index != _selectedTabIndex.value) {
            _selectedTabIndex.value = index
        }
    }

    private fun reorderTabs(
        fromIndex: Int,
        toIndex: Int,
    ) {
        val currentTabs = _tabs.value
        if (fromIndex !in 0..currentTabs.lastIndex || toIndex !in 0..currentTabs.lastIndex) return
        if (fromIndex == toIndex) return

        val newTabs = currentTabs.toMutableList()
        val movedTab = newTabs.removeAt(fromIndex)
        newTabs.add(toIndex, movedTab)
        _tabs.value = newTabs.toList()

        // Adjust selected index: track which tab was selected by its identity
        val selectedTab = currentTabs.getOrNull(_selectedTabIndex.value)
        if (selectedTab != null) {
            val newSelectedIndex = newTabs.indexOfFirst { it.id == selectedTab.id }
            if (newSelectedIndex != -1) {
                _selectedTabIndex.value = newSelectedIndex
            }
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
        _tabs.value = listOf(newTab) + _tabs.value
        _selectedTabIndex.value = 0
        // Trigger GC when a new tab is opened via the plus button
        System.gc()
    }

    private fun addTabWithDestination(destination: TabsDestination) {
        // Preserve the provided tabId to allow callers to pre-initialize tab state.
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
        _tabs.value = listOf(newTab) + _tabs.value
        _selectedTabIndex.value = 0
    }

    private fun closeAllTabs() {
        val currentTabs = _tabs.value

        // Create a fresh default tab
        val destination = TabsDestination.BookContent(bookId = -1, tabId = UUID.randomUUID().toString())
        val newTab =
            TabItem(
                id = _nextTabId++,
                title = getTabTitle(destination),
                destination = destination,
                tabType = TabType.SEARCH,
            )
        _tabs.value = listOf(newTab)
        _selectedTabIndex.value = 0
        System.gc()
    }

    private fun closeOthers(index: Int) {
        val currentTabs = _tabs.value
        if (index !in 0..currentTabs.lastIndex) return

        val keep = currentTabs[index]
        _tabs.value = listOf(keep)
        _selectedTabIndex.value = 0
        System.gc()
    }

    private fun closeLeft(index: Int) {
        val currentTabs = _tabs.value
        if (index !in 0..currentTabs.lastIndex) return

        val newTabs = currentTabs.drop(index).toList()
        _tabs.value = newTabs

        // Adjust selection: preserve selection if still present; otherwise select the first (which is the original index tab)
        val s = _selectedTabIndex.value
        _selectedTabIndex.value =
            if (s >= index) {
                (s - index).coerceIn(0, newTabs.lastIndex)
            } else {
                0
            }
        System.gc()
    }

    private fun closeRight(index: Int) {
        val currentTabs = _tabs.value
        if (index !in 0..currentTabs.lastIndex) return

        val newTabs = currentTabs.take(index + 1).toList()
        _tabs.value = newTabs

        // Adjust selection: if the previous selected was to the right, clamp to last (which is index)
        val s = _selectedTabIndex.value
        _selectedTabIndex.value = if (s <= index) s else newTabs.lastIndex
        System.gc()
    }

    /**
     * Public API to open a new tab with the given destination.
     * Keeps the provided tabId, selects the new tab.
     */
    fun openTab(destination: TabsDestination) {
        addTabWithDestination(destination)
    }

    /**
     * Replaces the destination of the currently selected tab, preserving the tabId.
     * Does not create a new tab. Updates the tab title accordingly.
     */
    fun replaceCurrentTabDestination(destination: TabsDestination) {
        val index = _selectedTabIndex.value
        val currentTabs = _tabs.value
        if (index !in 0..currentTabs.lastIndex) return

        val current = currentTabs[index]
        val newDestination =
            when (destination) {
                is TabsDestination.Home ->
                    TabsDestination.Home(
                        tabId = current.destination.tabId,
                        version = System.currentTimeMillis(),
                    )
                is TabsDestination.Search ->
                    TabsDestination.Search(
                        searchQuery = destination.searchQuery,
                        tabId = current.destination.tabId,
                    )
                is TabsDestination.BookContent ->
                    TabsDestination.BookContent(
                        bookId = destination.bookId,
                        tabId = current.destination.tabId,
                        lineId = destination.lineId,
                    )
            }

        val updated =
            current.copy(
                title = getTabTitle(newDestination),
                destination = newDestination,
                tabType = tabTypeFor(newDestination),
            )
        _tabs.value = currentTabs.toMutableList().apply { set(index, updated) }.toList()
    }

    /**
     * Replaces the currently selected tab with a fresh tabId, similar to opening
     * a brand-new tab but keeping the same visual slot. Clears the previous tabId
     * state to avoid leaking state into the new content.
     */
    fun replaceCurrentTabWithNewTabId(destination: TabsDestination) {
        val index = _selectedTabIndex.value
        val currentTabs = _tabs.value
        if (index !in 0..currentTabs.lastIndex) return

        val current = currentTabs[index]
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
            current.copy(
                title = getTabTitle(newDestination),
                destination = newDestination,
                tabType = tabTypeFor(newDestination),
            )
        _tabs.value = currentTabs.toMutableList().apply { set(index, updated) }.toList()
    }

    /**
     * Replaces the entire tab list with the provided destinations.
     * Used for cold-boot session restore to avoid keeping the default tab alive.
     */
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

        _tabs.value = restoredTabs
        _selectedTabIndex.value = selectedIndex.coerceIn(0, restoredTabs.lastIndex)
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
            // For Home, return empty so UI can localize via resources
            is TabsDestination.Home -> ""
            is TabsDestination.Search -> destination.searchQuery
            is TabsDestination.BookContent -> if (destination.bookId > 0) "${destination.bookId}" else ""
        }

    /**
     * Updates the title of a tab with the given tabId.
     *
     * @param tabId The ID of the tab to update
     * @param newTitle The new title for the tab
     * @param tabType The type of content in the tab
     */
    private fun updateTabTitle(
        tabId: String,
        newTitle: String,
        tabType: TabType = TabType.SEARCH,
    ) {
        val currentTabs = _tabs.value
        val updatedTabs =
            currentTabs.map { tab ->
                if (tab.destination.tabId == tabId) {
                    tab.copy(title = newTitle, tabType = tabType)
                } else {
                    tab
                }
            }

        // Only update if there was a change
        if (updatedTabs != currentTabs) {
            _tabs.value = updatedTabs
        }
    }
}
