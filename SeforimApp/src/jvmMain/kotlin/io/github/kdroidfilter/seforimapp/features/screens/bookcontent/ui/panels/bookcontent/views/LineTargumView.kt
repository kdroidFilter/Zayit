package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import app.cash.paging.PagingData
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state.BookContentUiState
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.*

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

    Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp, start = 8.dp, end = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(Res.string.links),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 16.dp),
                textDecoration = TextDecoration.Underline,
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

                    // We no longer select a single source; display all sources sequentially.
                    // Emit all available source IDs so state stays consistent.
                    LaunchedEffect(titleToIdMap) {
                        onSelectedSourcesChange(titleToIdMap.values.toSet())
                    }

                    // Stack all targum blocks vertically with commentator name above each block.
                    // Each block contains its own scrollable list with a bounded height.
                    val outerScroll = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(outerScroll),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        availableSources.forEachIndexed { index, source ->
                            val id = titleToIdMap[source]
                            if (id != null) {

                                // Header: commentator name
                                Text(
                                    text = source,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                )

                                PagedLinksList(
                                    buildLinksPagerFor = buildLinksPagerFor,
                                    lineId = selectedLine.id,
                                    sourceBookId = id,
                                    isPrimary = index == 0,
                                    initialIndex = if (index == 0) commentariesScrollIndex else 0,
                                    initialOffset = if (index == 0) commentariesScrollOffset else 0,
                                    onScroll = onScroll,
                                    onLinkClick = onLinkClick,
                                    commentTextSize = commentTextSize,
                                    lineHeight = lineHeight
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

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
        val windowInfo = LocalWindowInfo.current
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
            val mods = windowInfo.keyboardModifiers
            if (mods.isCtrlPressed || mods.isMetaPressed) {
                onEvent(
                    BookContentEvent.OpenCommentaryTarget(
                        bookId = commentary.link.targetBookId, lineId = commentary.link.targetLineId
                    )
                )
            }
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

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
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

    // Column + ScrollState au lieu de LazyColumn + LazyListState
    val scrollState = rememberScrollState(initial = if (isPrimary) initialOffset else 0)

    if (isPrimary) {
        LaunchedEffect(scrollState) {
            snapshotFlow { scrollState.value }.collect { o ->
                onScroll(0, o)
            }
        }
    }

    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 0.dp, max = 480.dp)
                .verticalScroll(scrollState)
        ) {
            repeat(lazyPagingItems.itemCount) { index ->
                val item = lazyPagingItems[index]
                if (item != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                            .pointerInput(Unit) { detectTapGestures(onTap = { onLinkClick(item) }) }
                    ) {
                        // Unified HTML to AnnotatedString
                        val annotated = remember(item.link.id, item.targetText, commentTextSize) {
                            buildAnnotatedFromHtml(
                                item.targetText,
                                commentTextSize
                            )
                        }
                        Text(
                            text = annotated,
                            textAlign = TextAlign.Justify,
                            fontFamily = FontFamily(Font(resource = Res.font.frankruhllibre)),
                            lineHeight = (commentTextSize * lineHeight).sp
                        )
                    }
                }
            }

            when (val state = lazyPagingItems.loadState.append) {
                is LoadState.Error -> {
                    CenteredMessage(message = state.error.message ?: "Error loading more")
                }

                is LoadState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                else -> {}
            }
        }
    }
}
