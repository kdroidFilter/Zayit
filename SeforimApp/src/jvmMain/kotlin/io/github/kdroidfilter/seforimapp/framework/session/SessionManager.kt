package io.github.kdroidfilter.seforimapp.framework.session

import io.github.kdroidfilter.seforim.tabs.TabStateManager
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.StateKeys
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.*
import kotlinx.serialization.json.Json

/**
 * Persists and restores the navigation session (open tabs + per-tab state) when enabled in settings.
 */
object SessionManager {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class SavedSession(
        val tabs: List<TabsDestination>,
        val selectedIndex: Int,
        val tabStates: Map<String, Map<String, String>> // tabId -> (stateKey -> encodedJson)
    )

    /** Saves current session if the user enabled persistence in settings. */
    fun saveIfEnabled(appGraph: AppGraph) {
        if (!AppSettings.isPersistSessionEnabled()) return

        val tabsVm: TabsViewModel = appGraph.tabsViewModel
        val tabStateManager: TabStateManager = appGraph.tabStateManager

        val currentTabs = tabsVm.tabs.value
        if (currentTabs.isEmpty()) return

        val destinations = currentTabs.map { it.destination }
        val selectedIndex = tabsVm.selectedTabIndex.value

        val snapshot = tabStateManager.snapshot()
        val encodedStates = snapshot.mapValues { (_, stateMap) ->
            stateMap.mapNotNull { (key, value) ->
                encodeValue(key, value)?.let { encoded -> key to encoded }
            }.toMap()
        }

        val saved = SavedSession(
            tabs = destinations,
            selectedIndex = selectedIndex.coerceIn(0, destinations.lastIndex),
            tabStates = encodedStates
        )

        val jsonStr = json.encodeToString(SavedSession.serializer(), saved)
        AppSettings.setSavedSessionJson(jsonStr)
    }

    /** Restores a saved session if the user enabled persistence in settings. */
    fun restoreIfEnabled(appGraph: AppGraph) {
        if (!AppSettings.isPersistSessionEnabled()) return
        val blob = AppSettings.getSavedSessionJson() ?: return

        val saved = runCatching { json.decodeFromString(SavedSession.serializer(), blob) }.getOrNull() ?: return

        val decodedStates: Map<String, Map<String, Any>> = saved.tabStates.mapValues { (_, stateMap) ->
            stateMap.mapNotNull { (key, encoded) ->
                decodeValue(key, encoded)?.let { decoded -> key to decoded }
            }.toMap()
        }

        // Restore TabStateManager state first, so screens can pick it up when tabs open
        appGraph.tabStateManager.restore(decodedStates)

        // Recreate tabs and selection via navigator/tabs VM
        val tabsVm: TabsViewModel = appGraph.tabsViewModel
        val titleUpdateManager: TabTitleUpdateManager = appGraph.tabTitleUpdateManager

        if (saved.tabs.isEmpty()) return

        runBlocking {
            // Open first tab then close default initial one
            tabsVm.openTab(saved.tabs.first())
        }
        // Close the initial default tab at index 0
        tabsVm.onEvent(TabsEvents.onClose(0))

        // Open remaining tabs
        saved.tabs.drop(1).forEach { dest ->
            runBlocking { tabsVm.openTab(dest) }
        }

        // Update tab titles immediately based on restored state (e.g., Book.title),
        // so users don't see raw IDs before the screen composes.
        saved.tabs.forEach { dest ->
            val tabId = dest.tabId
            (decodedStates[tabId]?.get(StateKeys.SELECTED_BOOK) as? io.github.kdroidfilter.seforimlibrary.core.models.Book)?.let { book ->
                titleUpdateManager.updateTabTitle(tabId, book.title, TabType.BOOK)
            }
        }

        // Select saved index (bounds-safe)
        val targetIndex = saved.selectedIndex.coerceIn(0, saved.tabs.lastIndex)
        tabsVm.onEvent(TabsEvents.onSelected(targetIndex))
    }

    // --- Encoding/Decoding helpers for known state keys ---

    private fun encodeValue(key: String, value: Any): String? = try {
        when (key) {
            // Navigation
            StateKeys.EXPANDED_CATEGORIES -> json.encodeToString(SetSerializer(Long.serializer()), value as Set<Long>)
            // Skip heavy caches that can be recomputed quickly on startup
            StateKeys.CATEGORY_CHILDREN -> null
            StateKeys.BOOKS_IN_CATEGORY -> null
            StateKeys.SELECTED_CATEGORY -> json.encodeToString(Category.serializer().nullable, value as Category?)
            StateKeys.SELECTED_BOOK -> json.encodeToString(Book.serializer().nullable, value as Book?)
            StateKeys.SEARCH_TEXT -> json.encodeToString(String.serializer(), value as String)
            StateKeys.SHOW_BOOK_TREE -> json.encodeToString(Boolean.serializer(), value as Boolean)
            StateKeys.BOOK_TREE_SCROLL_INDEX -> json.encodeToString(Int.serializer(), value as Int)
            StateKeys.BOOK_TREE_SCROLL_OFFSET -> json.encodeToString(Int.serializer(), value as Int)

            // TOC
            StateKeys.EXPANDED_TOC_ENTRIES -> json.encodeToString(SetSerializer(Long.serializer()), value as Set<Long>)
            StateKeys.TOC_CHILDREN -> null
            StateKeys.SHOW_TOC -> json.encodeToString(Boolean.serializer(), value as Boolean)
            StateKeys.TOC_SCROLL_INDEX -> json.encodeToString(Int.serializer(), value as Int)
            StateKeys.TOC_SCROLL_OFFSET -> json.encodeToString(Int.serializer(), value as Int)

            // Content
            StateKeys.SELECTED_LINE -> json.encodeToString(Line.serializer().nullable, value as Line?)
            StateKeys.SHOW_COMMENTARIES -> json.encodeToString(Boolean.serializer(), value as Boolean)
            StateKeys.SHOW_TARGUM -> json.encodeToString(Boolean.serializer(), value as Boolean)
            StateKeys.CONTENT_SCROLL_INDEX -> json.encodeToString(Int.serializer(), value as Int)
            StateKeys.CONTENT_SCROLL_OFFSET -> json.encodeToString(Int.serializer(), value as Int)
            StateKeys.CONTENT_ANCHOR_ID -> json.encodeToString(Long.serializer(), value as Long)
            StateKeys.CONTENT_ANCHOR_INDEX -> json.encodeToString(Int.serializer(), value as Int)
            StateKeys.PARAGRAPH_SCROLL_POSITION -> json.encodeToString(Int.serializer(), value as Int)
            StateKeys.CHAPTER_SCROLL_POSITION -> json.encodeToString(Int.serializer(), value as Int)
            StateKeys.SELECTED_CHAPTER -> json.encodeToString(Int.serializer(), value as Int)

            // Commentaries
            StateKeys.COMMENTARIES_SELECTED_TAB -> json.encodeToString(Int.serializer(), value as Int)
            StateKeys.COMMENTARIES_SCROLL_INDEX -> json.encodeToString(Int.serializer(), value as Int)
            StateKeys.COMMENTARIES_SCROLL_OFFSET -> json.encodeToString(Int.serializer(), value as Int)
            StateKeys.COMMENTATORS_LIST_SCROLL_INDEX -> json.encodeToString(Int.serializer(), value as Int)
            StateKeys.COMMENTATORS_LIST_SCROLL_OFFSET -> json.encodeToString(Int.serializer(), value as Int)
            StateKeys.COMMENTARIES_COLUMN_SCROLL_INDEX_BY_COMMENTATOR -> json.encodeToString(MapSerializer(Long.serializer(), Int.serializer()), value as Map<Long, Int>)
            StateKeys.COMMENTARIES_COLUMN_SCROLL_OFFSET_BY_COMMENTATOR -> json.encodeToString(MapSerializer(Long.serializer(), Int.serializer()), value as Map<Long, Int>)
            StateKeys.SELECTED_COMMENTATORS_BY_LINE -> json.encodeToString(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), value as Map<Long, Set<Long>>)
            StateKeys.SELECTED_COMMENTATORS_BY_BOOK -> json.encodeToString(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), value as Map<Long, Set<Long>>)
            StateKeys.SELECTED_TARGUM_SOURCES_BY_LINE -> json.encodeToString(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), value as Map<Long, Set<Long>>)
            StateKeys.SELECTED_TARGUM_SOURCES_BY_BOOK -> json.encodeToString(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), value as Map<Long, Set<Long>>)

            // Layout
            StateKeys.SPLIT_PANE_POSITION -> json.encodeToString(Float.serializer(), value as Float)
            StateKeys.TOC_SPLIT_PANE_POSITION -> json.encodeToString(Float.serializer(), value as Float)
            StateKeys.CONTENT_SPLIT_PANE_POSITION -> json.encodeToString(Float.serializer(), value as Float)
            StateKeys.TARGUM_SPLIT_PANE_POSITION -> json.encodeToString(Float.serializer(), value as Float)
            StateKeys.PREVIOUS_MAIN_SPLIT_POSITION -> json.encodeToString(Float.serializer(), value as Float)
            StateKeys.PREVIOUS_TOC_SPLIT_POSITION -> json.encodeToString(Float.serializer(), value as Float)
            StateKeys.PREVIOUS_CONTENT_SPLIT_POSITION -> json.encodeToString(Float.serializer(), value as Float)
            StateKeys.PREVIOUS_TARGUM_SPLIT_POSITION -> json.encodeToString(Float.serializer(), value as Float)

            else -> null
        }
    } catch (_: Throwable) {
        null
    }

    private fun decodeValue(key: String, encoded: String): Any? = try {
        when (key) {
            // Navigation
            StateKeys.EXPANDED_CATEGORIES -> json.decodeFromString(SetSerializer(Long.serializer()), encoded)
            StateKeys.CATEGORY_CHILDREN -> null
            StateKeys.BOOKS_IN_CATEGORY -> null
            StateKeys.SELECTED_CATEGORY -> json.decodeFromString(Category.serializer().nullable, encoded)
            StateKeys.SELECTED_BOOK -> json.decodeFromString(Book.serializer().nullable, encoded)
            StateKeys.SEARCH_TEXT -> json.decodeFromString(String.serializer(), encoded)
            StateKeys.SHOW_BOOK_TREE -> json.decodeFromString(Boolean.serializer(), encoded)
            StateKeys.BOOK_TREE_SCROLL_INDEX -> json.decodeFromString(Int.serializer(), encoded)
            StateKeys.BOOK_TREE_SCROLL_OFFSET -> json.decodeFromString(Int.serializer(), encoded)

            // TOC
            StateKeys.EXPANDED_TOC_ENTRIES -> json.decodeFromString(SetSerializer(Long.serializer()), encoded)
            StateKeys.TOC_CHILDREN -> null
            StateKeys.SHOW_TOC -> json.decodeFromString(Boolean.serializer(), encoded)
            StateKeys.TOC_SCROLL_INDEX -> json.decodeFromString(Int.serializer(), encoded)
            StateKeys.TOC_SCROLL_OFFSET -> json.decodeFromString(Int.serializer(), encoded)

            // Content
            StateKeys.SELECTED_LINE -> json.decodeFromString(Line.serializer().nullable, encoded)
            StateKeys.SHOW_COMMENTARIES -> json.decodeFromString(Boolean.serializer(), encoded)
            StateKeys.SHOW_TARGUM -> json.decodeFromString(Boolean.serializer(), encoded)
            StateKeys.CONTENT_SCROLL_INDEX -> json.decodeFromString(Int.serializer(), encoded)
            StateKeys.CONTENT_SCROLL_OFFSET -> json.decodeFromString(Int.serializer(), encoded)
            StateKeys.CONTENT_ANCHOR_ID -> json.decodeFromString(Long.serializer(), encoded)
            StateKeys.CONTENT_ANCHOR_INDEX -> json.decodeFromString(Int.serializer(), encoded)
            StateKeys.PARAGRAPH_SCROLL_POSITION -> json.decodeFromString(Int.serializer(), encoded)
            StateKeys.CHAPTER_SCROLL_POSITION -> json.decodeFromString(Int.serializer(), encoded)
            StateKeys.SELECTED_CHAPTER -> json.decodeFromString(Int.serializer(), encoded)

            // Commentaries
            StateKeys.COMMENTARIES_SELECTED_TAB -> json.decodeFromString(Int.serializer(), encoded)
            StateKeys.COMMENTARIES_SCROLL_INDEX -> json.decodeFromString(Int.serializer(), encoded)
            StateKeys.COMMENTARIES_SCROLL_OFFSET -> json.decodeFromString(Int.serializer(), encoded)
            StateKeys.COMMENTATORS_LIST_SCROLL_INDEX -> json.decodeFromString(Int.serializer(), encoded)
            StateKeys.COMMENTATORS_LIST_SCROLL_OFFSET -> json.decodeFromString(Int.serializer(), encoded)
            StateKeys.COMMENTARIES_COLUMN_SCROLL_INDEX_BY_COMMENTATOR -> json.decodeFromString(MapSerializer(Long.serializer(), Int.serializer()), encoded)
            StateKeys.COMMENTARIES_COLUMN_SCROLL_OFFSET_BY_COMMENTATOR -> json.decodeFromString(MapSerializer(Long.serializer(), Int.serializer()), encoded)
            StateKeys.SELECTED_COMMENTATORS_BY_LINE -> json.decodeFromString(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), encoded)
            StateKeys.SELECTED_COMMENTATORS_BY_BOOK -> json.decodeFromString(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), encoded)
            StateKeys.SELECTED_TARGUM_SOURCES_BY_LINE -> json.decodeFromString(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), encoded)
            StateKeys.SELECTED_TARGUM_SOURCES_BY_BOOK -> json.decodeFromString(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), encoded)

            // Layout
            StateKeys.SPLIT_PANE_POSITION -> json.decodeFromString(Float.serializer(), encoded)
            StateKeys.TOC_SPLIT_PANE_POSITION -> json.decodeFromString(Float.serializer(), encoded)
            StateKeys.CONTENT_SPLIT_PANE_POSITION -> json.decodeFromString(Float.serializer(), encoded)
            StateKeys.TARGUM_SPLIT_PANE_POSITION -> json.decodeFromString(Float.serializer(), encoded)
            StateKeys.PREVIOUS_MAIN_SPLIT_POSITION -> json.decodeFromString(Float.serializer(), encoded)
            StateKeys.PREVIOUS_TOC_SPLIT_POSITION -> json.decodeFromString(Float.serializer(), encoded)
            StateKeys.PREVIOUS_CONTENT_SPLIT_POSITION -> json.decodeFromString(Float.serializer(), encoded)
            StateKeys.PREVIOUS_TARGUM_SPLIT_POSITION -> json.decodeFromString(Float.serializer(), encoded)

            else -> null
        }
    } catch (_: Throwable) {
        null
    }
}
