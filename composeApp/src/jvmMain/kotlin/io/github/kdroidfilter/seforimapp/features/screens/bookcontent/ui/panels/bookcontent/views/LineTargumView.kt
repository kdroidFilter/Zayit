package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.bookcontent.views

import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state.BookContentUiState

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import app.cash.paging.PagingData
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.RadioButtonChip
import org.jetbrains.jewel.ui.component.Text
import seforimapp.composeapp.generated.resources.*

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun LineTargumView(
    selectedLine: Line?,
    buildLinksPagerFor: (Long, Long?) -> Flow<PagingData<CommentaryWithText>>,
    getAvailableLinksForLine: suspend (Long) -> Map<String, Long>,
    commentariesScrollIndex: Int = 0,
    commentariesScrollOffset: Int = 0,
    initiallySelectedSourceIds: Set<Long> = emptySet(),
    onSelectedSourcesChange: (Set<Long>) -> Unit = {},
    onLinkClick: (CommentaryWithText) -> Unit = {},
    onScroll: (Int, Int) -> Unit = { _, _ -> }
) {
    val rawTextSize by AppSettings.textSizeFlow.collectAsState()
    val commentTextSize by animateFloatAsState(
        targetValue = rawTextSize * 0.875f,
        animationSpec = tween(durationMillis = 300),
        label = "linkTextSizeAnim"
    )
    val rawLineHeight by AppSettings.lineHeightFlow.collectAsState()
    val lineHeight by animateFloatAsState(
        targetValue = rawLineHeight,
        animationSpec = tween(durationMillis = 300),
        label = "linkLineHeightAnim"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.links),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        when (selectedLine) {
            null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(Res.string.select_line_for_links))
                }
            }

            else -> {
                var titleToIdMap by remember(selectedLine.id) { mutableStateOf<Map<String, Long>>(emptyMap()) }
                LaunchedEffect(selectedLine.id) {
                    runCatching { getAvailableLinksForLine(selectedLine.id) }
                        .onSuccess { map -> titleToIdMap = map }
                        .onFailure { titleToIdMap = emptyMap() }
                }

                if (titleToIdMap.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(Res.string.no_links_for_line))
                    }
                } else {
                    val availableSources = remember(titleToIdMap) { titleToIdMap.keys.sorted().toList() }

                    var selectedSource by remember(availableSources) { mutableStateOf<String?>(null) }

                    // Initialize from initiallySelectedSourceIds (pick first match)
                    LaunchedEffect(initiallySelectedSourceIds, titleToIdMap) {
                        if (selectedSource == null && initiallySelectedSourceIds.isNotEmpty() && titleToIdMap.isNotEmpty()) {
                            val firstMatch =
                                titleToIdMap.firstNotNullOfOrNull { (name, id) -> name.takeIf { id in initiallySelectedSourceIds } }
                            if (firstMatch != null) selectedSource = firstMatch
                        }
                    }

                    // Keep selection valid when list changes
                    LaunchedEffect(availableSources) {
                        if (selectedSource != null && selectedSource !in availableSources) {
                            selectedSource = null
                        }
                    }

                    // Emit selection as a single-ID set
                    LaunchedEffect(selectedSource, titleToIdMap) {
                        val id = selectedSource?.let { titleToIdMap[it] }
                        onSelectedSourcesChange(if (id != null) setOf(id) else emptySet())
                    }

                    val splitState = rememberSplitPaneState(0.2f)

                    _root_ide_package_.io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.EnhancedVerticalSplitPane(
                        splitPaneState = splitState,
                        firstMinSize = 120f,
                        firstContent = {
                            val id = selectedSource?.let { titleToIdMap[it] }
                            if (id == null) {
                                CenteredMessage(message = stringResource(Res.string.select_at_least_one_source))
                            } else {
                                PagedLinksList(
                                    buildLinksPagerFor = buildLinksPagerFor,
                                    lineId = selectedLine.id,
                                    sourceBookId = id,
                                    isPrimary = true,
                                    initialIndex = commentariesScrollIndex,
                                    initialOffset = commentariesScrollOffset,
                                    onScroll = onScroll,
                                    onLinkClick = onLinkClick,
                                    commentTextSize = commentTextSize,
                                    lineHeight = lineHeight
                                )
                            }
                        },
                        secondContent = {
                            ChipsSourcesListView(
                                sources = availableSources,
                                selected = selectedSource,
                                onSelected = { name -> selectedSource = name },
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LineTargumView(
    uiState: BookContentUiState,
    onEvent: (BookContentEvent) -> Unit,
) {
    val providers = uiState.providers ?: return
    val contentState = uiState.content
    LineTargumView(
        selectedLine = contentState.selectedLine,
        buildLinksPagerFor = providers.buildLinksPagerFor,
        getAvailableLinksForLine = providers.getAvailableLinksForLine,
        commentariesScrollIndex = contentState.commentariesScrollIndex,
        commentariesScrollOffset = contentState.commentariesScrollOffset,
        initiallySelectedSourceIds = contentState.selectedTargumSourceIds,
        onSelectedSourcesChange = { ids ->
            contentState.selectedLine?.let { line ->
                onEvent(BookContentEvent.SelectedTargumSourcesChanged(line.id, ids))
            }
        },
        onLinkClick = { commentary ->
            onEvent(
                BookContentEvent.OpenCommentaryTarget(
                    bookId = commentary.link.targetBookId, lineId = commentary.link.targetLineId
                )
            )
        },
        onScroll = { index, offset ->
            onEvent(BookContentEvent.CommentariesScrolled(index, offset))
        }
    )
}

@Composable
private fun CenteredMessage(message: String, fontSize: Float = 14f) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, fontSize = fontSize.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsSourcesListView(
    sources: List<String>,
    selected: String?,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Make chips area vertically scrollable with a vertical scrollbar when content exceeds height
    Box(modifier = modifier.fillMaxSize()) {
        val scrollState = rememberScrollState()
        Column {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
                    .verticalScroll(scrollState)
            ) {
                sources.forEach { source ->
                    val isSelected = source == selected
                    RadioButtonChip(
                        selected = isSelected,
                        onClick = { onSelected(source) },
                        enabled = true
                    ) { Text(source) }
                }
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(horizontal = 4.dp),
            adapter = rememberScrollbarAdapter(scrollState)
        )
    }
}

@Composable
private fun PagedLinksList(
    buildLinksPagerFor: (Long, Long?) -> Flow<PagingData<CommentaryWithText>>,
    lineId: Long,
    sourceBookId: Long,
    isPrimary: Boolean,
    initialIndex: Int,
    initialOffset: Int,
    onScroll: (Int, Int) -> Unit,
    onLinkClick: (CommentaryWithText) -> Unit,
    commentTextSize: Float,
    lineHeight: Float
) {
    val pagerFlow: Flow<PagingData<CommentaryWithText>> = remember(lineId, sourceBookId) {
        buildLinksPagerFor(lineId, sourceBookId)
    }

    val lazyPagingItems: LazyPagingItems<CommentaryWithText> = pagerFlow.collectAsLazyPagingItems()

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialOffset
    )

    if (isPrimary) {
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (i, o) ->
                onScroll(
                    i,
                    o
                )
            }
        }
    }

    SelectionContainer {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(lazyPagingItems.itemCount) { index ->
                val item = lazyPagingItems[index]
                if (item != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                            .pointerInput(Unit) { detectTapGestures(onTap = { onLinkClick(item) }) }
                    ) {
                        Text(
                            text = item.targetText,
                            textAlign = TextAlign.Justify,
                            fontFamily = FontFamily(Font(resource = Res.font.frankruhllibre)),
                            fontSize = commentTextSize.sp,
                            lineHeight = (commentTextSize * lineHeight).sp
                        )
                    }
                }
            }

            when (val state = lazyPagingItems.loadState.append) {
                is LoadState.Error -> {
                    item { CenteredMessage(message = state.error.message ?: "Error loading more") }
                }

                is LoadState.Loading -> {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }

                else -> {}
            }
        }
    }
}
