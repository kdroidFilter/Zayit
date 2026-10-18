package io.github.kdroidfilter.seforimapp.features.bookcontent.state

/**
 * Type‑safe hint of where a book open action originated from.
 * Used to tailor initial UI (e.g., showing TOC) only for specific flows.
 */
enum class BookOpenSource {
    HOME_REFERENCE,
    CATEGORY_TREE_NEW_TAB,
    SEARCH_RESULT,
    COMMENTARY_OR_TARGUM,
}
