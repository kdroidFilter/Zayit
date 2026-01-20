package io.github.kdroidfilter.seforimapp.framework.session

/**
 * In-memory store for per-tab persisted UI state.
 *
 * This is the single source of truth for what will be serialized on disk at app close.
 * It is updated continuously by screen state managers / viewmodels, so SessionManager
 * does not need to know about individual keys.
 */
class TabPersistedStateStore {
    private val lock = Any()
    private val states: MutableMap<String, TabPersistedState> = mutableMapOf()

    fun get(tabId: String): TabPersistedState? = synchronized(lock) { states[tabId] }

    fun getOrCreate(tabId: String): TabPersistedState =
        synchronized(lock) {
            states.getOrPut(tabId) { TabPersistedState() }
        }

    fun set(
        tabId: String,
        state: TabPersistedState,
    ) {
        synchronized(lock) { states[tabId] = state }
    }

    fun update(
        tabId: String,
        transform: (TabPersistedState) -> TabPersistedState,
    ) {
        synchronized(lock) {
            val current = states[tabId] ?: TabPersistedState()
            states[tabId] = transform(current)
        }
    }

    fun remove(tabId: String) {
        synchronized(lock) { states.remove(tabId) }
    }

    fun clearAll() {
        synchronized(lock) { states.clear() }
    }

    fun snapshot(): Map<String, TabPersistedState> = synchronized(lock) { states.toMap() }

    fun restore(snapshot: Map<String, TabPersistedState>) {
        synchronized(lock) {
            states.clear()
            states.putAll(snapshot)
        }
    }
}
