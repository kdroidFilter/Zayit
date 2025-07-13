package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBar
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBarPosition
import io.github.kdroidfilter.seforimapp.core.presentation.icons.*
import io.github.kdroidfilter.seforimapp.core.presentation.utils.cursorForHorizontalResize
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.SplitPaneState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BookContentScreen() {
    val viewModel: BookContentViewModel = koinViewModel()
    val state = rememberBookContentState(viewModel)
    BookContentView(state, viewModel::onEvent)

}

@OptIn(ExperimentalSplitPaneApi::class, ExperimentalFoundationApi::class)
@Composable
fun BookContentView(state: BookContentState, onEvents: (BookContentEvents) -> Unit) {
    // Local scroll state
    val paragraphScrollState = rememberScrollState(state.paragraphScrollPosition)

    val chapterScrollState = rememberScrollState(state.chapterScrollPosition)

    // Save scroll position when it changes
    LaunchedEffect(paragraphScrollState) {
        snapshotFlow { paragraphScrollState.value }.collect { position ->
            onEvents(BookContentEvents.OnUpdateParagraphScrollPosition(position))
        }
    }

    LaunchedEffect(chapterScrollState) {
        snapshotFlow { chapterScrollState.value }.collect { position ->
            onEvents(BookContentEvents.OnUpdateChapterScrollPosition(position))
        }
    }

    // Save split pane position when it changes
    LaunchedEffect(state.splitPaneState.positionPercentage) {
        snapshotFlow { state.splitPaneState.positionPercentage }.collect {
            onEvents(BookContentEvents.SaveAllStates)
        }
    }

    // Save all states when the screen is disposed
    DisposableEffect(Unit) {
        onDispose {
            onEvents(BookContentEvents.SaveAllStates)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        StartVerticalBar()
        EnhancedSplitLayouts(
            modifier = Modifier.weight(1f),
            splitPaneState = state.splitPaneState,
            searchText = state.searchText,
            onSearchTextChange = { onEvents(BookContentEvents.OnSearchTextChange(it)) },
            selectedChapter = state.selectedChapter,
            onChapterSelected = { onEvents(BookContentEvents.OnChapterSelected(it)) },
            paragraphScrollState = paragraphScrollState,
            chapterScrollState = chapterScrollState
        )
        EndVerticalBar()
    }
}


@Composable
fun StartVerticalBar() {
    VerticalLateralBar(
        position = VerticalLateralBarPosition.Start,
        topContentLabel = "ניווט",
        topContent = {
            SelectableIconButtonWithToolip(
                toolTipText = "רשימת הספרים",
                onClick = {

                },
                isSelected = false,
                icon = Library,
                iconDescription = "",
                label = "ספרים"
            )
            SelectableIconButtonWithToolip(
                toolTipText = "תוכן הספר",
                onClick = {

                },
                isSelected = false,
                icon = TableOfContents,
                iconDescription = "",
                label = "תוכן עניינים של הספר"
            )
        },
        bottomContentLabel = "אישי",
        bottomContent = {
            SelectableIconButtonWithToolip(
                toolTipText = "הסימניות שלי",
                onClick = {

                },
                isSelected = false,
                icon = JournalBookmark,
                iconDescription = "",
                label = "סימניות"
            )
            SelectableIconButtonWithToolip(
                toolTipText = "הפירושים שלי",
                onClick = {

                },
                isSelected = false,
                icon = JournalText,
                iconDescription = "",
                label = "פירושים שלי"
            )
        })

}

@Composable
fun EndVerticalBar() {
    VerticalLateralBar(
        position = VerticalLateralBarPosition.End,
        topContentLabel = "כלים",
        topContent = {
            SelectableIconButtonWithToolip(
                toolTipText = "הגדל גודל הטקסט",
                onClick = {

                },
                isSelected = false,
                icon = ZoomIn,
                iconDescription = "",
                label = "הגדל"
            )
            SelectableIconButtonWithToolip(
                toolTipText = "הקטן גודל הטקסט",
                onClick = {

                },
                isSelected = false,
                icon = ZoomOut,
                iconDescription = "",
                label = "הקטן"
            )
            SelectableIconButtonWithToolip(
                toolTipText = "",
                onClick = {

                },
                isSelected = false,
                icon = Bookmark,
                iconDescription = "",
                label = "הוסף סימנייה"
            )
            SelectableIconButtonWithToolip(
                toolTipText = "הפש בתוך העמוד",
                onClick = {

                },
                isSelected = false,
                icon = Manage_search,
                iconDescription = "",
                label = "הפש בעמוד"
            )
            SelectableIconButtonWithToolip(
                toolTipText = "",
                onClick = {

                },
                isSelected = false,
                icon = Print,
                iconDescription = "",
                label = "הדפס"
            )
            SelectableIconButtonWithToolip(
                toolTipText = "",
                onClick = {

                },
                isSelected = false,
                icon = FileWarning,
                iconDescription = "",
                label = "דיווח"
            )
        }, bottomContentLabel = "פירושים", // "Commentaires" en hébreu
        bottomContent = {
            SelectableIconButtonWithToolip(
                toolTipText = "הצעג את הפירושים הקיימים על שורה זאת",
                onClick = {

                },
                isSelected = false,
                icon = ListColumnsReverse,
                iconDescription = "",
                label = "הציג פירושים"
            )
            SelectableIconButtonWithToolip(
                toolTipText = "",
                onClick = {

                },
                isSelected = false,
                icon = ColumnsGap,
                iconDescription = "",
                label = ""
            )
            SelectableIconButtonWithToolip(
                toolTipText = "סנן את הפרשנים",
                onClick = {

                },
                isSelected = false,
                icon = Filter,
                iconDescription = "",
                label = "סנן"
            )
            SelectableIconButtonWithToolip(
                toolTipText = "כתוב הערה על שורה זאת",
                onClick = {

                },
                isSelected = false, icon = NotebookPen,
                iconDescription = "",
                label = "כתוב הערה"
            )
        })
}

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun EnhancedSplitLayouts(
    modifier: Modifier = Modifier,
    splitPaneState: SplitPaneState,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    selectedChapter: Int,
    onChapterSelected: (Int) -> Unit,
    paragraphScrollState: ScrollState,
    chapterScrollState: ScrollState
) {
    Column(modifier = modifier) {
        HorizontalSplitPane(
            splitPaneState = splitPaneState
        ) {
            first(200.dp) {
                // Navigation panel
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                ) {
                    // Search field
                    val searchFieldState = rememberTextFieldState(searchText)

                    // Synchronisez l'état avec le viewmodel
                    LaunchedEffect(searchText) {
                        if (searchFieldState.text.toString() != searchText) {
                            searchFieldState.edit {
                                replace(0, length, searchText)
                            }
                        }
                    }

                    LaunchedEffect(searchFieldState.text) {
                        onSearchTextChange(searchFieldState.text.toString())
                    }

                    TextField(
                        state = searchFieldState,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Rechercher") }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Chapter list
                    Column(
                        modifier = Modifier
                            .verticalScroll(chapterScrollState)
                    ) {
                        repeat(20) { index ->
                            ChapterItem(
                                chapter = index,
                                isSelected = selectedChapter == index,
                                onClick = { onChapterSelected(index) })
                        }
                    }
                }
            }
            second(200.dp) {

                // Main content
                Column(
                    modifier = Modifier
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
                    Divider(
                        Orientation.Vertical,
                        Modifier.fillMaxHeight().width(1.dp),
                        color = JewelTheme.globalColors.borders.disabled
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

                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterItem(
    chapter: Int, isSelected: Boolean, onClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() }.background(
            if (isSelected) Color.Blue.copy(alpha = 0.2f)
            else Color.Transparent
        ).padding(8.dp)
    ) {
        Text("Chapitre ${chapter + 1}")
    }
}