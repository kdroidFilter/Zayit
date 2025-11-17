@file:OptIn(ExperimentalSerializationApi::class)

package io.github.kdroidfilter.seforimapp.framework.session

import io.github.kdroidfilter.seforim.tabs.*
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.StateKeys
import io.github.kdroidfilter.seforimapp.features.search.SearchStateKeys
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.*

/**
 * Persists and restores the navigation session (open tabs + per-tab state) when enabled in settings.
 */
object SessionManager {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val proto = ProtoBuf

    @Serializable
    private data class SavedSession(
        val tabs: List<TabsDestination>,
        val selectedIndex: Int,
        val tabStates: Map<String, Map<String, String>> // tabId -> (stateKey -> base64(proto) or legacy json)
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

        // Persist as ProtoBuf bytes (base64, chunked in settings for size limits)
        val bytes = proto.encodeToByteArray(SavedSession.serializer(), saved)
        val b64 = Base64.getEncoder().encodeToString(bytes)
        AppSettings.setSavedSessionJson(b64)
    }

    /** Restores a saved session if the user enabled persistence in settings. */
    fun restoreIfEnabled(appGraph: AppGraph) {
        if (!AppSettings.isPersistSessionEnabled()) return
        val blob = AppSettings.getSavedSessionJson() ?: return

        // Try to decode as base64(ProtoBuf) first, then legacy JSON fallback
        val savedFromProto = runCatching {
            val bytes = Base64.getDecoder().decode(blob)
            proto.decodeFromByteArray(SavedSession.serializer(), bytes)
        }.getOrNull()
        val saved = savedFromProto ?: runCatching {
            json.decodeFromString(SavedSession.serializer(), blob)
        }.getOrNull() ?: return
        if (savedFromProto == null) {
            // Migrate legacy JSON session to ProtoBuf for next time
            runCatching {
                val bytes = proto.encodeToByteArray(SavedSession.serializer(), saved)
                val b64 = Base64.getEncoder().encodeToString(bytes)
                AppSettings.setSavedSessionJson(b64)
            }
        }

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
        val targetIndex = saved.selectedIndex.coerceIn(0, saved.tabs.lastIndex)

        // Replace current tabs with the restored destinations in one shot
        tabsVm.restoreTabs(saved.tabs, targetIndex)

        // Update tab titles immediately based on restored state (e.g., Book.title),
        // so users don't see raw IDs before the screen composes.
        saved.tabs.forEach { dest ->
            val tabId = dest.tabId
            (decodedStates[tabId]?.get(StateKeys.SELECTED_BOOK) as? io.github.kdroidfilter.seforimlibrary.core.models.Book)?.let { book ->
                titleUpdateManager.updateTabTitle(tabId, book.title, TabType.BOOK)
            }
        }

        // Selected index already applied in restoreTabs()
    }

    // --- Encoding/Decoding helpers for known state keys ---

    private fun encodeValue(key: String, value: Any): String? = try {
        when (key) {
            // Navigation
            StateKeys.EXPANDED_CATEGORIES -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(SetSerializer(Long.serializer()), value as Set<Long>))
            // Skip heavy caches that can be recomputed quickly on startup
            StateKeys.CATEGORY_CHILDREN -> null
            StateKeys.BOOKS_IN_CATEGORY -> null
            StateKeys.SELECTED_CATEGORY -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Category.serializer().nullable, value as Category?))
            StateKeys.SELECTED_BOOK -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Book.serializer().nullable, value as Book?))
            StateKeys.SEARCH_TEXT -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(String.serializer(), value as String))
            StateKeys.SHOW_BOOK_TREE -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Boolean.serializer(), value as Boolean))
            StateKeys.BOOK_TREE_SCROLL_INDEX -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Int.serializer(), value as Int))
            StateKeys.BOOK_TREE_SCROLL_OFFSET -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Int.serializer(), value as Int))

            // TOC
            StateKeys.EXPANDED_TOC_ENTRIES -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(SetSerializer(Long.serializer()), value as Set<Long>))
            StateKeys.TOC_CHILDREN -> null
            StateKeys.SHOW_TOC -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Boolean.serializer(), value as Boolean))
            StateKeys.TOC_SCROLL_INDEX -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Int.serializer(), value as Int))
            StateKeys.TOC_SCROLL_OFFSET -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Int.serializer(), value as Int))

            // Content
            StateKeys.SELECTED_LINE -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Line.serializer().nullable, value as Line?))
            StateKeys.SHOW_COMMENTARIES -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Boolean.serializer(), value as Boolean))
            StateKeys.SHOW_TARGUM -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Boolean.serializer(), value as Boolean))
            StateKeys.CONTENT_SCROLL_INDEX -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Int.serializer(), value as Int))
            StateKeys.CONTENT_SCROLL_OFFSET -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Int.serializer(), value as Int))
            StateKeys.CONTENT_ANCHOR_ID -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Long.serializer(), value as Long))
            StateKeys.CONTENT_ANCHOR_INDEX -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Int.serializer(), value as Int))
            StateKeys.PARAGRAPH_SCROLL_POSITION -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Int.serializer(), value as Int))
            StateKeys.CHAPTER_SCROLL_POSITION -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Int.serializer(), value as Int))
            StateKeys.SELECTED_CHAPTER -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Int.serializer(), value as Int))

            // Commentaries
            StateKeys.COMMENTARIES_SELECTED_TAB -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Int.serializer(), value as Int))
            StateKeys.COMMENTARIES_SCROLL_INDEX -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Int.serializer(), value as Int))
            StateKeys.COMMENTARIES_SCROLL_OFFSET -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Int.serializer(), value as Int))
            StateKeys.COMMENTATORS_LIST_SCROLL_INDEX -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Int.serializer(), value as Int))
            StateKeys.COMMENTATORS_LIST_SCROLL_OFFSET -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Int.serializer(), value as Int))
            StateKeys.COMMENTARIES_COLUMN_SCROLL_INDEX_BY_COMMENTATOR -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(MapSerializer(Long.serializer(), Int.serializer()), value as Map<Long, Int>))
            StateKeys.COMMENTARIES_COLUMN_SCROLL_OFFSET_BY_COMMENTATOR -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(MapSerializer(Long.serializer(), Int.serializer()), value as Map<Long, Int>))
            StateKeys.SELECTED_COMMENTATORS_BY_LINE -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), value as Map<Long, Set<Long>>))
            StateKeys.SELECTED_COMMENTATORS_BY_BOOK -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), value as Map<Long, Set<Long>>))
            StateKeys.SELECTED_TARGUM_SOURCES_BY_LINE -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), value as Map<Long, Set<Long>>))
            StateKeys.SELECTED_TARGUM_SOURCES_BY_BOOK -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), value as Map<Long, Set<Long>>))

            // Search (SearchResultView)
            SearchStateKeys.QUERY -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(String.serializer(), value as String))
            SearchStateKeys.NEAR -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Int.serializer(), value as Int))
            SearchStateKeys.FILTER_CATEGORY_ID -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Long.serializer(), value as Long))
            SearchStateKeys.FILTER_BOOK_ID -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Long.serializer(), value as Long))
            SearchStateKeys.FILTER_TOC_ID -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Long.serializer(), value as Long))
            SearchStateKeys.DATASET_SCOPE -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(String.serializer(), value as String))
            SearchStateKeys.FETCH_CATEGORY_ID -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Long.serializer(), value as Long))
            SearchStateKeys.FETCH_BOOK_ID -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Long.serializer(), value as Long))
            SearchStateKeys.FETCH_TOC_ID -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Long.serializer(), value as Long))
            SearchStateKeys.SCROLL_INDEX -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Int.serializer(), value as Int))
            SearchStateKeys.SCROLL_OFFSET -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Int.serializer(), value as Int))
            SearchStateKeys.ANCHOR_ID -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Long.serializer(), value as Long))
            SearchStateKeys.ANCHOR_INDEX -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Int.serializer(), value as Int))

            // Layout
            StateKeys.SPLIT_PANE_POSITION -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Float.serializer(), value as Float))
            StateKeys.TOC_SPLIT_PANE_POSITION -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Float.serializer(), value as Float))
            StateKeys.CONTENT_SPLIT_PANE_POSITION -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Float.serializer(), value as Float))
            StateKeys.TARGUM_SPLIT_PANE_POSITION -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Float.serializer(), value as Float))
            StateKeys.PREVIOUS_MAIN_SPLIT_POSITION -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Float.serializer(), value as Float))
            StateKeys.PREVIOUS_TOC_SPLIT_POSITION -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Float.serializer(), value as Float))
            StateKeys.PREVIOUS_CONTENT_SPLIT_POSITION -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Float.serializer(), value as Float))
            StateKeys.PREVIOUS_TARGUM_SPLIT_POSITION -> Base64.getEncoder().encodeToString(proto.encodeToByteArray(Float.serializer(), value as Float))

            else -> null
        }
    } catch (_: Throwable) {
        null
    }

    private fun decodeValue(key: String, encoded: String): Any? = try {
        // Primary path: base64(ProtoBuf) per key. Fallback to legacy JSON string when needed.
        when (key) {
            // Navigation
            StateKeys.EXPANDED_CATEGORIES -> runCatching { proto.decodeFromByteArray(SetSerializer(Long.serializer()), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(SetSerializer(Long.serializer()), encoded) }
            StateKeys.CATEGORY_CHILDREN -> null
            StateKeys.BOOKS_IN_CATEGORY -> null
            StateKeys.SELECTED_CATEGORY -> runCatching { proto.decodeFromByteArray(Category.serializer().nullable, Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Category.serializer().nullable, encoded) }
            StateKeys.SELECTED_BOOK -> runCatching { proto.decodeFromByteArray(Book.serializer().nullable, Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Book.serializer().nullable, encoded) }
            StateKeys.SEARCH_TEXT -> runCatching { proto.decodeFromByteArray(String.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(String.serializer(), encoded) }
            StateKeys.SHOW_BOOK_TREE -> runCatching { proto.decodeFromByteArray(Boolean.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Boolean.serializer(), encoded) }
            StateKeys.BOOK_TREE_SCROLL_INDEX -> runCatching { proto.decodeFromByteArray(Int.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Int.serializer(), encoded) }
            StateKeys.BOOK_TREE_SCROLL_OFFSET -> runCatching { proto.decodeFromByteArray(Int.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Int.serializer(), encoded) }

            // TOC
            StateKeys.EXPANDED_TOC_ENTRIES -> runCatching { proto.decodeFromByteArray(SetSerializer(Long.serializer()), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(SetSerializer(Long.serializer()), encoded) }
            StateKeys.TOC_CHILDREN -> null
            StateKeys.SHOW_TOC -> runCatching { proto.decodeFromByteArray(Boolean.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Boolean.serializer(), encoded) }
            StateKeys.TOC_SCROLL_INDEX -> runCatching { proto.decodeFromByteArray(Int.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Int.serializer(), encoded) }
            StateKeys.TOC_SCROLL_OFFSET -> runCatching { proto.decodeFromByteArray(Int.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Int.serializer(), encoded) }

            // Content
            StateKeys.SELECTED_LINE -> runCatching { proto.decodeFromByteArray(Line.serializer().nullable, Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Line.serializer().nullable, encoded) }
            StateKeys.SHOW_COMMENTARIES -> runCatching { proto.decodeFromByteArray(Boolean.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Boolean.serializer(), encoded) }
            StateKeys.SHOW_TARGUM -> runCatching { proto.decodeFromByteArray(Boolean.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Boolean.serializer(), encoded) }
            StateKeys.CONTENT_SCROLL_INDEX -> runCatching { proto.decodeFromByteArray(Int.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Int.serializer(), encoded) }
            StateKeys.CONTENT_SCROLL_OFFSET -> runCatching { proto.decodeFromByteArray(Int.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Int.serializer(), encoded) }
            StateKeys.CONTENT_ANCHOR_ID -> runCatching { proto.decodeFromByteArray(Long.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Long.serializer(), encoded) }
            StateKeys.CONTENT_ANCHOR_INDEX -> runCatching { proto.decodeFromByteArray(Int.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Int.serializer(), encoded) }
            StateKeys.PARAGRAPH_SCROLL_POSITION -> runCatching { proto.decodeFromByteArray(Int.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Int.serializer(), encoded) }
            StateKeys.CHAPTER_SCROLL_POSITION -> runCatching { proto.decodeFromByteArray(Int.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Int.serializer(), encoded) }
            StateKeys.SELECTED_CHAPTER -> runCatching { proto.decodeFromByteArray(Int.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Int.serializer(), encoded) }

            // Commentaries
            StateKeys.COMMENTARIES_SELECTED_TAB -> runCatching { proto.decodeFromByteArray(Int.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Int.serializer(), encoded) }
            StateKeys.COMMENTARIES_SCROLL_INDEX -> runCatching { proto.decodeFromByteArray(Int.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Int.serializer(), encoded) }
            StateKeys.COMMENTARIES_SCROLL_OFFSET -> runCatching { proto.decodeFromByteArray(Int.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Int.serializer(), encoded) }
            StateKeys.COMMENTATORS_LIST_SCROLL_INDEX -> runCatching { proto.decodeFromByteArray(Int.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Int.serializer(), encoded) }
            StateKeys.COMMENTATORS_LIST_SCROLL_OFFSET -> runCatching { proto.decodeFromByteArray(Int.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Int.serializer(), encoded) }
            StateKeys.COMMENTARIES_COLUMN_SCROLL_INDEX_BY_COMMENTATOR -> runCatching { proto.decodeFromByteArray(MapSerializer(Long.serializer(), Int.serializer()), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(MapSerializer(Long.serializer(), Int.serializer()), encoded) }
            StateKeys.COMMENTARIES_COLUMN_SCROLL_OFFSET_BY_COMMENTATOR -> runCatching { proto.decodeFromByteArray(MapSerializer(Long.serializer(), Int.serializer()), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(MapSerializer(Long.serializer(), Int.serializer()), encoded) }
            StateKeys.SELECTED_COMMENTATORS_BY_LINE -> runCatching { proto.decodeFromByteArray(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), encoded) }
            StateKeys.SELECTED_COMMENTATORS_BY_BOOK -> runCatching { proto.decodeFromByteArray(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), encoded) }
            StateKeys.SELECTED_TARGUM_SOURCES_BY_LINE -> runCatching { proto.decodeFromByteArray(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), encoded) }
            StateKeys.SELECTED_TARGUM_SOURCES_BY_BOOK -> runCatching { proto.decodeFromByteArray(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(MapSerializer(Long.serializer(), SetSerializer(Long.serializer())), encoded) }

            // Search (SearchResultView)
            SearchStateKeys.QUERY -> runCatching { proto.decodeFromByteArray(String.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(String.serializer(), encoded) }
            SearchStateKeys.NEAR -> runCatching { proto.decodeFromByteArray(Int.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Int.serializer(), encoded) }
            SearchStateKeys.FILTER_CATEGORY_ID -> runCatching { proto.decodeFromByteArray(Long.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Long.serializer(), encoded) }
            SearchStateKeys.FILTER_BOOK_ID -> runCatching { proto.decodeFromByteArray(Long.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Long.serializer(), encoded) }
            SearchStateKeys.FILTER_TOC_ID -> runCatching { proto.decodeFromByteArray(Long.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Long.serializer(), encoded) }
            SearchStateKeys.DATASET_SCOPE -> runCatching { proto.decodeFromByteArray(String.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(String.serializer(), encoded) }
            SearchStateKeys.FETCH_CATEGORY_ID -> runCatching { proto.decodeFromByteArray(Long.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Long.serializer(), encoded) }
            SearchStateKeys.FETCH_BOOK_ID -> runCatching { proto.decodeFromByteArray(Long.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Long.serializer(), encoded) }
            SearchStateKeys.FETCH_TOC_ID -> runCatching { proto.decodeFromByteArray(Long.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Long.serializer(), encoded) }
            SearchStateKeys.SCROLL_INDEX -> runCatching { proto.decodeFromByteArray(Int.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Int.serializer(), encoded) }
            SearchStateKeys.SCROLL_OFFSET -> runCatching { proto.decodeFromByteArray(Int.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Int.serializer(), encoded) }
            SearchStateKeys.ANCHOR_ID -> runCatching { proto.decodeFromByteArray(Long.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Long.serializer(), encoded) }
            SearchStateKeys.ANCHOR_INDEX -> runCatching { proto.decodeFromByteArray(Int.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Int.serializer(), encoded) }

            // Layout
            StateKeys.SPLIT_PANE_POSITION -> runCatching { proto.decodeFromByteArray(Float.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Float.serializer(), encoded) }
            StateKeys.TOC_SPLIT_PANE_POSITION -> runCatching { proto.decodeFromByteArray(Float.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Float.serializer(), encoded) }
            StateKeys.CONTENT_SPLIT_PANE_POSITION -> runCatching { proto.decodeFromByteArray(Float.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Float.serializer(), encoded) }
            StateKeys.TARGUM_SPLIT_PANE_POSITION -> runCatching { proto.decodeFromByteArray(Float.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Float.serializer(), encoded) }
            StateKeys.PREVIOUS_MAIN_SPLIT_POSITION -> runCatching { proto.decodeFromByteArray(Float.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Float.serializer(), encoded) }
            StateKeys.PREVIOUS_TOC_SPLIT_POSITION -> runCatching { proto.decodeFromByteArray(Float.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Float.serializer(), encoded) }
            StateKeys.PREVIOUS_CONTENT_SPLIT_POSITION -> runCatching { proto.decodeFromByteArray(Float.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Float.serializer(), encoded) }
            StateKeys.PREVIOUS_TARGUM_SPLIT_POSITION -> runCatching { proto.decodeFromByteArray(Float.serializer(), Base64.getDecoder().decode(encoded)) }.getOrElse { json.decodeFromString(Float.serializer(), encoded) }

            else -> null
        }
    } catch (_: Throwable) {
        null
    }
}
