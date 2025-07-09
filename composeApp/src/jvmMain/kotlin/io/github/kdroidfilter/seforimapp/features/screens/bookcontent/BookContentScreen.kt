package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.SplitPaneState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.koin.compose.viewmodel.koinViewModel
import java.awt.Cursor

@Composable
fun BookContentScreen() {
    val viewModel: BookContentViewModel = koinViewModel()
    val state = rememberBookContentState(viewModel)
    BookContentView(state, viewModel::onEvent)

}

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun BookContentView(state: BookContentState, onEvents: (BookContentEvents) -> Unit) {
    // Local scroll state
    val paragraphScrollState = rememberScrollState(state.paragraphScrollPosition)

    val chapterScrollState = rememberScrollState(state.chapterScrollPosition)

    // Save scroll position when it changes
    LaunchedEffect(paragraphScrollState) {
        snapshotFlow { paragraphScrollState.value }
            .collect { position ->
                onEvents(BookContentEvents.OnUpdateParagraphScrollPosition(position))
            }
    }

    LaunchedEffect(chapterScrollState) {
        snapshotFlow { chapterScrollState.value }
            .collect { position ->
                onEvents(BookContentEvents.OnUpdateChapterScrollPosition(position))
            }
    }

    // Save split pane position when it changes
    LaunchedEffect(state.splitPaneState.positionPercentage) {
        snapshotFlow { state.splitPaneState.positionPercentage }
            .collect {
                onEvents(BookContentEvents.SaveAllStates)
            }
    }

    // Save all states when the screen is disposed
    DisposableEffect(Unit) {
        onDispose {
            onEvents(BookContentEvents.SaveAllStates)
        }
    }

    EnhancedSplitLayouts(
        splitPaneState = state.splitPaneState,
        searchText = state.searchText,
        onSearchTextChange = { onEvents(BookContentEvents.OnSearchTextChange(it)) },
        selectedChapter = state.selectedChapter,
        onChapterSelected = { onEvents(BookContentEvents.OnChapterSelected(it)) },
        paragraphScrollState = paragraphScrollState,
        chapterScrollState = chapterScrollState
    )
}

private fun Modifier.cursorForHorizontalResize(): Modifier =
    pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun EnhancedSplitLayouts(
    splitPaneState: SplitPaneState,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    selectedChapter: Int,
    onChapterSelected: (Int) -> Unit,
    paragraphScrollState: ScrollState,
    chapterScrollState: ScrollState
) {
    Column(Modifier.fillMaxSize()) {
        HorizontalSplitPane(
            splitPaneState = splitPaneState
        ) {
            first(200.dp) {
                // Navigation panel
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Search field
                    TextField(
                        value = searchText,
                        onValueChange = onSearchTextChange,
                        label = { Text("Rechercher") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Chapter list
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(chapterScrollState)
                    ) {
                        repeat(20) { index ->
                            ChapterItem(
                                chapter = index,
                                isSelected = selectedChapter == index,
                                onClick = { onChapterSelected(index) }
                            )
                        }
                    }
                }
            }
            second(50.dp) {
                // Main content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(paragraphScrollState)
                ) {
                    Text("Chapitre $selectedChapter")
                    Spacer(modifier = Modifier.height(16.dp))

                    // Simulated content
                    repeat(100) { index ->
                        Text(
                            "Paragraphe $index du chapitre $selectedChapter",
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
            splitter {
                visiblePart {
                    Box(
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(JewelTheme.globalColors.borders.disabled)
                    )
                }
                handle {
                    Box(
                        Modifier
                            .width(5.dp)
                            .fillMaxHeight()
                            .markAsHandle()
                            .cursorForHorizontalResize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(JewelTheme.globalColors.borders.disabled)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterItem(
    chapter: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
            .background(
                if (isSelected) Color.Blue.copy(alpha = 0.2f)
                else Color.Transparent
            )
            .padding(8.dp)
    ) {
        Text("Chapitre ${chapter + 1}")
    }
}