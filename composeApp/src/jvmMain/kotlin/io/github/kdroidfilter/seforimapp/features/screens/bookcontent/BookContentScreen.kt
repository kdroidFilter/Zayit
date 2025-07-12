package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.components.HorizontalLateralBar
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBar
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBarPosition
import io.github.kdroidfilter.seforimapp.core.presentation.icons.Bookmark
import io.github.kdroidfilter.seforimapp.core.presentation.icons.ColumnsGap
import io.github.kdroidfilter.seforimapp.core.presentation.icons.FileWarning
import io.github.kdroidfilter.seforimapp.core.presentation.icons.Filter
import io.github.kdroidfilter.seforimapp.core.presentation.icons.ListColumnsReverse
import io.github.kdroidfilter.seforimapp.core.presentation.icons.ListTree
import io.github.kdroidfilter.seforimapp.core.presentation.icons.Manage_search
import io.github.kdroidfilter.seforimapp.core.presentation.icons.NotebookPen
import io.github.kdroidfilter.seforimapp.core.presentation.icons.Print
import io.github.kdroidfilter.seforimapp.core.presentation.icons.TableOfContents
import io.github.kdroidfilter.seforimapp.core.presentation.icons.ZoomIn
import io.github.kdroidfilter.seforimapp.core.presentation.icons.ZoomOut
import io.github.kdroidfilter.seforimapp.core.presentation.utils.cursorForHorizontalResize
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.SplitPaneState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
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
    var isBookSelected by remember { mutableStateOf(false) }
    var isChapterSelected by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxSize()) {

        VerticalLateralBar(position = VerticalLateralBarPosition.Start, topContent = {
            SelectableIconButtonWithToolip(
                toolTipText = "Liste des livres", onClick = {
                    isBookSelected = !isBookSelected
                    println("isBookSelected = $isBookSelected")
                }, isSelected = isBookSelected, icon = ListTree, iconDescription = "", label = "ספרים"
            )
            SelectableIconButtonWithToolip(
                toolTipText = "Liste des livres", onClick = {
                    isChapterSelected = !isChapterSelected
                    println("isChapterSelected: $isChapterSelected")
                }, isSelected = isChapterSelected, icon = TableOfContents, iconDescription = "", label = "תוכן עניינים"
            )
        }, bottomContent = {
            SelectableIconButtonWithToolip(
                toolTipText = "Liste des livres", onClick = {
                    isChapterSelected = !isChapterSelected
                    println("isChapterSelected: $isChapterSelected")
                }, isSelected = isChapterSelected, icon = TableOfContents, iconDescription = "", label = "תוכן עניינים"
            )
            SelectableIconButtonWithToolip(
                toolTipText = "Liste des livres", onClick = {
                    isChapterSelected = !isChapterSelected
                    println("isChapterSelected: $isChapterSelected")
                }, isSelected = isChapterSelected, icon = TableOfContents, iconDescription = "", label = "תוכן עניינים"
            )
            SelectableIconButtonWithToolip(
                toolTipText = "Liste des livres", onClick = {
                    isChapterSelected = !isChapterSelected
                    println("isChapterSelected: $isChapterSelected")
                }, isSelected = isChapterSelected, icon = TableOfContents, iconDescription = "", label = "תוכן עניינים"
            )
        })

        EnhancedSplitLayouts(
            modifier = Modifier.fillMaxSize(),
            splitPaneState = state.splitPaneState,
            searchText = state.searchText,
            onSearchTextChange = { onEvents(BookContentEvents.OnSearchTextChange(it)) },
            selectedChapter = state.selectedChapter,
            onChapterSelected = { onEvents(BookContentEvents.OnChapterSelected(it)) },
            paragraphScrollState = paragraphScrollState,
            chapterScrollState = chapterScrollState
        )
    }

    VerticalLateralBar(position = VerticalLateralBarPosition.End, topContent = {
        SelectableIconButtonWithToolip(
            toolTipText = "Liste des livres", onClick = {
                isBookSelected = !isBookSelected
                println("isBookSelected = $isBookSelected")
            }, isSelected = isBookSelected, icon = ZoomIn, iconDescription = "", label = "ספרים"
        )
        SelectableIconButtonWithToolip(
            toolTipText = "Liste des livres", onClick = {
                isChapterSelected = !isChapterSelected
                println("isChapterSelected: $isChapterSelected")
            }, isSelected = isChapterSelected, icon = ZoomOut, iconDescription = "", label = "תוכן עניינים"
        )
        SelectableIconButtonWithToolip(
            toolTipText = "Liste des livres", onClick = {
                isChapterSelected = !isChapterSelected
                println("isChapterSelected: $isChapterSelected")
            }, isSelected = isChapterSelected, icon = Bookmark, iconDescription = "", label = "תוכן עניינים"
        )
        SelectableIconButtonWithToolip(
            toolTipText = "Liste des livres", onClick = {
                isChapterSelected = !isChapterSelected
                println("isChapterSelected: $isChapterSelected")
            }, isSelected = isChapterSelected, icon = Manage_search, iconDescription = "", label = "תוכן עניינים"
        )
        SelectableIconButtonWithToolip(
            toolTipText = "Liste des livres", onClick = {
                isChapterSelected = !isChapterSelected
                println("isChapterSelected: $isChapterSelected")
            }, isSelected = isChapterSelected, icon = Print, iconDescription = "", label = "תוכן עניינים"
        )
        SelectableIconButtonWithToolip(
            toolTipText = "Liste des livres", onClick = {
                isChapterSelected = !isChapterSelected
                println("isChapterSelected: $isChapterSelected")
            }, isSelected = isChapterSelected, icon = FileWarning, iconDescription = "", label = "תוכן עניינים"
        )
    }, bottomContent = {
        SelectableIconButtonWithToolip(
            toolTipText = "Liste des livres", onClick = {
                isChapterSelected = !isChapterSelected
                println("isChapterSelected: $isChapterSelected")
            }, isSelected = isChapterSelected, icon = ListColumnsReverse, iconDescription = "", label = "תוכן עניינים"
        )
        SelectableIconButtonWithToolip(
            toolTipText = "Liste des livres", onClick = {
                isChapterSelected = !isChapterSelected
                println("isChapterSelected: $isChapterSelected")
            }, isSelected = isChapterSelected, icon = ColumnsGap, iconDescription = "", label = "תוכן עניינים"
        )
        SelectableIconButtonWithToolip(
            toolTipText = "Liste des livres", onClick = {
                isChapterSelected = !isChapterSelected
                println("isChapterSelected: $isChapterSelected")
            }, isSelected = isChapterSelected, icon = Filter, iconDescription = "", label = "תוכן עניינים"
        )
        SelectableIconButtonWithToolip(
            toolTipText = "Liste des livres", onClick = {
                isChapterSelected = !isChapterSelected
                println("isChapterSelected: $isChapterSelected")
            }, isSelected = isChapterSelected, icon = NotebookPen, iconDescription = "", label = "תוכן עניינים"
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
                    modifier = Modifier.fillMaxSize().padding(16.dp)
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
                        modifier = Modifier.fillMaxWidth().verticalScroll(chapterScrollState)
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
            second(50.dp) {

                // Main content
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(paragraphScrollState)
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
                        Modifier.width(1.dp).fillMaxHeight().background(JewelTheme.globalColors.borders.disabled)
                    )
                }
                handle {
                    Box(
                        Modifier.width(5.dp).fillMaxHeight().markAsHandle().cursorForHorizontalResize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.width(1.dp).fillMaxHeight().background(JewelTheme.globalColors.borders.disabled)
                        )
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
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() }.background(
            if (isSelected) Color.Blue.copy(alpha = 0.2f)
            else Color.Transparent
        ).padding(8.dp)) {
        Text("Chapitre ${chapter + 1}")
    }
}

