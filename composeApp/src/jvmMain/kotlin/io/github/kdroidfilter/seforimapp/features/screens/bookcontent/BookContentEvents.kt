package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import io.github.kdroidfilter.seforimlibrary.core.models.*


sealed interface BookContentEvent {
    // Navigation events
    data class SearchTextChanged(val text: String) : BookContentEvent
    data class CategorySelected(val category: Category) : BookContentEvent
    data class BookSelected(val book: Book) : BookContentEvent
    data class BookSelectedInNewTab(val book: Book) : BookContentEvent
    data object ToggleBookTree : BookContentEvent

    // TOC events
    data class TocEntryExpanded(val entry: TocEntry) : BookContentEvent
    data object ToggleToc : BookContentEvent
    data class TocScrolled(val index: Int, val offset: Int) : BookContentEvent
    
    // Book tree events
    data class BookTreeScrolled(val index: Int, val offset: Int) : BookContentEvent

    // Content events
    data class LineSelected(val line: Line) : BookContentEvent
    data class LoadAndSelectLine(val lineId: Long) : BookContentEvent
    data object ToggleCommentaries : BookContentEvent
    data object ToggleLinks : BookContentEvent
    data class ContentScrolled(
        val anchorId: Long,
        val anchorIndex: Int,
        val scrollIndex: Int,
        val scrollOffset: Int
    ) : BookContentEvent
    data object NavigateToPreviousLine : BookContentEvent
    data object NavigateToNextLine : BookContentEvent
    
    // Commentaries events
    data class CommentariesTabSelected(val index: Int) : BookContentEvent
    data class CommentariesScrolled(val index: Int, val offset: Int) : BookContentEvent
    data class OpenCommentaryTarget(val bookId: Long, val lineId: Long?) : BookContentEvent
    data class SelectedCommentatorsChanged(val lineId: Long, val selectedIds: Set<Long>) : BookContentEvent
    // Links events
    data class SelectedLinksSourcesChanged(val lineId: Long, val selectedIds: Set<Long>) : BookContentEvent

    // Scroll events
    data class ParagraphScrolled(val position: Int) : BookContentEvent
    data class ChapterScrolled(val position: Int) : BookContentEvent
    data class ChapterSelected(val index: Int) : BookContentEvent

    // State management
    data object SaveState : BookContentEvent
}