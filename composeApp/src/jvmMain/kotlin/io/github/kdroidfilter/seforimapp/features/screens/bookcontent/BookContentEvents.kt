package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import io.github.kdroidfilter.seforimlibrary.core.models.*

sealed interface BookContentEvent {
    // Navigation events
    data class SearchTextChanged(val text: String) : BookContentEvent
    data class CategorySelected(val category: Category) : BookContentEvent
    data class BookSelected(val book: Book) : BookContentEvent
    data object ToggleBookTree : BookContentEvent

    // TOC events
    data class TocEntryExpanded(val entry: TocEntry) : BookContentEvent
    data object ToggleToc : BookContentEvent
    data class TocScrolled(val index: Int, val offset: Int) : BookContentEvent

    // Content events
    data class LineSelected(val line: Line) : BookContentEvent
    data class LoadAndSelectLine(val lineId: Long) : BookContentEvent
    data object ToggleCommentaries : BookContentEvent

    // Scroll events
    data class ParagraphScrolled(val position: Int) : BookContentEvent
    data class ChapterScrolled(val position: Int) : BookContentEvent
    data class ChapterSelected(val index: Int) : BookContentEvent

    // State management
    data object SaveState : BookContentEvent
}