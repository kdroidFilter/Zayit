package io.github.kdroidfilter.seforimapp.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple store to track the current text selection across the app.
 * Used for keyboard shortcuts that need access to selected text.
 */
object TextSelectionStore {
    private val _selectedText = MutableStateFlow("")
    val selectedText: StateFlow<String> = _selectedText.asStateFlow()

    // Track the line ID and approximate character offset where user is interacting
    private val _activeLineId = MutableStateFlow<Long?>(null)
    val activeLineId: StateFlow<Long?> = _activeLineId.asStateFlow()

    private val _activeCharOffset = MutableStateFlow<Int?>(null)
    val activeCharOffset: StateFlow<Int?> = _activeCharOffset.asStateFlow()

    fun updateSelection(text: String) {
        _selectedText.value = text
    }

    fun updateActiveLineId(lineId: Long) {
        _activeLineId.value = lineId
    }

    fun updateActivePosition(
        lineId: Long,
        charOffset: Int,
    ) {
        _activeLineId.value = lineId
        _activeCharOffset.value = charOffset
    }

    fun clear() {
        _selectedText.value = ""
    }

    fun clearActiveLineId() {
        _activeLineId.value = null
        _activeCharOffset.value = null
    }
}
