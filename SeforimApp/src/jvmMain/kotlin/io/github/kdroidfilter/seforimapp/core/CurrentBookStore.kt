package io.github.kdroidfilter.seforimapp.core

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot of the book currently active in the foreground tab, together with the title of its
 * root category. The root title lets formatters apply per-tradition rules (e.g. trim the line
 * segment for Talmud) without re-resolving the category chain on every action.
 */
data class ActiveBook(
    val book: Book,
    val rootTitle: String?,
)

object CurrentBookStore {
    private val _activeBook = MutableStateFlow<ActiveBook?>(null)
    val activeBook: StateFlow<ActiveBook?> = _activeBook.asStateFlow()

    fun update(
        book: Book?,
        rootTitle: String?,
    ) {
        _activeBook.value = book?.let { ActiveBook(it, rootTitle) }
    }

    fun clear() {
        _activeBook.value = null
    }
}
