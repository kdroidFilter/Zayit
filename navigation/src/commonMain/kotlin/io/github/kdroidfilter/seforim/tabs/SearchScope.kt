package io.github.kdroidfilter.seforim.tabs

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** Where a search runs: the whole library, or one book or TOC entry. */
@Serializable
@Immutable
sealed interface SearchScope {
    @Serializable
    data object Global : SearchScope

    /**
     * Read back only, from tabs and sessions saved when a search could be scoped to a category: it
     * runs everywhere now, the category tabs above the results narrowing it.
     */
    @Serializable
    data class Category(
        val categoryId: Long,
    ) : SearchScope

    @Serializable
    data class Book(
        val bookId: Long,
    ) : SearchScope

    @Serializable
    data class Toc(
        val bookId: Long,
        val tocId: Long,
    ) : SearchScope
}
