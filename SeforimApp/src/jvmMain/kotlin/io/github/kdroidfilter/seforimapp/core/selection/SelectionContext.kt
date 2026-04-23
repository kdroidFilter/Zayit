package io.github.kdroidfilter.seforimapp.core.selection

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The book currently active in the foreground tab, paired with the title of its root category.
 * The root title lets formatters apply per-tradition rules (e.g. trim the in-page line segment
 * for Talmud) without re-resolving the category chain on every action.
 */
data class ActiveBook(
    val book: Book,
    val rootTitle: String?,
)

/**
 * App-wide read/write surface that bridges Compose-side state (current selection, active book,
 * paged-line snapshot) to Compose-free consumers (AWT keyboard dispatchers in particular).
 *
 * Provided as a Metro-scoped singleton so that consumers (`BookContentScreen`, `BookContentView`,
 * `main.kt`) reach it through `LocalAppGraph`/`AppGraph` instead of touching object globals.
 */
interface SelectionContext {
    val selectedText: StateFlow<String>
    val activeBook: StateFlow<ActiveBook?>
    val visibleLines: StateFlow<List<Line>>

    fun setSelectedText(text: String)

    fun clearSelectedText()

    fun setActiveBook(
        book: Book?,
        rootTitle: String?,
    )

    /**
     * Clear the currently published active book only when its id matches [bookId]. Prevents a
     * background tab's dispose handler from wiping the book that the just-activated tab has
     * already published.
     */
    fun clearActiveBookIf(bookId: Long?)

    fun setVisibleLines(lines: List<Line>)

    /**
     * Clear the visible-lines snapshot only when the currently published list belongs to
     * [bookId]. Same race-safety rationale as [clearActiveBookIf].
     */
    fun clearVisibleLinesIf(bookId: Long?)
}

class DefaultSelectionContext : SelectionContext {
    private val _selectedText = MutableStateFlow("")
    override val selectedText: StateFlow<String> = _selectedText.asStateFlow()

    private val _activeBook = MutableStateFlow<ActiveBook?>(null)
    override val activeBook: StateFlow<ActiveBook?> = _activeBook.asStateFlow()

    private val _visibleLines = MutableStateFlow<List<Line>>(emptyList())
    override val visibleLines: StateFlow<List<Line>> = _visibleLines.asStateFlow()

    override fun setSelectedText(text: String) {
        _selectedText.value = text
    }

    override fun clearSelectedText() {
        _selectedText.value = ""
    }

    override fun setActiveBook(
        book: Book?,
        rootTitle: String?,
    ) {
        _activeBook.value = book?.let { ActiveBook(it, rootTitle) }
    }

    override fun clearActiveBookIf(bookId: Long?) {
        if (_activeBook.value?.book?.id == bookId) {
            _activeBook.value = null
        }
    }

    override fun setVisibleLines(lines: List<Line>) {
        _visibleLines.value = lines
    }

    override fun clearVisibleLinesIf(bookId: Long?) {
        val current = _visibleLines.value
        if (current.isEmpty() || current.first().bookId == bookId) {
            _visibleLines.value = emptyList()
        }
    }
}
