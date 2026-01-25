package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.kdroidfilter.seforimlibrary.search.LineHit

/**
 * Use case for search execution helper operations.
 *
 * This class provides functionality to:
 * - Convert search engine hits to search results
 * - Filter hits based on allowed line IDs
 */
class ExecuteSearchUseCase {
    companion object {
        const val DEFAULT_NEAR = 5
    }

    /**
     * Converts a list of LineHit objects from the search engine to SearchResult objects.
     *
     * @param hits The hits from the search engine
     * @param rawQuery The original search query (used for exact match boosting)
     * @return List of SearchResult objects
     */
    fun hitsToResults(
        hits: List<LineHit>,
        rawQuery: String,
    ): List<SearchResult> {
        if (hits.isEmpty()) return emptyList()

        val trimmedQuery = rawQuery.trim()
        val checkExact = trimmedQuery.isNotEmpty()
        val out = ArrayList<SearchResult>(hits.size)

        for (hit in hits) {
            val snippetFromIndex =
                when {
                    hit.snippet.isNotBlank() -> hit.snippet
                    hit.rawText.isNotBlank() -> hit.rawText
                    else -> ""
                }
            // Boost exact matches slightly for better ranking
            val scoreBoost = if (checkExact && hit.rawText.contains(trimmedQuery)) 1e-3 else 0.0

            out +=
                SearchResult(
                    bookId = hit.bookId,
                    bookTitle = hit.bookTitle,
                    lineId = hit.lineId,
                    lineIndex = hit.lineIndex,
                    snippet = snippetFromIndex,
                    rank = hit.score.toDouble() + scoreBoost,
                )
        }
        return out
    }

    /**
     * Filters hits to only include those whose line IDs are in the allowed set.
     * If the allowed set is empty, returns all hits unchanged.
     *
     * @param hits The hits to filter
     * @param allowedLineIds The set of allowed line IDs (empty means no filtering)
     * @return Filtered list of hits
     */
    fun filterHitsByLineIds(
        hits: List<LineHit>,
        allowedLineIds: Set<Long>,
    ): List<LineHit> {
        if (allowedLineIds.isEmpty()) return hits
        return hits.filter { it.lineId in allowedLineIds }
    }
}
