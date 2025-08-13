// tabs/TabsViewModel.kt
package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforimapp.core.presentation.navigation.Navigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max

data class TabItem(
    val id: Int,
    val title: String = "Default Tab",
    val destination: TabsDestination = TabsDestination.Home(UUID.randomUUID().toString()),
    val tabType: TabType = TabType.SEARCH
)

class TabsViewModel(
    private val navigator: Navigator,
    private val titleUpdateManager: TabTitleUpdateManager,
    private val stateManager: TabStateManager
) : ViewModel() {

    private var _nextTabId = 2 // Commence à 2 car on a déjà un onglet par défaut
    private val _tabs = MutableStateFlow(listOf(
        TabItem(
            id = 1,
            title = getTabTitle(navigator.startDestination),
            destination = navigator.startDestination
        )
    ))
    val tabs = _tabs.asStateFlow()

    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex = _selectedTabIndex.asStateFlow()

    init {
        // Écouter les requêtes de navigation
        viewModelScope.launch {
            navigator.navigationRequests.collect { destination ->
                addTabWithDestination(destination)
            }
        }
        
        // Écouter les mises à jour de titre
        viewModelScope.launch {
            titleUpdateManager.titleUpdates.collect { update ->
                updateTabTitle(update.tabId, update.newTitle, update.tabType)
            }
        }
    }

    fun onEvent(event: TabsEvents) {
        when (event) {
            is TabsEvents.onClose -> closeTab(event.index)
            is TabsEvents.onSelected -> selectTab(event.index)
            TabsEvents.onAdd -> addTab()
        }
    }

    private fun closeTab(index: Int) {
        val currentTabs = _tabs.value
        if (currentTabs.size <= 1) return // Ne pas fermer le dernier onglet

        if (index < 0 || index >= currentTabs.size) return // Index invalide

        // Capture tabId to clear any per-tab cached state
        val tabIdToClose = currentTabs[index].destination.tabId
        // Clear any state associated with this tab to free memory
        stateManager.clearTabState(tabIdToClose)

        // Supprimer l'onglet à l'index donné
        val newTabs = currentTabs.toMutableList().apply { removeAt(index) }
        _tabs.value = newTabs

        // Ajuster l'index sélectionné
        val currentSelectedIndex = _selectedTabIndex.value
        val newSelectedIndex = when {
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

        // Encourage memory reclamation after closing a tab
        System.gc()
    }

    private fun selectTab(index: Int) {
        val currentTabs = _tabs.value
        if (index in 0..currentTabs.lastIndex && index != _selectedTabIndex.value) {
            _selectedTabIndex.value = index
            // Trigger GC when switching tabs
            System.gc()
        }
    }

    private fun addTab() {
        val newTab = TabItem(
            id = _nextTabId++,
            title = "New Tab",
            destination = TabsDestination.Home(UUID.randomUUID().toString())
        )
        _tabs.value = _tabs.value + newTab
        _selectedTabIndex.value = _tabs.value.lastIndex
        // Trigger GC when a new tab is opened via the plus button
        System.gc()
    }

    private fun addTabWithDestination(destination: TabsDestination) {
        // Preserve the provided tabId to allow callers to pre-initialize tab state (e.g., via TabStateManager).
        val newDestination = when (destination) {
            is TabsDestination.Home -> TabsDestination.Home(destination.tabId)
            is TabsDestination.Search -> TabsDestination.Search(destination.searchQuery, destination.tabId)
            is TabsDestination.BookContent -> TabsDestination.BookContent(destination.bookId, destination.tabId, destination.lineId)
        }

        val newTab = TabItem(
            id = _nextTabId++,
            title = getTabTitle(newDestination),
            destination = newDestination
        )
        _tabs.value = _tabs.value + newTab
        _selectedTabIndex.value = _tabs.value.lastIndex
        // Trigger GC when a new tab is opened via navigation
        System.gc()
    }

    private fun getTabTitle(destination: TabsDestination): String {
        return when (destination) {
            is TabsDestination.Home -> "Home"
            is TabsDestination.Search -> destination.searchQuery
            is TabsDestination.BookContent -> "${destination.bookId}"
        }
    }
    
    /**
     * Updates the title of a tab with the given tabId.
     *
     * @param tabId The ID of the tab to update
     * @param newTitle The new title for the tab
     * @param tabType The type of content in the tab
     */
    private fun updateTabTitle(tabId: String, newTitle: String, tabType: TabType = TabType.SEARCH) {
        val currentTabs = _tabs.value
        val updatedTabs = currentTabs.map { tab ->
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
