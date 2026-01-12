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

    fun updateSelection(text: String) {
        _selectedText.value = text
    }

    fun clear() {
        _selectedText.value = ""
    }
}
