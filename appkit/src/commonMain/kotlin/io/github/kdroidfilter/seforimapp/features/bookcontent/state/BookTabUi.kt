package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.notes.NoteDraftAnchor
import io.github.kdroidfilter.seforimapp.logger.warnln
import kotlinx.coroutines.launch

/**
 * UI state of one tab that the text and the dock panes drawing the same book share: the book's
 * line-connections cache (commentaries / links / sources) and the note draft opened from the text.
 *
 * Lives in the tab's ViewModelStore, so it follows the tab between windows and the panes of
 * whichever window shows it read the same instance as its text.
 */
class BookTabUi : ViewModel() {
    /** A pending note draft (anchored, not yet saved), edited inline in the notes pane. */
    var noteDraft by mutableStateOf<NoteDraftAnchor?>(null)

    /** Primary line when the draft was opened; selecting another line drops the draft. */
    var noteDraftBaselineLine: Long? = null

    private var cacheBookId = Long.MIN_VALUE
    private var cache: SnapshotStateMap<Long, LineConnectionsSnapshot> = mutableStateMapOf()

    // Line ids whose connections load is in flight: two requests before the first resolves would
    // otherwise both see the id missing and each launch a redundant DB load. Main thread only.
    private val inFlight = mutableSetOf<Long>()

    /** Connections cache of [bookId]; a new book starts from an empty one. */
    fun connections(bookId: Long): SnapshotStateMap<Long, LineConnectionsSnapshot> {
        if (bookId != cacheBookId) {
            cacheBookId = bookId
            cache = mutableStateMapOf()
            inFlight.clear()
        }
        return cache
    }

    fun prefetch(
        bookId: Long,
        providers: Providers,
        ids: List<Long>,
    ) {
        if (ids.isEmpty()) return
        val target = connections(bookId)
        val missing = ids.filterNot { target.containsKey(it) || it in inFlight }.distinct()
        if (missing.isEmpty()) return
        inFlight.addAll(missing)
        viewModelScope.launch {
            runSuspendCatching { providers.loadLineConnections(missing) }
                .onSuccess { result -> target.putAll(result) }
                .onFailure { e -> warnln { "connections load failed for=$missing: $e" } }
            inFlight.removeAll(missing.toSet())
        }
    }
}
