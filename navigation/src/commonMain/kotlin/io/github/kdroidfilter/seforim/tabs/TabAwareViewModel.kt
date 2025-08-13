package io.github.kdroidfilter.seforim.tabs

import androidx.lifecycle.ViewModel

/**
 * Base ViewModel class that is aware of the tab it belongs to and can
 * save/restore state using the TabStateManager.
 *
 * @param tabId The ID of the tab this ViewModel is associated with
 * @param stateManager The TabStateManager to use for state persistence
 */
abstract class TabAwareViewModel(
    private val tabId: String,
    private val stateManager: TabStateManager
) : ViewModel() {

    /**
     * Saves a state value with the given key.
     *
     * @param key The key to identify the state
     * @param value The state value to save
     */
    protected fun saveState(key: String, value: Any) {
        stateManager.saveState(tabId, key, value)
    }

    /**
     * Retrieves a state value for the given key.
     *
     * @param key The key to identify the state
     * @return The state value, or null if not found
     */
    protected fun <T> getState(key: String): T? {
        return stateManager.getState(tabId, key)
    }

    /**
     * Clears all state for this tab.
     */
    protected fun clearTabState() {
        stateManager.clearTabState(tabId)
    }

    /**
     * Called when the ViewModel is cleared.
     * Override this method to perform cleanup operations.
     */
    override fun onCleared() {
        super.onCleared()
        // Note: We don't clear the tab state here because we want to preserve it
        // even when the ViewModel is cleared during navigation
    }
}