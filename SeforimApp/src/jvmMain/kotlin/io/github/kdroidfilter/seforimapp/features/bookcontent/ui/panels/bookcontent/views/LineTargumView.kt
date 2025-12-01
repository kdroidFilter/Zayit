package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
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
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
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
    emptyRes: StringResource = Res.string.no_links_for_line
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
        val isMac = System.getProperty("os.name")?.contains("Mac", ignoreCase = true) == true
        val lacksBold = targumFontCode in setOf("notoserifhebrew", "notorashihebrew", "frankruhllibre")
        if (isMac && lacksBold) 1.08f else 1.0f
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
                        val availableSources by remember(titleToIdMap) {
                            derivedStateOf {
                                titleToIdMap.keys.sorted().toList()
                            }
                        }

                        LaunchedEffect(titleToIdMap) {
                            val ids = titleToIdMap.values.toSet()
                            if (ids != initiallySelectedSourceIds) {
                                onSelectedSourcesChange(ids)
                            }
                        }

                        val outerScroll = rememberSaveable(
                            selectedLine.id,
                            commentariesScrollIndex,
                            saver = ScrollState.Saver
                        ) {
                            ScrollState(commentariesScrollOffset)
                        }

                        LaunchedEffect(outerScroll) {
                            snapshotFlow { outerScroll.value }
                                .distinctUntilChanged()
                                .collect { offset -> onScroll(0, offset) }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(outerScroll),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            availableSources.forEach { source ->
                                titleToIdMap[source]?.let { id ->
                                    key(id) {
                                        // Header: commentator name
                                        Text(
                                            text = source,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (commentTextSize * 1.1f).sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        PagedLinksList(
                                            buildLinksPagerFor = buildLinksPagerFor,
                                            lineId = selectedLine.id,
                                            sourceBookId = id,
                                            onLinkClick = onLinkClick,
                                            commentTextSize = commentTextSize,
                                            lineHeight = lineHeight,
                                            fontFamily = targumFontFamily,
                                            boldScale = boldScaleForPlatform,
                                            highlightQuery = highlightQuery
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
    }
}

@Stable
@Composable
fun LineTargumView(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    lineConnections: Map<Long, LineConnectionsSnapshot> = emptyMap(),
    availabilityType: ConnectionType = ConnectionType.TARGUM
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
        availabilityType = availabilityType
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
    onLinkClick: (CommentaryWithText) -> Unit,
    commentTextSize: Float,
    lineHeight: Float,
    fontFamily: FontFamily,
    boldScale: Float = 1.0f,
    highlightQuery: String = "",
) {
    val pagerFlow: Flow<PagingData<CommentaryWithText>> = remember(lineId, sourceBookId) {
        buildLinksPagerFor(lineId, sourceBookId).distinctUntilChanged()
    }

    val lazyPagingItems: LazyPagingItems<CommentaryWithText> = pagerFlow.collectAsLazyPagingItems()

    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            for (index in 0 until lazyPagingItems.itemCount) {
                lazyPagingItems[index]?.let { item ->
                    LinkItem(
                        item = item,
                        commentTextSize = commentTextSize,
                        lineHeight = lineHeight,
                        fontFamily = fontFamily,
                        boldScale = boldScale,
                        highlightQuery = highlightQuery,
                        onLinkClick = onLinkClick
                    )
                }
            }

            // Gestion des états de chargement
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

@Composable
private fun LinkItem(
    item: CommentaryWithText,
    commentTextSize: Float,
    lineHeight: Float,
    fontFamily: FontFamily,
    boldScale: Float = 1.0f,
    highlightQuery: String,
    onLinkClick: (CommentaryWithText) -> Unit
) {
    // Optimisation : mémorisation du callback pour éviter recréation
    val onClick = remember(item, onLinkClick) {
        { onLinkClick(item) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .pointerInput(item) {
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        val annotated = remember(item.link.id, item.targetText, commentTextSize, boldScale) {
            buildAnnotatedFromHtml(
                item.targetText,
                commentTextSize,
                boldScale = if (boldScale < 1f) 1f else boldScale
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
