package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.features.search.SearchSessionStore
import io.github.kdroidfilter.seforimapp.features.search.SearchTabs
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.session.SearchPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import kotlinx.coroutines.flow.Flow

/** The search's tabs on the desktop: those of the window that holds the searching tab, across virtual desktops. */
class DesktopSearchTabs(
    private val desktopManager: DesktopManager,
) : SearchTabs {
    override fun openTab(
        fromTabId: String,
        destination: TabsDestination,
    ) {
        desktopManager.tabsViewModelFor(fromTabId)?.openTab(destination)
    }

    override fun replace(
        tabId: String,
        destination: TabsDestination,
    ) {
        desktopManager.tabsViewModelFor(tabId)?.replaceCurrentTabDestination(destination)
    }

    override fun tabExistsFlow(tabId: String): Flow<Boolean> = desktopManager.tabExistsFlow(tabId)

    override fun isTabOpen(tabId: String): Boolean = desktopManager.isTabOpen(tabId)
}

/** The search's state in the desktop session: the per-tab state the session saves at close. */
class DesktopSearchSessionStore(
    private val store: TabPersistedStateStore,
) : SearchSessionStore {
    override fun search(tabId: String): SearchPersistedState? = store.get(tabId)?.search

    override fun updateSearch(
        tabId: String,
        transform: (SearchPersistedState?) -> SearchPersistedState?,
    ) {
        store.update(tabId) { current -> current.copy(search = transform(current.search)) }
    }

    override fun selectBook(
        tabId: String,
        bookId: Long,
    ) {
        store.update(tabId) { current -> current.copy(bookContent = current.bookContent.copy(selectedBookId = bookId)) }
    }

    override fun openBookAtLine(
        tabId: String,
        bookId: Long,
        lineId: Long,
    ) {
        store.update(tabId) { current ->
            current.copy(
                bookContent =
                    current.bookContent.copy(
                        selectedBookId = bookId,
                        selectedLineIds = setOf(lineId),
                        primarySelectedLineId = lineId,
                        isTocEntrySelection = false,
                        contentAnchorLineId = lineId,
                        contentAnchorIndex = 0,
                        contentScrollIndex = 0,
                        contentScrollOffset = 0,
                        isTocVisible = true,
                    ),
            )
        }
    }
}
