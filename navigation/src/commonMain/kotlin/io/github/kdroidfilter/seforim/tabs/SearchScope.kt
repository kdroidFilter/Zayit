package io.github.kdroidfilter.seforim.tabs

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** Where a search runs: the whole library, or one category, book or TOC entry. */
@Serializable
@Immutable
sealed interface SearchScope {
    @Serializable
    data object Global : SearchScope

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
