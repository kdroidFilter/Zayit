package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.icons.ListTree
import io.github.kdroidfilter.seforimapp.core.presentation.icons.TableOfContents
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.SplitPaneState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.SelectableIconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.PainterHint
import org.koin.compose.viewmodel.koinViewModel
import java.awt.Cursor
import java.awt.Label

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

    Row(modifier = Modifier.fillMaxSize()) {


        Row(modifier = Modifier.width(70.dp).fillMaxHeight(), horizontalArrangement = Arrangement.Start) {
            Column {
                Box(
                    modifier = Modifier.fillMaxHeight(0.5f).fillMaxWidth().padding(4.dp),
                    contentAlignment = Alignment.TopCenter
                ) {

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        var isSelected by mutableStateOf(true)
                        SelectableIconButtonWithToolip(
                            toolTipText = "Liste des livres",
                            onClick = {
                                isSelected = !isSelected
                            },
                            isSelected = isSelected,
                            icon = ListTree,
                            iconDescription = "",
                            label = "ספרים"
                        )
                        SelectableIconButtonWithToolip(
                            toolTipText = "Liste des livres",
                            onClick = {
                                isSelected = !isSelected
                            },
                            isSelected = isSelected,
                            icon = TableOfContents,
                            iconDescription = "",
                            label = "תוכן עניינים"
                        )
                    }


                }
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    SelectableIconActionButton(
                        key = AllIconsKeys.General.MoreTabs,
                        contentDescription = "Linux",
                        selected = false,
                        iconModifier = Modifier.size(24.dp),
                        modifier = Modifier.size(32.dp),
                        colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.selected),
                        onClick = {

                        }
                    )
                }

            }

        }
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
    Row(
        modifier = Modifier.width(32.dp).fillMaxHeight().background(Color.Red),
        horizontalArrangement = Arrangement.Start
    ) {}

}

private fun Modifier.cursorForHorizontalResize(): Modifier =
    pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))

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
    Row(modifier = modifier) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableIconButtonWithToolip(
    toolTipText: String,
    onClick: () -> Unit,
    isSelected: Boolean,
    icon : ImageVector,
    iconDescription : String = "",
    label: String
){
    Tooltip({
        Text(toolTipText)
    }) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .background(
                   if (isSelected) JewelTheme.globalColors.borders.focused else JewelTheme.globalColors.panelBackground,
                    shape = RoundedCornerShape(4.dp)
                )
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(4.dp)
            ) {
                Icon(
                    icon,
                    iconDescription,
                    tint = JewelTheme.globalColors.text.selected,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    label,
                    color = JewelTheme.globalColors.text.normal,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}