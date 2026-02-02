package io.github.kdroidfilter.seforimapp.core

import androidx.compose.runtime.Stable
import io.github.kdroidfilter.seforimapp.core.presentation.text.findAllMatchesOriginal

/**
 * Represents a match found in a line.
 *
 * @property lineId The ID of the line containing the match
 * @property startOffset The starting character offset within the line's plain text
 * @property endOffset The ending character offset within the line's plain text
 */
@Stable
data class LineMatch(
    val lineId: Long,
    val startOffset: Int,
    val endOffset: Int,
)

/**
 * Registry that tracks the plain text content of lines for a book.
 * Used to find which line(s) contain selected text.
 *
 * Thread-safe for concurrent access.
 */
class LineTextRegistry {
    // Map of lineId to plain text content
    private val lineTexts = mutableMapOf<Long, String>()

    /**
     * Registers or updates the plain text for a line.
     */
    @Synchronized
    fun registerLine(
        lineId: Long,
        plainText: String,
    ) {
        lineTexts[lineId] = plainText
    }

    /**
     * Unregisters a line (e.g., when it's no longer visible).
     */
    @Synchronized
    fun unregisterLine(lineId: Long) {
        lineTexts.remove(lineId)
    }

    /**
     * Clears all registered lines.
     */
    @Synchronized
    fun clear() {
        lineTexts.clear()
    }

    /**
     * Finds all lines that contain the given text.
     * Uses diacritic-insensitive matching for Hebrew text.
     *
     * @param selectedText The text to search for
     * @return List of matches with line IDs and offsets
     */
    @Synchronized
    fun findMatches(selectedText: String): List<LineMatch> {
        val trimmed = selectedText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val matches = mutableListOf<LineMatch>()
        for ((lineId, text) in lineTexts) {
            val ranges = findAllMatchesOriginal(text, trimmed)
            for (range in ranges) {
                matches.add(
                    LineMatch(
                        lineId = lineId,
                        startOffset = range.first,
                        endOffset = range.last + 1,
                    ),
                )
            }
        }
        return matches
    }

    /**
     * Finds a match in a specific line, closest to the given character offset.
     * Use this when you know which line and approximate position the user is interacting with.
     *
     * @param selectedText The text to search for
     * @param lineId The specific line to search in
     * @param nearOffset Optional character offset to find the closest match to
     * @return The match in that line closest to the offset, or null if not found
     */
    @Synchronized
    fun findMatchInLine(
        selectedText: String,
        lineId: Long,
        nearOffset: Int? = null,
    ): LineMatch? {
        val trimmed = selectedText.trim()
        if (trimmed.isEmpty()) return null

        val text = lineTexts[lineId] ?: return null
        val ranges = findAllMatchesOriginal(text, trimmed)
        if (ranges.isEmpty()) return null

        // If no offset hint, return first match
        if (nearOffset == null) {
            val range = ranges.first()
            return LineMatch(
                lineId = lineId,
                startOffset = range.first,
                endOffset = range.last + 1,
            )
        }

        // Find the match closest to the given offset
        val closestRange =
            ranges.minByOrNull { range ->
                minOf(
                    kotlin.math.abs(range.first - nearOffset),
                    kotlin.math.abs(range.last - nearOffset),
                )
            } ?: ranges.first()

        return LineMatch(
            lineId = lineId,
            startOffset = closestRange.first,
            endOffset = closestRange.last + 1,
        )
    }

    /**
     * Finds the first line that contains the given text.
     * Searches lines in sorted order by lineId for deterministic results.
     * Falls back to this when no specific line is known.
     *
     * @param selectedText The text to search for
     * @return The first match (by lineId order), or null if not found
     */
    @Synchronized
    fun findFirstMatch(selectedText: String): LineMatch? {
        val trimmed = selectedText.trim()
        if (trimmed.isEmpty()) return null

        // Sort by lineId for deterministic iteration order
        for ((lineId, text) in lineTexts.entries.sortedBy { it.key }) {
            val ranges = findAllMatchesOriginal(text, trimmed)
            if (ranges.isNotEmpty()) {
                val range = ranges.first()
                return LineMatch(
                    lineId = lineId,
                    startOffset = range.first,
                    endOffset = range.last + 1,
                )
            }
        }
        return null
    }

    /**
     * Gets the plain text for a specific line.
     */
    @Synchronized
    fun getLineText(lineId: Long): String? = lineTexts[lineId]

    /**
     * Gets the number of registered lines.
     */
    @Synchronized
    fun size(): Int = lineTexts.size
}

/**
 * Global registry instance for the current book view.
 * In a multi-tab scenario, each tab should have its own registry.
 */
object GlobalLineTextRegistry {
    private val registries = mutableMapOf<String, LineTextRegistry>()

    /**
     * Gets or creates a registry for the given tab.
     */
    @Synchronized
    fun getForTab(tabId: String): LineTextRegistry = registries.getOrPut(tabId) { LineTextRegistry() }

    /**
     * Removes the registry for the given tab.
     */
    @Synchronized
    fun removeForTab(tabId: String) {
        registries.remove(tabId)
    }

    /**
     * Clears all registries.
     */
    @Synchronized
    fun clearAll() {
        registries.clear()
    }
}
