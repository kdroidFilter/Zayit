package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.references.LinePlaces
import io.github.kdroidfilter.seforimapp.features.bookcontent.references.LineReferences
import io.github.kdroidfilter.seforimapp.features.bookcontent.references.ReferenceBook
import io.github.kdroidfilter.seforimapp.features.bookcontent.references.ReferenceKind
import io.github.kdroidfilter.seforimapp.features.bookcontent.references.ReferencePlace
import io.github.kdroidfilter.seforimapp.features.bookcontent.references.loadLinePlaces
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.ContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimapp.features.search.FilterTabs
import io.github.kdroidfilter.seforimapp.features.search.LocalResultTextScale
import io.github.kdroidfilter.seforimapp.features.search.ResultLink
import io.github.kdroidfilter.seforimapp.features.search.ResultRow
import io.github.kdroidfilter.seforimapp.features.search.ResultTitle
import io.github.kdroidfilter.seforimapp.features.search.SnippetText
import io.github.kdroidfilter.seforimapp.features.search.readingSecondary
import io.github.kdroidfilter.seforimapp.features.search.resultSp
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.no_references_for_line
import seforimapp.seforimapp.generated.resources.no_sources_for_line
import seforimapp.seforimapp.generated.resources.references
import seforimapp.seforimapp.generated.resources.references_ein_mishpat
import seforimapp.seforimapp.generated.resources.references_mesorat_hashas
import seforimapp.seforimapp.generated.resources.references_more_from_book
import seforimapp.seforimapp.generated.resources.references_one_more_from_book
import seforimapp.seforimapp.generated.resources.references_summary
import seforimapp.seforimapp.generated.resources.search_collapse
import seforimapp.seforimapp.generated.resources.select_line_for_references
import seforimapp.seforimapp.generated.resources.select_line_for_sources
import seforimapp.seforimapp.generated.resources.sources

/**
 * The [kind] places of the selected line (the places that refer to it, or its sources), as the search's results: a
 * book at a time, its first passage cut around the quote, its other places behind "עוד N מקומות", tabs by category
 * when the books are many. A click opens the book there.
 *
 * The pane docks anywhere: a column beside the text, a row of columns under it — as many as its width holds.
 */
@Composable
fun LinePlacesView(
    kind: LinePlaces,
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    diacritics: DiacriticsMode,
) {
    val graph = LocalAppGraph.current
    val content = uiState.content
    val lines = content.placesLines()
    val lineIds = lines.map { it.id }

    val references by produceState<LineReferences?>(null, kind, lineIds) {
        value = null
        value =
            if (lines.isEmpty()) {
                LineReferences.Empty
            } else {
                runSuspendCatching { loadLinePlaces(kind, graph.repository, lines) }
                    .getOrDefault(LineReferences.Empty)
            }
    }

    // The text follows the zoom as the other line panes' do, animated in the selected tab
    val textSize by graph.appSettings.textSizeFlow
        .collectAsState()
    val animateZoom = LocalTabSelected.current && !LocalBookContentZoomInProgress.current
    val scale by animateFloatAsState(
        targetValue = textSize / AppSettings.DEFAULT_TEXT_SIZE,
        animationSpec = if (animateZoom) tween(durationMillis = ZOOM_ANIMATION_MS) else snap(),
        label = "linePlacesScale",
    )

    val paneInteractionSource = remember { MutableInteractionSource() }
    Column(modifier = Modifier.fillMaxSize().hoverable(paneInteractionSource)) {
        PaneHeader(
            label = stringResource(kind.title),
            interactionSource = paneInteractionSource,
            onHide = { onEvent(kind.toggle) },
        )
        val shown = references
        when {
            lines.isEmpty() -> CenteredText(stringResource(kind.selectLine))
            shown == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            shown.books.isEmpty() -> CenteredText(stringResource(kind.empty))
            else ->
                CompositionLocalProvider(LocalResultTextScale provides scale) {
                    ReferencesList(
                        references = shown,
                        tabsMinBooks = kind.tabsMinBooks,
                        diacritics = diacritics,
                        onOpen = { place -> onEvent(BookContentEvent.OpenCommentaryTarget(place.line.bookId, place.line.id)) },
                    )
                }
        }
    }
}

@Composable
private fun ReferencesList(
    references: LineReferences,
    tabsMinBooks: Int,
    diacritics: DiacriticsMode,
    onOpen: (ReferencePlace) -> Unit,
) {
    var tab by remember(references) { mutableStateOf<Long?>(null) }
    var expanded by remember(references) { mutableStateOf(emptySet<String>()) }
    val books = remember(references, tab) { references.books.filter { tab == null || it.tabId == tab } }
    val listState = rememberLazyListState()
    LaunchedEffect(tab) { listState.scrollToItem(0) }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        // Docked under the text the pane is wide: its books side by side, each column as wide as in a side pane
        // Zoomed in, a column needs more room: fewer columns rather than cramped ones
        val columns = (maxWidth / (COLUMN_MIN_WIDTH * LocalResultTextScale.current)).toInt().coerceIn(1, MAX_COLUMNS)
        val rows = remember(books, columns) { books.chunked(columns) }
        // A few books, all side by side: the room is theirs, their passages read longer
        val passageLines = if (references.books.size <= columns) FEW_BOOKS_LINES else SNIPPET_LINES
        VerticallyScrollableContainer(scrollState = listState as ScrollableState, modifier = Modifier.fillMaxSize()) {
            // Room for the scrollbar, so that it never covers the passages
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 11.dp)) {
                item(key = "header") {
                    ReferencesHeader(references, tabsMinBooks, tab) { tab = it }
                }
                items(rows, key = { row -> row.first().key }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP), verticalAlignment = Alignment.Top) {
                        row.forEach { book ->
                            ReferenceBookView(
                                book = book,
                                expanded = book.key in expanded,
                                diacritics = diacritics,
                                passageLines = passageLines,
                                onExpand = { expanded = if (it) expanded + book.key else expanded - book.key },
                                onOpen = onOpen,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // A last row short of books keeps the columns' width
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** How many books and places refer to the line, and the tabs that filter them by category. */
@Composable
private fun ReferencesHeader(
    references: LineReferences,
    tabsMinBooks: Int,
    tab: Long?,
    onTab: (Long?) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(Res.string.references_summary, references.books.size, references.placeCount),
            fontSize = 12.sp,
            color = readingSecondary(),
        )
        if (references.books.size >= tabsMinBooks) {
            val kindTabs = references.kindTabs.map { it.tabId to stringResource(it.label!!) }
            val tabs = remember(kindTabs, references.categoryTabs) { kindTabs + references.categoryTabs }
            if (tabs.isNotEmpty()) FilterTabs(tabs, tab, onTab)
        }
    }
}

/** A book's places: its first one, the others once [expanded]; each a result row that opens the book there. */
@Composable
private fun ReferenceBookView(
    book: ReferenceBook,
    expanded: Boolean,
    diacritics: DiacriticsMode,
    passageLines: Int,
    onExpand: (Boolean) -> Unit,
    onOpen: (ReferencePlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = JewelTheme.globalColors.outlines.focused
    val graph = LocalAppGraph.current
    val searchEngine = graph.searchEngine
    val bookFontCode by graph.appSettings.bookFontCodeFlow
        .collectAsState()
    val places = if (expanded) book.places else book.places.take(1)
    // The kind of a ruling or a parallel passage beside its place: it is no plain reference
    val kind = book.kind.label?.let { stringResource(it) }
    Column(modifier) {
        places.forEachIndexed { index, place ->
            ResultRow(selected = false, onClick = { onOpen(place) }) {
                if (index == 0) {
                    ResultTitle(book.title, listOfNotNull(kind, place.place).joinToString(" · ").ifEmpty { null })
                } else if (place.place != null) {
                    Text(place.place, fontSize = resultSp(12f), color = readingSecondary(), maxLines = 1)
                }
                // The passage's start until its quote is found, off the UI thread, for the places on screen only
                val snippet by produceState(diacritics.apply(place.start), place, diacritics) {
                    value = withContext(Dispatchers.Default) { diacritics.apply(place.snippet(searchEngine)) }
                }
                SnippetText(snippet, null, bookFontCode, maxLines = passageLines)
                val more = book.places.size - 1
                when {
                    index == 0 && !expanded && more > 0 -> {
                        val label =
                            if (more == 1) {
                                stringResource(Res.string.references_one_more_from_book, book.title)
                            } else {
                                stringResource(Res.string.references_more_from_book, more, book.title)
                            }
                        ResultLink(label, accent, resultSp(11f)) { onExpand(true) }
                    }
                    expanded && index == places.lastIndex && more > 0 ->
                        ResultLink(stringResource(Res.string.search_collapse), accent, resultSp(11f)) { onExpand(false) }
                }
            }
        }
    }
}

private val LinePlaces.title: StringResource
    get() = if (this == LinePlaces.Sources) Res.string.sources else Res.string.references

private val LinePlaces.selectLine: StringResource
    get() = if (this == LinePlaces.Sources) Res.string.select_line_for_sources else Res.string.select_line_for_references

private val LinePlaces.empty: StringResource
    get() = if (this == LinePlaces.Sources) Res.string.no_sources_for_line else Res.string.no_references_for_line

private val LinePlaces.toggle: BookContentEvent
    get() = if (this == LinePlaces.Sources) BookContentEvent.ToggleSources else BookContentEvent.ToggleReferences

/**
 * The lines a places pane lists the places of: every selected line — a manual multi-selection (Ctrl+click), or a
 * heading's whole section (a daf, a perek), as the commentaries show it — else the selected line.
 */
fun ContentState.placesLines(): List<Line> =
    if (selectedLines.size > 1) selectedLines.sortedBy { it.lineIndex } else listOfNotNull(primaryLine)

/** A book's key in the list: one book may refer to the line as a ruling and as a plain reference. */
private val ReferenceBook.key: String get() = "${kind.name}-$bookId"

private val ReferenceKind.label: StringResource?
    get() =
        when (this) {
            ReferenceKind.EinMishpat -> Res.string.references_ein_mishpat
            ReferenceKind.MesoratHashas -> Res.string.references_mesorat_hashas
            ReferenceKind.Reference -> null
        }

@Composable
private fun CenteredText(text: String) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center)
    }
}

// A column of places as wide as the pane beside the text holds it; never more than four side by side
private val COLUMN_MIN_WIDTH = 300.dp
private val COLUMN_GAP = 12.dp
private const val MAX_COLUMNS = 4

// The passage's lines: two as the search's, more when a few books have the pane to themselves
private const val SNIPPET_LINES = 2
private const val FEW_BOOKS_LINES = 6

/**
 * Under this many books the filter tabs are hidden: a handful of references need no filter; the sources keep them,
 * which tell the kind of each (חומש, תלמוד, הלכה) at a glance.
 */
private val LinePlaces.tabsMinBooks: Int
    get() = if (this == LinePlaces.Sources) 0 else REFERENCES_TABS_MIN_BOOKS

private const val REFERENCES_TABS_MIN_BOOKS = 6

private const val ZOOM_ANIMATION_MS = 300
