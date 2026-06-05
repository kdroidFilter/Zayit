package io.github.kdroidfilter.seforimapp.pagination

import androidx.paging.PagingConfig

/**
 * Centralized paging configuration for the Book Content feature.
 *
 * Use LINES for the main content list and COMMENTS for the commentaries list
 * to keep pageSize, prefetchDistance and initialLoadSize consistent across the app.
 */
object PagingDefaults {
    object LINES {
        const val PAGE_SIZE: Int = 10
        const val PREFETCH_DISTANCE: Int = 10
        const val INITIAL_LOAD_SIZE: Int = 30

        fun config(placeholders: Boolean = false): PagingConfig =
            PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                enablePlaceholders = placeholders,
            )
    }

    object COMMENTS {
        // A commentator column loads its whole content eagerly (the scrollbar relies on the full
        // char-count vector). Use large pages so that load happens in ~1 query instead of many
        // 10-item pages — each page previously grew `itemCount` and recomposed the column,
        // producing a recomposition storm on first open. One big page → one itemCount jump.
        const val PAGE_SIZE: Int = 64
        const val PREFETCH_DISTANCE: Int = 64
        const val INITIAL_LOAD_SIZE: Int = 256

        fun config(placeholders: Boolean = false): PagingConfig =
            PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                enablePlaceholders = placeholders,
            )
    }
}
