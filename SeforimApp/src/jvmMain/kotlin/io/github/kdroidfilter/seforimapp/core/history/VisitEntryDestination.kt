package io.github.kdroidfilter.seforimapp.core.history

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import java.util.UUID

/** Where a history entry reopens, in a new tab: the book at its line, or the search in the scope it ran in. */
fun VisitEntry.destination(): TabsDestination? {
    val tabId = UUID.randomUUID().toString()
    return when (kind) {
        VisitKind.BOOK -> bookId?.let { TabsDestination.BookContent(bookId = it, tabId = tabId, lineId = lineId) }
        VisitKind.SEARCH ->
            searchQuery?.let {
                TabsDestination.Search(
                    searchQuery = it,
                    tabId = tabId,
                    scope = searchScope,
                    globalExtended = searchGlobalExtended,
                )
            }
    }
}
