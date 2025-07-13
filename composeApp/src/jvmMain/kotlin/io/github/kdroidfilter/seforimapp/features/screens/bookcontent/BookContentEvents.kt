package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry

sealed class BookContentEvents {
    data class OnUpdateParagraphScrollPosition(val position: Int): BookContentEvents()
    data class OnUpdateChapterScrollPosition(val position: Int): BookContentEvents()
    data object SaveAllStates : BookContentEvents()
    data class OnSearchTextChange(val text: String): BookContentEvents()
    data class OnChapterSelected(val index: Int): BookContentEvents()

    // Database-related events
    data class OnCategorySelected(val category: Category): BookContentEvents()
    data class OnBookSelected(val book: Book): BookContentEvents()
    data class OnTocEntryExpanded(val tocEntry: TocEntry): BookContentEvents()
    data class OnLineSelected(val line: Line): BookContentEvents()
    data class OnLoadAndSelectLine(val lineId: Long): BookContentEvents()
}
