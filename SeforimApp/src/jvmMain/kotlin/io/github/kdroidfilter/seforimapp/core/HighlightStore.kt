package io.github.kdroidfilter.seforimapp.core

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Represents a single user highlight with position information.
 * Highlights are stored per line with character offsets for precise positioning.
 *
 * @property lineId The ID of the line containing this highlight
 * @property startOffset The starting character offset within the line's text
 * @property endOffset The ending character offset within the line's text
 * @property color The highlight color
 */
@Stable
data class UserHighlight(
    val lineId: Long,
    val startOffset: Int,
    val endOffset: Int,
    val color: Color,
) {
    /** Returns the text range of this highlight */
    val textRange: TextRange get() = TextRange(startOffset, endOffset)
}

/**
 * Available highlight colors for the user to choose from.
 */
object HighlightColors {
    val Yellow = Color(0xFFFFEB3B)
    val Green = Color(0xFF4CAF50)
    val Blue = Color(0xFF2196F3)
    val Pink = Color(0xFFE91E63)
    val Orange = Color(0xFFFF9800)
    val Transparent = Color.Transparent

    val all = listOf(Yellow, Green, Blue, Pink, Orange)
    val allWithClear = listOf(Yellow, Green, Blue, Pink, Orange, Transparent)
}

/**
 * In-memory store for user highlights.
 * Highlights are stored per book with position information (lineId + offsets).
 * Will be lost when the application closes.
 */
object HighlightStore {
    // Map of bookId to list of highlights for that book
    private val _highlightsByBook = MutableStateFlow<Map<Long, List<UserHighlight>>>(emptyMap())
    val highlightsByBook: StateFlow<Map<Long, List<UserHighlight>>> = _highlightsByBook.asStateFlow()

    /**
     * Adds a new highlight for the specified book and line.
     * If an overlapping highlight exists on the same line, it merges or updates.
     */
    fun addHighlight(
        bookId: Long,
        lineId: Long,
        startOffset: Int,
        endOffset: Int,
        color: Color,
    ) {
        if (startOffset >= endOffset) return

        _highlightsByBook.update { current ->
            val bookHighlights = current[bookId].orEmpty().toMutableList()

            // Find existing highlight on the same line that overlaps
            val existingIndex =
                bookHighlights.indexOfFirst { hl ->
                    hl.lineId == lineId && rangesOverlap(hl.startOffset, hl.endOffset, startOffset, endOffset)
                }

            if (existingIndex >= 0) {
                // Update existing highlight (replace with new range/color)
                bookHighlights[existingIndex] = UserHighlight(lineId, startOffset, endOffset, color)
            } else {
                // Add new highlight
                bookHighlights.add(UserHighlight(lineId, startOffset, endOffset, color))
            }

            current + (bookId to bookHighlights)
        }
    }

    /**
     * Removes highlights that overlap with the specified range on the given line.
     */
    fun removeHighlight(
        bookId: Long,
        lineId: Long,
        startOffset: Int,
        endOffset: Int,
    ) {
        _highlightsByBook.update { current ->
            val bookHighlights =
                current[bookId].orEmpty().filter { hl ->
                    !(hl.lineId == lineId && rangesOverlap(hl.startOffset, hl.endOffset, startOffset, endOffset))
                }
            if (bookHighlights.isEmpty()) {
                current - bookId
            } else {
                current + (bookId to bookHighlights)
            }
        }
    }

    /**
     * Gets all highlights for a specific book.
     */
    fun getHighlightsForBook(bookId: Long): List<UserHighlight> = _highlightsByBook.value[bookId].orEmpty()

    /**
     * Gets all highlights for a specific line in a book.
     */
    fun getHighlightsForLine(
        bookId: Long,
        lineId: Long,
    ): List<UserHighlight> = _highlightsByBook.value[bookId].orEmpty().filter { it.lineId == lineId }

    /**
     * Clears all highlights for a specific book.
     */
    fun clearHighlightsForBook(bookId: Long) {
        _highlightsByBook.update { current ->
            current - bookId
        }
    }

    /**
     * Clears all highlights.
     */
    fun clearAll() {
        _highlightsByBook.value = emptyMap()
    }

    private fun rangesOverlap(
        start1: Int,
        end1: Int,
        start2: Int,
        end2: Int,
    ): Boolean = start1 < end2 && start2 < end1
}
