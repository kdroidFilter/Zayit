package io.github.kdroidfilter.seforimapp.features.bookcontent

import io.github.kdroidfilter.seforimlibrary.core.models.*

/**
 * Represents different events related to book content, including navigation, table of contents (TOC),
 * book tree interaction, content-specific actions, commentaries, targum, scroll events, and state management.
 * This sealed interface is used as an event model for handling various user interactions and application states
 * in the context of book content exploration.
 */
sealed interface BookContentEvent {
    // Navigation events
    data class SearchTextChanged(
        val text: String,
    ) : BookContentEvent

    data class SearchInDatabase(
        val query: String,
    ) : BookContentEvent

    data class CategorySelected(
        val category: Category,
    ) : BookContentEvent

    data class BookSelected(
        val book: Book,
    ) : BookContentEvent

    data class BookSelectedInNewTab(
        val book: Book,
    ) : BookContentEvent

    data object ToggleBookTree : BookContentEvent

    // TOC events
    data class TocEntryExpanded(
        val entry: TocEntry,
    ) : BookContentEvent

    data object ToggleToc : BookContentEvent

    data class TocScrolled(
        val index: Int,
        val offset: Int,
    ) : BookContentEvent

    data class AltTocEntryExpanded(
        val entry: AltTocEntry,
    ) : BookContentEvent

    data class AltTocScrolled(
        val index: Int,
        val offset: Int,
    ) : BookContentEvent

    data class AltTocStructureSelected(
        val structure: AltTocStructure,
    ) : BookContentEvent

    data class AltTocEntrySelected(
        val entry: AltTocEntry,
    ) : BookContentEvent

    // Book tree events
    data class BookTreeScrolled(
        val index: Int,
        val offset: Int,
    ) : BookContentEvent

    // Content events
    data class LineSelected(
        val line: Line,
    ) : BookContentEvent

    data class LineCtrlClicked(
        val line: Line,
    ) : BookContentEvent

    data class LoadAndSelectLine(
        val lineId: Long,
    ) : BookContentEvent

    // Open a specific book and jump to a given line in the current tab
    data class OpenBookAtLine(
        val bookId: Long,
        val lineId: Long,
    ) : BookContentEvent

    // Open a book by its id (VM resolves Book)
    data class OpenBookById(
        val bookId: Long,
    ) : BookContentEvent

    data object ToggleCommentaries : BookContentEvent

    data object ToggleTargum : BookContentEvent

    data object ToggleSources : BookContentEvent

    data object ToggleDiacritics : BookContentEvent

    data class ContentScrolled(
        val anchorId: Long,
        val anchorIndex: Int,
        val scrollIndex: Int,
        val scrollOffset: Int,
    ) : BookContentEvent

    data object NavigateToPreviousLine : BookContentEvent

    data object NavigateToNextLine : BookContentEvent

    // Commentaries events
    data class CommentariesTabSelected(
        val index: Int,
    ) : BookContentEvent

    data class CommentariesScrolled(
        val index: Int,
        val offset: Int,
    ) : BookContentEvent

    data class CommentatorsListScrolled(
        val index: Int,
        val offset: Int,
    ) : BookContentEvent

    data class CommentaryColumnScrolled(
        val commentatorId: Long,
        val index: Int,
        val offset: Int,
    ) : BookContentEvent

    data class OpenCommentaryTarget(
        val bookId: Long,
        val lineId: Long?,
    ) : BookContentEvent

    data class SelectedCommentatorsChanged(
        val lineId: Long,
        val selectedIds: Set<Long>,
    ) : BookContentEvent

    data object CommentatorsSelectionLimitExceeded : BookContentEvent

    // Targum events
    data class SelectedTargumSourcesChanged(
        val lineId: Long,
        val selectedIds: Set<Long>,
    ) : BookContentEvent

    data class SelectedSourcesChanged(
        val lineId: Long,
        val selectedIds: Set<Long>,
    ) : BookContentEvent

    // Scroll events
    data class ParagraphScrolled(
        val position: Int,
    ) : BookContentEvent

    data class ChapterScrolled(
        val position: Int,
    ) : BookContentEvent

    data class ChapterSelected(
        val index: Int,
    ) : BookContentEvent

    // State management
    data object SaveState : BookContentEvent
}
