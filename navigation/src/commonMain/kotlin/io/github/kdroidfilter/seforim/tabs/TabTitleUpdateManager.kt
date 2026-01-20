package io.github.kdroidfilter.seforim.tabs

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Enum representing the type of tab content
 */
enum class TabType {
    BOOK,
    SEARCH,
}

/**
 * Data class representing a tab title update event
 *
 * @param tabId The ID of the tab to update
 * @param newTitle The new title for the tab
 * @param tabType The type of content in the tab
 */
data class TabTitleUpdate(
    val tabId: String,
    val newTitle: String,
    val tabType: TabType = TabType.SEARCH,
)

/**
 * Manager for updating tab titles across the application.
 * This class provides a communication channel between ViewModels
 * to update tab titles reactively.
 */
class TabTitleUpdateManager {
    private val _titleUpdates = MutableSharedFlow<TabTitleUpdate>(extraBufferCapacity = 10)
    val titleUpdates: SharedFlow<TabTitleUpdate> = _titleUpdates.asSharedFlow()

    /**
     * Updates the title of a tab.
     *
     * @param tabId The ID of the tab to update
     * @param newTitle The new title for the tab
     * @param tabType The type of content in the tab
     * @return true if the update was successful, false otherwise
     */
    fun updateTabTitle(
        tabId: String,
        newTitle: String,
        tabType: TabType = TabType.SEARCH,
    ): Boolean = _titleUpdates.tryEmit(TabTitleUpdate(tabId, newTitle, tabType))
}
