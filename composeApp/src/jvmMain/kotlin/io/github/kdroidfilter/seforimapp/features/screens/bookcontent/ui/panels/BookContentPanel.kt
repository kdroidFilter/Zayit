package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.ContentUiState
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState
import org.jetbrains.jewel.ui.component.Text
import seforimapp.composeapp.generated.resources.Res
import seforimapp.composeapp.generated.resources.select_book

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun BookContentPanel(
    selectedBook: Book?,
    contentState: ContentUiState,
    contentSplitState: SplitPaneState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        selectedBook == null -> {
            Box(
                modifier = modifier.padding(16.dp).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(Res.string.select_book))
            }
        }
        contentState.showCommentaries -> {
            EnhancedVerticalSplitPane(
                splitPaneState = contentSplitState,
                modifier = modifier,
                firstContent = {
                    BookContentView(
                        book = selectedBook,
                        lines = contentState.lines,
                        selectedLine = contentState.selectedLine,
                        onLineSelected = { line ->
                            onEvent(BookContentEvent.LineSelected(line))
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                },
                secondContent = {
                    LineCommentsView(
                        selectedLine = contentState.selectedLine,
                        commentaries = contentState.commentaries
                    )
                }
            )
        }
        else -> {
            BookContentView(
                book = selectedBook,
                lines = contentState.lines,
                selectedLine = contentState.selectedLine,
                onLineSelected = { line ->
                    onEvent(BookContentEvent.LineSelected(line))
                },
                modifier = modifier.padding(16.dp)
            )
        }
    }
}
