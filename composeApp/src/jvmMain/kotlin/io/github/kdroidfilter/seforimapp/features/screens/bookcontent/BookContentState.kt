package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState

data class BookContentState @OptIn(ExperimentalSplitPaneApi::class) constructor(
    val splitPaneState: SplitPaneState,
    val tocSplitPaneState: SplitPaneState,
    val contentSplitPaneState: SplitPaneState, // New split pane for content and commentaries
    val searchText: String,
    val paragraphScrollPosition: Int,
    val chapterScrollPosition: Int,
    val selectedChapter: Int,
    val showCommentaries: Boolean = false, // Flag to show/hide commentaries
    val showBookTree: Boolean = true, // Flag to show/hide book tree, default is true

    // Database-related state
    val rootCategories: List<Category> = emptyList(),
    val expandedCategories: Set<Long> = emptySet(),
    val categoryChildren: Map<Long, List<Category>> = emptyMap(),
    val booksInCategory: Set<Book> = emptySet(),
    val selectedCategory: Category? = null,
    val selectedBook: Book? = null,
    val bookLines: List<Line> = emptyList(),
    val selectedLine: Line? = null,
    val tocEntries: List<TocEntry> = emptyList(),
    val expandedTocEntries: Set<Long> = emptySet(),
    val tocChildren: Map<Long, List<TocEntry>> = emptyMap(),
    val commentaries: List<CommentaryWithText> = emptyList(), // Commentaries for the selected line
    val isLoading: Boolean = false
)

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun rememberBookContentState(viewModel: BookContentViewModel): BookContentState {
    return BookContentState(
        splitPaneState = viewModel.splitPaneState.collectAsState().value,
        tocSplitPaneState = viewModel.tocSplitPaneState.collectAsState().value,
        contentSplitPaneState = viewModel.contentSplitPaneState.collectAsState().value,
        searchText = viewModel.searchText.collectAsState().value,
        paragraphScrollPosition = viewModel.paragraphScrollPosition.collectAsState().value,
        chapterScrollPosition = viewModel.chapterScrollPosition.collectAsState().value,
        selectedChapter = viewModel.selectedChapter.collectAsState().value,
        showCommentaries = viewModel.showCommentaries.collectAsState().value,
        showBookTree = viewModel.showBookTree.collectAsState().value,

        // Database-related state
        rootCategories = viewModel.rootCategories.collectAsState().value,
        expandedCategories = viewModel.expandedCategories.collectAsState().value,
        categoryChildren = viewModel.categoryChildren.collectAsState().value,
        booksInCategory = viewModel.booksInCategory.collectAsState().value,
        selectedCategory = viewModel.selectedCategory.collectAsState().value,
        selectedBook = viewModel.selectedBook.collectAsState().value,
        bookLines = viewModel.bookLines.collectAsState().value,
        selectedLine = viewModel.selectedLine.collectAsState().value,
        tocEntries = viewModel.tocEntries.collectAsState().value,
        expandedTocEntries = viewModel.expandedTocEntries.collectAsState().value,
        tocChildren = viewModel.tocChildren.collectAsState().value,
        commentaries = viewModel.commentaries.collectAsState().value,
        isLoading = viewModel.isLoading.collectAsState().value
    )
}
