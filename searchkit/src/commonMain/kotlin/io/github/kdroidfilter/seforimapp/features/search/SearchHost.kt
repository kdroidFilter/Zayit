package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.framework.session.SearchPersistedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/*
 * What the search screens ask of the app around them: the desktop's windows, tabs and session, a mobile app's
 * navigation and storage. Each app implements these; the view models know nothing else of it.
 */

/** The tabs (or screens) the search opens and watches. */
interface SearchTabs {
    /** Opens [destination] in a new tab, from the tab [fromTabId]. */
    fun openTab(
        fromTabId: String,
        destination: TabsDestination,
    )

    /** Shows [destination] in the tab [tabId] instead of what it shows. */
    fun replace(
        tabId: String,
        destination: TabsDestination,
    )

    /** Whether the tab [tabId] still exists, as it changes. */
    fun tabExistsFlow(tabId: String): Flow<Boolean>

    fun isTabOpen(tabId: String): Boolean
}

/** What the tabs keep across sessions, as far as the search is concerned. */
interface SearchSessionStore {
    /** The search state of the tab [tabId], null when it has none. */
    fun search(tabId: String): SearchPersistedState?

    /** Replaces the search state of the tab [tabId] by [transform]'s (null: the tab has none any more). */
    fun updateSearch(
        tabId: String,
        transform: (SearchPersistedState?) -> SearchPersistedState?,
    )

    /** Makes [bookId] the book the tab [tabId] shows. */
    fun selectBook(
        tabId: String,
        bookId: Long,
    )

    /**
     * Prepares the tab [tabId] to open [bookId] at [lineId]: the line selected and anchored, the table of contents
     * shown, as a found line opens.
     */
    fun openBookAtLine(
        tabId: String,
        bookId: Long,
        lineId: Long,
    )
}

/** The app settings the search reads and sets. */
interface SearchSettings {
    val textSizeFlow: StateFlow<Float>

    fun getTextSize(): Float

    /** Pre-fills the find-in-page bar of the tab [tabId] with [q]. */
    fun setFindQuery(
        tabId: String,
        q: String,
    )

    fun setFindSmartMode(
        tabId: String,
        enabled: Boolean,
    )

    fun openFindBar(tabId: String)

    val userFirstNameFlow: StateFlow<String>
    val userLastNameFlow: StateFlow<String>
    val userCommunityCodeFlow: StateFlow<String?>

    companion object {
        /** The text size before the user changes it. */
        const val DEFAULT_TEXT_SIZE = 16f
    }
}

/** The history of what was searched. */
interface SearchHistory {
    suspend fun recordSearchVisit(
        query: String,
        scope: SearchScope,
        globalExtended: Boolean,
        scopeLabel: String?,
        timestamp: Long,
    )
}
