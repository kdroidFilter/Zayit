package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.LineConnectionsSnapshot
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.links
import seforimapp.seforimapp.generated.resources.no_links_for_line
import seforimapp.seforimapp.generated.resources.select_line_for_links

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
    onScroll: (Int, Int) -> Unit = { _, _ -> },
    onHide: () -> Unit = {},
    highlightQuery: String = "",
    lineConnections: Map<Long, LineConnectionsSnapshot> = emptyMap(),
    availabilityType: ConnectionType = ConnectionType.TARGUM,
    fontCodeFlow: StateFlow<String> = AppSettings.targumFontCodeFlow,
    titleRes: StringResource = Res.string.links,
    selectLineRes: StringResource = Res.string.select_line_for_links,
    emptyRes: StringResource = Res.string.no_links_for_line,
    showDiacritics: Boolean
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

    // Selected font for targumim
    val targumFontCode by fontCodeFlow.collectAsState()
    val targumFontFamily = FontCatalog.familyFor(targumFontCode)
    val boldScaleForPlatform = remember(targumFontCode) {
        val lacksBold = targumFontCode in setOf("notoserifhebrew", "notorashihebrew", "frankruhllibre")
        if (PlatformInfo.isMacOS && lacksBold) 1.08f else 1.0f
    }

    val paneInteractionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .hoverable(paneInteractionSource)
    ) {

        PaneHeader(
            label = stringResource(titleRes),
            interactionSource = paneInteractionSource,
            onHide = onHide
        )

        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            when (selectedLine) {
                null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(selectLineRes))
                    }
                }

                else -> {
                    val cachedSources = remember(selectedLine.id, lineConnections, availabilityType) {
                        lineConnections[selectedLine.id]?.let { snapshot ->
                            when (availabilityType) {
                                ConnectionType.SOURCE -> snapshot.sources
                                else -> snapshot.targumSources
                            }
                        }
                    }

                    var titleToIdMap by remember(selectedLine.id, cachedSources) {
                        mutableStateOf<Map<String, Long>>(cachedSources ?: emptyMap())
                    }

                    LaunchedEffect(selectedLine.id, lineConnections) {
                        val cached = lineConnections[selectedLine.id]?.let { snapshot ->
                            when (availabilityType) {
                                ConnectionType.SOURCE -> snapshot.sources
                                else -> snapshot.targumSources
                            }
                        }
                        if (cached != null) {
                            titleToIdMap = cached
                            return@LaunchedEffect
                        }

                        runCatching { getAvailableLinksForLine(selectedLine.id) }
                            .onSuccess { map -> titleToIdMap = map }
                            .onFailure { titleToIdMap = emptyMap() }
                    }

                    if (titleToIdMap.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = stringResource(emptyRes))
                        }
                    } else {
                        val availableSources = remember(titleToIdMap) {
                            titleToIdMap.entries
                                .sortedBy { it.key }
                                .map { SourceMeta(it.key, it.value) }
                        }

                        val selectedSources = remember(titleToIdMap, initiallySelectedSourceIds) {
                            val availableIds = availableSources.map { it.bookId }.toSet()
                            val initial = initiallySelectedSourceIds.ifEmpty { availableIds }
                            initial.intersect(availableIds)
                        }

                        LaunchedEffect(selectedSources) {
                            onSelectedSourcesChange(selectedSources)
                        }

                        val sourceSections = availableSources.mapNotNull { meta ->
                            val pagerFlow = remember(selectedLine.id, meta.bookId) {
                                buildLinksPagerFor(selectedLine.id, meta.bookId).distinctUntilChanged()
                            }
                            val lazyPagingItems = pagerFlow.collectAsLazyPagingItems()
                            SourceSection(
                                title = meta.title,
                                bookId = meta.bookId,
                                items = lazyPagingItems
                            )
                        }

                        val listState = rememberSaveable(
                            selectedLine.id,
                            saver = LazyListState.Saver
                        ) {
                            LazyListState(
                                firstVisibleItemIndex = commentariesScrollIndex,
                                firstVisibleItemScrollOffset = commentariesScrollOffset
                            )
                        }

                        LaunchedEffect(listState) {
                            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                                .distinctUntilChanged()
                                .collect { (index, offset) -> onScroll(index, offset) }
                        }

                        SelectionContainer {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                sourceSections.forEach { section ->
                                    item(key = "header-${section.bookId}") {
                                        Text(
                                            text = section.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (commentTextSize * 1.1f).sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    items(
                                        count = section.items.itemCount,
                                        key = { index ->
                                            section.items.peek(index)?.link?.id ?: "source-${section.bookId}-$index"
                                        }
                                    ) { index ->
                                        section.items[index]?.let { item ->
                                            LinkItem(
                                                linkId = item.link.id,
                                                targetText = item.targetText,
                                                commentTextSize = commentTextSize,
                                                lineHeight = lineHeight,
                                                fontFamily = targumFontFamily,
                                                boldScale = boldScaleForPlatform,
                                                highlightQuery = highlightQuery,
                                                onClick = { onLinkClick(item) },
                                                showDiacritics = showDiacritics
                                            )
                                        }
                                    }

                                    when (val state = section.items.loadState.append) {
                                        is LoadState.Error -> item(key = "append-error-${section.bookId}") {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = state.error.message ?: "Error loading more")
                                            }
                                        }

                                        is LoadState.Loading -> item(key = "append-loading-${section.bookId}") {
                                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator()
                                            }
                                        }

                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Stable
@Composable
fun LineTargumView(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    lineConnections: Map<Long, LineConnectionsSnapshot> = emptyMap(),
    availabilityType: ConnectionType = ConnectionType.TARGUM,
    showDiacritics: Boolean
) {
    val providers = uiState.providers ?: return
    val contentState = uiState.content
    val windowInfo = LocalWindowInfo.current
    val findQuery by AppSettings.findQueryFlow(uiState.tabId).collectAsState("")
    val showFind by AppSettings.findBarOpenFlow(uiState.tabId).collectAsState()
    val activeQuery = if (showFind) findQuery else ""

    val onSelectedSourcesChange = remember(contentState.selectedLine) {
        { ids: Set<Long> ->
            contentState.selectedLine?.let { line ->
                onEvent(BookContentEvent.SelectedTargumSourcesChanged(line.id, ids))
            }
            Unit
        }
    }

    val onLinkClick = remember(windowInfo) {
        { commentary: CommentaryWithText ->
            val mods = windowInfo.keyboardModifiers
            if (mods.isCtrlPressed || mods.isMetaPressed) {
                onEvent(
                    BookContentEvent.OpenCommentaryTarget(
                        bookId = commentary.link.targetBookId,
                        lineId = commentary.link.targetLineId
                    )
                )
            }
        }
    }

    val onScroll = remember {
        { index: Int, offset: Int ->
            onEvent(BookContentEvent.CommentariesScrolled(index, offset))
        }
    }

    val onHide = remember {
        { onEvent(BookContentEvent.ToggleTargum) }
    }

    LineTargumView(
        selectedLine = contentState.selectedLine,
        buildLinksPagerFor = providers.buildLinksPagerFor,
        getAvailableLinksForLine = providers.getAvailableLinksForLine,
        commentariesScrollIndex = contentState.commentariesScrollIndex,
        commentariesScrollOffset = contentState.commentariesScrollOffset,
        initiallySelectedSourceIds = contentState.selectedTargumSourceIds,
        onSelectedSourcesChange = onSelectedSourcesChange,
        onLinkClick = onLinkClick,
        onScroll = onScroll,
        onHide = onHide,
        highlightQuery = activeQuery,
        lineConnections = lineConnections,
        availabilityType = availabilityType,
        showDiacritics = showDiacritics
    )
}

private data class SourceSection(
    val title: String,
    val bookId: Long,
    val items: LazyPagingItems<CommentaryWithText>
)

private data class SourceMeta(
    val title: String,
    val bookId: Long
)

@Composable
private fun LinkItem(
    linkId: Long,
    targetText: String,
    commentTextSize: Float,
    lineHeight: Float,
    fontFamily: FontFamily,
    boldScale: Float = 1.0f,
    highlightQuery: String,
    onClick: () -> Unit,
    showDiacritics: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .pointerInput(linkId) {
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        val processedText = remember(linkId, targetText, showDiacritics) {
            if (showDiacritics) targetText else HebrewTextUtils.removeAllDiacritics(targetText)
        }

        // Footnote marker color from theme
        val footnoteMarkerColor = JewelTheme.globalColors.outlines.focused

        val annotated = remember(linkId, processedText, commentTextSize, boldScale, showDiacritics, footnoteMarkerColor) {
            buildAnnotatedFromHtml(
                processedText,
                commentTextSize,
                boldScale = if (boldScale < 1f) 1f else boldScale,
                footnoteMarkerColor = footnoteMarkerColor
            )
        }

        // Highlight occurrences using the current tab's find-in-page query
        val display: AnnotatedString = remember(annotated, highlightQuery) {
            io.github.kdroidfilter.seforimapp.core.presentation.text.highlightAnnotated(annotated, highlightQuery)
        }

        Text(
            text = display,
            textAlign = TextAlign.Justify,
            fontFamily = fontFamily,
            lineHeight = (commentTextSize * lineHeight).sp
        )
    }
}
