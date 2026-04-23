package io.github.kdroidfilter.seforimapp.core.selection

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The book currently active in the foreground tab, paired with the title of its root category
 * and the id of the tab that published it. The tabId is the real ownership key: it lets a
 * dispose handler clear only its own publish, even when the same book is open in another tab.
 */
data class ActiveBook(
    val tabId: String,
    val book: Book,
    val rootTitle: String?,
)

/**
 * App-wide read/write surface that bridges Compose-side state (current selection, active book,
 * paged-line snapshot) to Compose-free consumers (AWT keyboard dispatchers in particular).
 *
 * Provided as a Metro-scoped singleton so consumers (`BookContentScreen`, `BookContentView`,
 * `main.kt`) reach it through `LocalAppGraph`/`AppGraph` instead of touching object globals.
 *
 * Ownership is tracked by `tabId`: setters embed the publishing tab and `clear*If` only
 * removes a publish whose tabId matches. This prevents one tab's lifecycle (background or
 * dispose) from wiping a sibling tab's state, even when both tabs reference the same book.
 */
interface SelectionContext {
    val selectedText: StateFlow<String>
    val activeBook: StateFlow<ActiveBook?>
    val visibleLines: StateFlow<VisibleLines>

    fun setSelectedText(text: String)

    fun clearSelectedText()

    fun setActiveBook(
        tabId: String,
        book: Book?,
        rootTitle: String?,
    )

    fun clearActiveBookIfOwnedBy(tabId: String)

    fun setVisibleLines(
        tabId: String,
        lines: List<Line>,
    )

    fun clearVisibleLinesIfOwnedBy(tabId: String)
}

/**
 * Snapshot of the currently materialized lines, with the id of the tab that published them.
 * Empty publisher means no tab currently owns the snapshot.
 */
data class VisibleLines(
    val tabId: String?,
    val lines: List<Line>,
) {
    companion object {
        val EMPTY = VisibleLines(tabId = null, lines = emptyList())
    }
}

class DefaultSelectionContext : SelectionContext {
    private val _selectedText = MutableStateFlow("")
    override val selectedText: StateFlow<String> = _selectedText.asStateFlow()

    private val _activeBook = MutableStateFlow<ActiveBook?>(null)
    override val activeBook: StateFlow<ActiveBook?> = _activeBook.asStateFlow()

    private val _visibleLines = MutableStateFlow(VisibleLines.EMPTY)
    override val visibleLines: StateFlow<VisibleLines> = _visibleLines.asStateFlow()

    override fun setSelectedText(text: String) {
        _selectedText.value = text
    }

    override fun clearSelectedText() {
        _selectedText.value = ""
    }

    override fun setActiveBook(
        tabId: String,
        book: Book?,
        rootTitle: String?,
    ) {
        _activeBook.value = book?.let { ActiveBook(tabId, it, rootTitle) }
    }

    override fun clearActiveBookIfOwnedBy(tabId: String) {
        if (_activeBook.value?.tabId == tabId) {
            _activeBook.value = null
        }
    }

    override fun setVisibleLines(
        tabId: String,
        lines: List<Line>,
    ) {
        _visibleLines.value = VisibleLines(tabId, lines)
    }

    override fun clearVisibleLinesIfOwnedBy(tabId: String) {
        if (_visibleLines.value.tabId == tabId) {
            _visibleLines.value = VisibleLines.EMPTY
        }
    }
}
