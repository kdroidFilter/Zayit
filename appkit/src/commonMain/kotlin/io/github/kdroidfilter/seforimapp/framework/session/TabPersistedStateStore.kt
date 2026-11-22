package io.github.kdroidfilter.seforimapp.framework.session

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * In-memory store for per-tab persisted UI state.
 *
 * This is the single source of truth for what will be serialized on disk at app close.
 * It is updated continuously by screen state managers / viewmodels, so SessionManager
 * does not need to know about individual keys.
 */
class TabPersistedStateStore {
    private val lock = SynchronizedObject()
    private val states = HashMap<String, TabPersistedState>()

    fun get(tabId: String): TabPersistedState? = synchronized(lock) { states[tabId] }

    fun getOrCreate(tabId: String): TabPersistedState = synchronized(lock) { states.getOrPut(tabId) { TabPersistedState() } }

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
        synchronized(lock) { states[tabId] = transform(states[tabId] ?: TabPersistedState()) }
    }

    fun remove(tabId: String) {
        synchronized(lock) { states.remove(tabId) }
    }

    fun removeAll(tabIds: Collection<String>) {
        synchronized(lock) { tabIds.forEach { states.remove(it) } }
    }

    /** Loads a desktop's saved states without touching entries of other open desktops. */
    fun putAll(snapshot: Map<String, TabPersistedState>) {
        synchronized(lock) { states.putAll(snapshot) }
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
