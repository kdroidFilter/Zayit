package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimapp.features.search.SearchSessionStore
import io.github.kdroidfilter.seforimapp.framework.session.SearchPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore

/** The search's state in the session: the per-tab state the session saves at close. */
class TabSearchSessionStore(
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
