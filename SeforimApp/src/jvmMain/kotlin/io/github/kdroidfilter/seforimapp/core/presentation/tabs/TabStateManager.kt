package io.github.kdroidfilter.seforimapp.core.presentation.tabs

/**
 * Manages state persistence across tabs.
 * This class provides a centralized cache for storing and retrieving state
 * associated with specific tabs, allowing state to be preserved when navigating
 * between tabs.
 */
class TabStateManager {
    // Map of tabId to a map of state keys to state values
    private val stateCache = mutableMapOf<String, MutableMap<String, Any>>()

    /**
     * Saves a state value for a specific tab and key.
     *
     * @param tabId The ID of the tab
     * @param key The key to identify the state
     * @param value The state value to save
     */
    fun saveState(tabId: String, key: String, value: Any) {
        val tabState = stateCache.getOrPut(tabId) { mutableMapOf() }
        tabState[key] = value
    }

    /**
     * Retrieves a state value for a specific tab and key.
     *
     * @param tabId The ID of the tab
     * @param key The key to identify the state
     * @return The state value, or null if not found
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getState(tabId: String, key: String): T? {
        return stateCache[tabId]?.get(key) as? T
    }

    /**
     * Copies selected keys from one tab to another.
     * This allows initializing a new tab with the state of an existing tab.
     */
    fun copyKeys(fromTabId: String, toTabId: String, keys: Collection<String>) {
        val source = stateCache[fromTabId] ?: return
        val dest = stateCache.getOrPut(toTabId) { mutableMapOf() }
        keys.forEach { key ->
            source[key]?.let { value -> dest[key] = value }
        }
    }

    /**
     * Clears all state for a specific tab.
     *
     * @param tabId The ID of the tab
     */
    fun clearTabState(tabId: String) {
        stateCache.remove(tabId)
    }

    /**
     * Clears all state for all tabs.
     */
    fun clearAllState() {
        stateCache.clear()
    }
}