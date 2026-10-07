package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.features.bookcontent.state.PaneSearchResult
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.PaneSearchState

/** Shows a hidden search bar, hides a shown one. */
internal fun <T : PaneSearchResult> PaneSearchState<T>?.toggled(): PaneSearchState<T>? = if (this == null) PaneSearchState() else null

/**
 * Runs a pane search: records [query], then stores what [compute] finds (null: nothing to
 * show) unless the query changed or the bar closed meanwhile. The previous result stays
 * shown until then. [update] transforms the pane's search state, if shown.
 */
internal suspend fun <T : PaneSearchResult> runPaneSearch(
    query: String,
    update: ((PaneSearchState<T>) -> PaneSearchState<T>) -> Unit,
    compute: suspend () -> T?,
) {
    update { it.copy(query = query) }
    val result = if (query.isBlank()) null else compute()
    update { if (it.query == query) it.copy(result = result, resultQuery = query) else it }
}
