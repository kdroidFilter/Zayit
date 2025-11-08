package io.github.kdroidfilter.seforimapp.features.search

/**
 * State keys for the Search results tab stored in TabStateManager.
 */
object SearchStateKeys {
    const val QUERY = "search.query"
    const val NEAR = "search.near"
    const val FILTER_CATEGORY_ID = "search.filter.categoryId"
    const val FILTER_BOOK_ID = "search.filter.bookId"
    const val FILTER_TOC_ID = "search.filter.tocEntryId"
    // Dataset scope persisted at search execution time
    const val DATASET_SCOPE = "search.dataset.scope" // values: global, category, book, toc
    const val FETCH_CATEGORY_ID = "search.fetch.categoryId"
    const val FETCH_BOOK_ID = "search.fetch.bookId"
    const val FETCH_TOC_ID = "search.fetch.tocEntryId"
    const val SCROLL_INDEX = "search.scroll.index"
    const val SCROLL_OFFSET = "search.scroll.offset"
    const val ANCHOR_ID = "search.anchor.id"
    const val ANCHOR_INDEX = "search.anchor.index"
    // Global search scope: when false (default), restrict to base books only; when true, search all books
    const val GLOBAL_EXTENDED = "search.global.extended"
}
