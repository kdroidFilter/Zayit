package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.features.bookcontent.state.PaneSearchResult
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.PaneSearchState
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Shows a hidden search bar, hides a shown one. */
internal fun <T : PaneSearchResult> PaneSearchState<T>?.toggled(): PaneSearchState<T>? = if (this == null) PaneSearchState() else null

/**
 * Runs the searches of one pane, through [update] which transforms its search state if shown.
 * A search cancels the one still running, records its query, then stores what its compute
 * finds (null: nothing to show) unless the bar closed or the query changed meanwhile; the
 * previous result stays shown until then. Main-thread confined, as the ViewModel events.
 */
internal class PaneSearchRunner<T : PaneSearchResult>(
    private val update: ((PaneSearchState<T>) -> PaneSearchState<T>) -> Unit,
) {
    private var running: Job? = null

    suspend fun run(
        query: String,
        compute: suspend () -> T?,
    ) {
        val current = currentCoroutineContext()[Job]
        // Never the caller itself, when it runs successive searches
        running?.takeIf { it !== current }?.cancel()
        running = current
        try {
            update { it.copy(query = query) }
            val result = if (query.isBlank()) null else compute()
            currentCoroutineContext().ensureActive()
            update { if (it.query == query) it.copy(result = result, resultQuery = query) else it }
        } finally {
            // Forget a finished search, so a later one cannot cancel its (still alive) caller
            if (running === current) running = null
        }
    }
}
