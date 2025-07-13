package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState

data class BookContentState @OptIn(ExperimentalSplitPaneApi::class) constructor(
    val splitPaneState: SplitPaneState,
    val tocSplitPaneState: SplitPaneState,
    val searchText: String,
    val paragraphScrollPosition: Int,
    val chapterScrollPosition: Int,
    val selectedChapter: Int,

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
    val isLoading: Boolean = false
)

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun rememberBookContentState(viewModel: BookContentViewModel): BookContentState {
    return BookContentState(
        splitPaneState = viewModel.splitPaneState.collectAsState().value,
        tocSplitPaneState = viewModel.tocSplitPaneState.collectAsState().value,
        searchText = viewModel.searchText.collectAsState().value,
        paragraphScrollPosition = viewModel.paragraphScrollPosition.collectAsState().value,
        chapterScrollPosition = viewModel.chapterScrollPosition.collectAsState().value,
        selectedChapter = viewModel.selectedChapter.collectAsState().value,

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
        isLoading = viewModel.isLoading.collectAsState().value
    )
}
