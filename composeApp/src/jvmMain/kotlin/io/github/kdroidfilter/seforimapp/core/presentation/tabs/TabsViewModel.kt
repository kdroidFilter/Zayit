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
    val destination: TabsDestination = TabsDestination.Home(UUID.randomUUID().toString())
)

class TabsViewModel(
    private val navigator: Navigator,
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
    }

    private fun selectTab(index: Int) {
        val currentTabs = _tabs.value
        if (index in 0..currentTabs.lastIndex) {
            _selectedTabIndex.value = index
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
    }

    private fun addTabWithDestination(destination: TabsDestination) {
        val newDestination = when (destination) {
            is TabsDestination.Home -> TabsDestination.Home(UUID.randomUUID().toString())
            is TabsDestination.Search -> TabsDestination.Search(destination.searchQuery, UUID.randomUUID().toString())
            is TabsDestination.BookContent -> TabsDestination.BookContent(destination.bookId, UUID.randomUUID().toString(), destination.lineId)
        }

        val newTab = TabItem(
            id = _nextTabId++,
            title = getTabTitle(newDestination),
            destination = newDestination
        )
        _tabs.value = _tabs.value + newTab
        _selectedTabIndex.value = _tabs.value.lastIndex
    }

    private fun getTabTitle(destination: TabsDestination): String {
        return when (destination) {
            is TabsDestination.Home -> "Home"
            is TabsDestination.Search -> "Search: ${destination.searchQuery}"
            is TabsDestination.BookContent -> "Book ${destination.bookId}"
        }
    }
}
