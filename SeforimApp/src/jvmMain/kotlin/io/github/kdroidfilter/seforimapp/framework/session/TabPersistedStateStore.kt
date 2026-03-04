package io.github.kdroidfilter.seforimapp.framework.session

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory store for per-tab persisted UI state.
 *
 * This is the single source of truth for what will be serialized on disk at app close.
 * It is updated continuously by screen state managers / viewmodels, so SessionManager
 * does not need to know about individual keys.
 */
class TabPersistedStateStore {
    private val states = ConcurrentHashMap<String, TabPersistedState>()

    fun get(tabId: String): TabPersistedState? = states[tabId]

    fun getOrCreate(tabId: String): TabPersistedState = states.getOrPut(tabId) { TabPersistedState() }

    fun set(
        tabId: String,
        state: TabPersistedState,
    ) {
        states[tabId] = state
    }

    fun update(
        tabId: String,
        transform: (TabPersistedState) -> TabPersistedState,
    ) {
        states.compute(tabId) { _, current ->
            transform(current ?: TabPersistedState())
        }
    }

    fun remove(tabId: String) {
        states.remove(tabId)
    }

    fun clearAll() {
        states.clear()
    }

    fun snapshot(): Map<String, TabPersistedState> = states.toMap()

    fun restore(snapshot: Map<String, TabPersistedState>) {
        synchronized(states) {
            states.clear()
            states.putAll(snapshot)
        }
    }
}
