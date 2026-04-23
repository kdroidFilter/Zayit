package io.github.kdroidfilter.seforimapp.core

import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot of the lines currently materialized by the paged content list.
 * Read by features that need to map a free-form text selection back to its source lines
 * (e.g. "copy with source") without coupling them to the paging UI layer.
 */
object VisibleLinesStore {
    private val _visibleLines = MutableStateFlow<List<Line>>(emptyList())
    val visibleLines: StateFlow<List<Line>> = _visibleLines.asStateFlow()

    fun update(lines: List<Line>) {
        _visibleLines.value = lines
    }

    fun clear() {
        _visibleLines.value = emptyList()
    }
}
