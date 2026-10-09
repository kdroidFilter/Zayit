@file:OptIn(ExperimentalJewelApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.e2e.E2e
import io.github.kdroidfilter.seforimapp.core.presentation.components.rememberPillTextFieldStyle
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.core.presentation.theme.AccentColor
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.components.CatalogRow
import io.github.kdroidfilter.seforimapp.features.home.widgets.FullWidthSection
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeUserLocation
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeUserLocationViewModel
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsGrid
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsLayout
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsOverlay
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.decodeLayout
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.Community
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeUiState
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.ResolvedReference
import io.github.kdroidfilter.seforimapp.features.search.domain.searchKey
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService.AuthorHit
import io.github.kdroidfilter.seforimapp.icons.JournalBookmark
import io.github.kdroidfilter.seforimapp.icons.WritingHand
import io.github.kdroidfilter.seforimapp.icons.bookOpenTabs
import io.github.kdroidfilter.seforimapp.texteffects.TypewriterPlaceholder
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.rememberResourceEnvironment
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.menuStyle
import seforimapp.seforimapp.generated.resources.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import io.github.kdroidfilter.seforimlibrary.core.models.Book as BookModel

// Suggestion models for the scope picker
@Immutable
private data class BookSuggestion(
    val book: BookModel,
    val path: List<String>,
    val exactAcronym: Boolean = false,
)

/** A row of the book-stage list; see [catBookRows] for their order. */
private sealed interface CatBookRow {
    data class Jump(
        val reference: ResolvedReference,
    ) : CatBookRow

    data object TextSearch : CatBookRow

    data class Author(
        val hit: AuthorHit,
    ) : CatBookRow

    data class Book(
        val suggestion: BookSuggestion,
    ) : CatBookRow
}

/**
 * The book-stage rows in order: the references, then the books and authors named exactly as typed
 * (`חולין` opens Chullin on Enter; `שוע` puts the שולחן ערוך parts first), the text search, then the other authors and books.
 */
private fun catBookRows(
    query: String,
    jumps: List<ResolvedReference>,
    textSearch: Boolean,
    authors: List<AuthorHit>,
    books: List<BookSuggestion>,
): ImmutableList<CatBookRow> {
    val key = query.searchKey()
    val (exactAuthors, otherAuthors) =
        authors.partition { author ->
            listOfNotNull(author.name, AuthorNames.display(author.name), author.alias).any { it.searchKey() == key }
        }
    val (exactBooks, otherBooks) = books.partition { it.exactAcronym || it.book.title.searchKey() == key }
    return buildList {
        jumps.mapTo(this) { CatBookRow.Jump(it) }
        exactBooks.mapTo(this) { CatBookRow.Book(it) }
        exactAuthors.mapTo(this) { CatBookRow.Author(it) }
        if (textSearch) add(CatBookRow.TextSearch)
        otherAuthors.mapTo(this) { CatBookRow.Author(it) }
        otherBooks.mapTo(this) { CatBookRow.Book(it) }
    }.toImmutableList()
}

@Immutable
private data class TocSuggestion(
    val toc: TocEntry,
    val path: List<String>,
)

private data class AnchorBounds(
    val windowOffset: IntOffset,
    val size: IntSize,
)

/**
 * Callbacks used by [HomeView] to delegate all search-related
 * interactions to the SearchHomeViewModel without referencing it
 * directly inside UI code.
 */
@Stable
data class HomeSearchCallbacks(
    val onReferenceQueryChanged: (String) -> Unit,
    val onTocQueryChanged: (String) -> Unit,
    val onGlobalExtendedChange: (Boolean) -> Unit,
    val onSubmitTextSearch: (String) -> Unit,
    val onOpenReference: () -> Unit,
    val onPickBook: (BookModel) -> Unit,
    val onPickToc: (TocEntry) -> Unit,
    val onOpenJump: (ResolvedReference) -> Unit = {},
    val onPickAuthor: (AuthorHit) -> Unit = {},
    val onClearAuthor: () -> Unit = {},
    val onOpenBook: (BookModel) -> Unit = {},
    val onOpenAuthor: (AuthorHit) -> Unit = {},
)

/**
 * High-level Home surface that wires CatalogRow with the core Home body content.
 */
@OptIn(ExperimentalJewelApi::class, ExperimentalLayoutApi::class)
@Composable
fun HomeView(
    onEvent: (BookContentEvent) -> Unit,
    searchUi: SearchHomeUiState,
    searchCallbacks: HomeSearchCallbacks,
    modifier: Modifier = Modifier,
    homeUserLocation: HomeUserLocation? = null,
) {
    val appSettings = LocalAppGraph.current.appSettings

    val panelBackground = JewelTheme.globalColors.panelBackground
    val showWallpaper by appSettings.showHomeWallpaperFlow.collectAsState()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (showWallpaper) {
            val isDark = JewelTheme.isDark
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.roundToInt()
            val wallpaper =
                if (isDark) {
                    when {
                        widthPx <= 960 -> Res.drawable.homepage_wallpaper_dark_small
                        widthPx <= 1440 -> Res.drawable.homepage_wallpaper_dark_medium
                        else -> Res.drawable.homepage_wallpaper_dark_large
                    }
                } else {
                    when {
                        widthPx <= 960 -> Res.drawable.homepage_wallpaper_small
                        widthPx <= 1440 -> Res.drawable.homepage_wallpaper_medium
                        else -> Res.drawable.homepage_wallpaper_large
                    }
                }
            // Layer 1: Wallpaper background, decoded off the UI thread (a new tab's first frame is its opening animation's)
            rememberDecodedImage(wallpaper)?.let { image ->
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            // Layer 2: Smooth horizontal vignette — image blends into background atmosphere
            Box(
                modifier =
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            colorStops =
                                arrayOf(
                                    0.00f to panelBackground.copy(alpha = 0.10f),
                                    0.04f to panelBackground.copy(alpha = 0.20f),
                                    0.08f to panelBackground.copy(alpha = 0.35f),
                                    0.13f to panelBackground.copy(alpha = 0.50f),
                                    0.20f to panelBackground.copy(alpha = 0.65f),
                                    0.28f to panelBackground.copy(alpha = 0.78f),
                                    0.38f to panelBackground.copy(alpha = 0.85f),
                                    0.50f to panelBackground.copy(alpha = 0.78f),
                                    0.62f to panelBackground.copy(alpha = 0.85f),
                                    0.72f to panelBackground.copy(alpha = 0.78f),
                                    0.80f to panelBackground.copy(alpha = 0.65f),
                                    0.87f to panelBackground.copy(alpha = 0.50f),
                                    0.92f to panelBackground.copy(alpha = 0.35f),
                                    0.96f to panelBackground.copy(alpha = 0.20f),
                                    1.00f to panelBackground.copy(alpha = 0.10f),
                                ),
                        ),
                    ),
            )

            // Layer 3: Radial gradient — bottom-center opaque, edges visible all around
            Box(
                modifier =
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            colorStops =
                                arrayOf(
                                    0.00f to panelBackground,
                                    0.35f to panelBackground,
                                    0.55f to panelBackground.copy(alpha = 0.75f),
                                    0.75f to panelBackground.copy(alpha = 0.40f),
                                    1.00f to Color.Transparent,
                                ),
                            center =
                                androidx.compose.ui.geometry.Offset(
                                    x = constraints.maxWidth / 2f,
                                    y = constraints.maxHeight * 0.65f,
                                ),
                            radius = constraints.maxWidth * 0.55f,
                        ),
                    ),
            )
        }

        // Layer 4: Content
        HomeBody(
            onEvent = onEvent,
            searchUi = searchUi,
            searchCallbacks = searchCallbacks,
            homeUserLocation = homeUserLocation,
        )
    }
}

/**
 * Home screen for the Book Content feature.
 *
 * Renders the welcome header, the main search bar with a mode toggle (Text vs Reference),
 * and the Book/TOC scope picker. State is sourced from the SearchHomeViewModel
 * through the Metro DI graph and kept outside of the page's sections to avoid losing focus or
 * field contents during recomposition.
 */
@OptIn(ExperimentalJewelApi::class, ExperimentalLayoutApi::class)
@Composable
private fun HomeBody(
    onEvent: (BookContentEvent) -> Unit,
    searchUi: SearchHomeUiState,
    searchCallbacks: HomeSearchCallbacks,
    homeUserLocation: HomeUserLocation?,
) {
    val appSettings = LocalAppGraph.current.appSettings

    val userLocation =
        homeUserLocation ?: run {
            val viewModel: HomeUserLocationViewModel =
                metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
            val state by viewModel.state.collectAsState()
            state
        }
    val community =
        remember(searchUi.userCommunityCode) {
            searchUi.userCommunityCode?.let { code -> runCatching { Community.valueOf(code) }.getOrNull() }
        }
    val widgetsState =
        remember(userLocation, community, appSettings) {
            HomeWidgetsState(userLocation, community, HomeWidgetsLayout(appSettings))
        }
    val tabs = LocalOpenWindow.current.tabsViewModel
    SideEffect { widgetsState.openTab = tabs::openTab }
    if (E2e.enabled && LocalTabSelected.current) SideEffect { E2e.homeWidgets = widgetsState }
    // Composed here, outside the page, so a card's window doesn't depend on the page's composition
    val widgetsLayoutRaw by appSettings.homeWidgetsLayoutFlow.collectAsState()
    val widgetsLayout = remember(widgetsLayoutRaw) { decodeLayout(widgetsLayoutRaw) }
    widgetsLayout.forEach { it.widget.Detached(widgetsState) }

    val scrollState = rememberScrollState()

    Box(Modifier.fillMaxSize()) {
        // The page owns its verticalScroll (HomeWidgetsGrid): this container only draws the scrollbar
        VerticallyScrollableContainer(
            scrollState = scrollState as ScrollableState,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Main search field focus handled inside SearchBar via autoFocus

                val homeContentModifier =
                    Modifier.widthIn(max = 600.dp).fillMaxWidth()

                // The page is centred; while the gallery shows or a widget is dragged it holds still, gliding back after
                FreezableCenter(frozen = widgetsState.pageHeld) {
                    HomeWidgetsGrid(
                        state = widgetsState,
                        widgets = widgetsLayout,
                        scrollState = scrollState,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Scrolls with the page instead of floating over the widgets
                        FullWidthSection(gapAfter = 4.dp) { CatalogRow(onEvent = onEvent) }
                        FullWidthSection(gapAfter = 4.dp) {
                            BoxWithConstraints(
                                Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                // Scale logo width proportionally; height follows the image aspect ratio
                                val logoWidth = (maxWidth * 0.50f).coerceIn(320.dp, 450.dp)
                                LogoImage(modifier = Modifier.width(logoWidth))
                            }
                        }
                        FullWidthSection(gapAfter = 4.dp) {
                            Box(
                                Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(homeContentModifier) {
                                    UnifiedSearchBar(searchUi = searchUi, searchCallbacks = searchCallbacks)
                                }
                            }
                        }
                        FullWidthSection(gapAfter = 4.dp) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Box(homeContentModifier) {
                                    Spacer(Modifier.height(32.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        HomeWidgetsOverlay(widgetsState, widgetsLayout)
    }
}

/**
 * The one smart search bar (references, books, authors, texts) with its suggestions: the home
 * page's, reused by the search results page. [initialText] fills the field without suggesting
 * (the results page's query); a text search goes to [HomeSearchCallbacks.onSubmitTextSearch].
 */
@Composable
internal fun UnifiedSearchBar(
    searchUi: SearchHomeUiState,
    searchCallbacks: HomeSearchCallbacks,
    modifier: Modifier = Modifier,
    initialText: String = "",
    autoFocus: Boolean = true,
    // The typed text, as it changes (the results page keeps it for the tab's restore)
    onTextChange: (String) -> Unit = {},
) {
    // Keep state outside the sections so it persists across their recompositions
    val scope = rememberCoroutineScope()

    fun focusAfterDelay(
        @StructuredScope scope: CoroutineScope,
        ms: Long,
        focusRequester: FocusRequester,
    ) {
        scope.launch {
            delay(ms.milliseconds)
            focusRequester.requestFocus()
        }
    }

    val referenceSearchState = remember { TextFieldState() }
    val currentOnTextChange by rememberUpdatedState(onTextChange)
    val currentBookPicked by rememberUpdatedState(searchUi.selectedScopeBook != null)
    val tocSearchState = remember { TextFieldState() }
    var skipNextReferenceQuery by remember { mutableStateOf(false) }
    var skipNextTocQuery by remember { mutableStateOf(false) }
    var tocEditedSinceBook by remember { mutableStateOf(false) }
    // Shared focus requester for the MAIN search bar so other UI (e.g., level changes)
    // can reliably return focus to it, allowing immediate Enter to submit.
    val mainSearchFocusRequester = remember { FocusRequester() }
    // Forward reference input changes to the ViewModel (VM handles debouncing and suggestions)
    LaunchedEffect(Unit) {
        snapshotFlow { referenceSearchState.text.toString() }.collect { qRaw ->
            currentOnTextChange(qRaw)
            if (skipNextReferenceQuery) {
                skipNextReferenceQuery = false
            } else {
                searchCallbacks.onReferenceQueryChanged(qRaw)
            }
        }
    }
    // Forward toc input changes to the ViewModel (ignored until a book is selected)
    LaunchedEffect(Unit) {
        snapshotFlow { tocSearchState.text.toString() }.collect { qRaw ->
            // In a book, the typed text is also the search's query
            if (currentBookPicked) currentOnTextChange(qRaw)
            if (skipNextTocQuery) {
                skipNextTocQuery = false
                tocEditedSinceBook = qRaw.isNotBlank()
            } else {
                tocEditedSinceBook = qRaw.isNotBlank()
                searchCallbacks.onTocQueryChanged(qRaw)
            }
        }
    }

    fun searchText(query: String) {
        if (query.isNotBlank()) searchCallbacks.onSubmitTextSearch(query)
    }

    fun openReference() {
        searchCallbacks.onOpenReference()
    }

    // The results page shows its query in the field: the book's field once the bar is in a book (its
    // search's scope), else the main one. The suggestions follow it as typed text (they show on focus)
    // Seeded once per query, and again only when a restore puts the bar in its book; never over what the
    // user does with the bar (picking or clearing a book)
    val inBook = searchUi.selectedScopeBook != null && searchUi.selectedScopeToc == null
    var seededQuery by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(initialText, inBook) {
        if (initialText.isEmpty()) return@LaunchedEffect
        val restoredBook = inBook && !searchUi.bookPickedByUser
        if (seededQuery == initialText && !restoredBook) return@LaunchedEffect
        seededQuery = initialText
        val field = if (inBook) tocSearchState else referenceSearchState
        if (field.text.toString() != initialText) field.edit { replace(0, length, initialText) }
    }

    // Back to the bar once a book is picked, to type in its TOC
    LaunchedEffect(searchUi.selectedScopeBook?.id) {
        if (searchUi.selectedScopeBook != null && searchUi.bookPickedByUser) {
            delay(80.milliseconds)
            mainSearchFocusRequester.requestFocus()
        }
    }
    val mappedBookSuggestionsForBar =
        searchUi.bookSuggestions
            .map { bs ->
                BookSuggestion(bs.book, bs.path, bs.exactAcronym)
            }.toImmutableList()
    val mappedTocSuggestionsForBar =
        searchUi.tocSuggestions.map { ts ->
            TocSuggestion(ts.toc, ts.path)
        }
    val breadcrumbSeparatorTop = stringResource(Res.string.breadcrumb_separator)
    val isTocInTopBar = searchUi.selectedScopeBook != null
    SearchBar(
        state = if (isTocInTopBar) tocSearchState else referenceSearchState,
        onSubmit = { openReference() },
        modifier = modifier,
        focusRequester = mainSearchFocusRequester,
        autoFocus = autoFocus,
        // A picked TOC entry fills the field with its path: nothing to search then
        // An author's books are picked, not searched in: no text row then
        textSearchEnabled = searchUi.selectedScopeToc == null && searchUi.selectedScopeAuthor == null,
        textSearchInBook = searchUi.selectedScopeBook?.title,
        onTextSearch = ::searchText,
        // Before a book is picked: go-to rows, the text search and books; after: its TOC
        suggestionsVisible = if (!isTocInTopBar) searchUi.suggestionsVisible else false,
        bookSuggestions = if (!isTocInTopBar) mappedBookSuggestionsForBar else persistentListOf(),
        jumpSuggestions =
            if (!isTocInTopBar) searchUi.jumpSuggestions.toImmutableList() else persistentListOf(),
        onPickJump = { jump -> searchCallbacks.onOpenJump(jump) },
        authorSuggestions =
            if (!isTocInTopBar) searchUi.authorSuggestions.toImmutableList() else persistentListOf(),
        selectedAuthor = searchUi.selectedScopeAuthor?.name,
        onPickAuthor = { author ->
            searchCallbacks.onPickAuthor(author)
            skipNextReferenceQuery = true
            referenceSearchState.edit { replace(0, length, "") }
        },
        onClearAuthor = { searchCallbacks.onClearAuthor() },
        onOpenBook = { opened -> searchCallbacks.onOpenBook(opened.book) },
        onOpenAuthor = { author -> searchCallbacks.onOpenAuthor(author) },
        tocSuggestionsVisible = isTocInTopBar && searchUi.tocSuggestionsVisible,
        tocSuggestions = if (isTocInTopBar) mappedTocSuggestionsForBar else emptyList(),
        selectedBook = searchUi.selectedScopeBook,
        placeholderText =
            if (isTocInTopBar) {
                stringResource(
                    Res.string.search_in_book_placeholder,
                )
            } else {
                null
            },
        submitOnEnterInReference = isTocInTopBar,
        onGlobalExtendedChange = { searchCallbacks.onGlobalExtendedChange(it) },
        isBookLoading = searchUi.isReferenceLoading && !isTocInTopBar,
        isTocLoading = searchUi.isTocLoading && isTocInTopBar,
        onPickBook = { picked ->
            searchCallbacks.onPickBook(picked.book)
            skipNextReferenceQuery = true
            referenceSearchState.edit { replace(0, length, "") }
            skipNextTocQuery = true
            tocSearchState.edit { replace(0, length, "") }
            skipNextTocQuery = false
            tocEditedSinceBook = false
            focusAfterDelay(scope, 80, mainSearchFocusRequester)
        },
        onPickToc = { picked ->
            searchCallbacks.onPickToc(picked.toc)
            val dedup = dedupAdjacent(picked.path)
            val stripped = stripBookPrefixFromTocPath(searchUi.selectedScopeBook, dedup)
            val display = stripped.joinToString(breadcrumbSeparatorTop)
            skipNextTocQuery = true
            tocSearchState.edit { replace(0, length, display) }
            tocEditedSinceBook = true
        },
        onClearBook = {
            searchCallbacks.onReferenceQueryChanged("")
            searchCallbacks.onTocQueryChanged("")
            skipNextReferenceQuery = true
            skipNextTocQuery = true
            referenceSearchState.edit { replace(0, length, "") }
            tocSearchState.edit { replace(0, length, "") }
            skipNextTocQuery = false
            tocEditedSinceBook = false
        },
        canClearBookOnBackspace = { !tocEditedSinceBook },
    )
}

/**
 * Centres [content] vertically, or while [frozen] keeps it where it was: content added below then only lengthens its
 * scroll instead of lifting the whole page by half of it. The content scrolls within what's left under its top.
 */
@Composable
internal fun FreezableCenter(
    frozen: Boolean,
    content: @Composable () -> Unit,
) {
    // Not state: written while measuring, read back only by the next measure
    val lastTop = remember { IntArray(1) }
    val lastSize = remember { IntArray(2) }
    // Where the page is centred, gliding there when its height changes (a widget moved or resized), not jumping
    val shownTop = remember { Animatable(0f) }
    var wanted by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    LaunchedEffect(wanted) {
        val (top, snap) = wanted ?: return@LaunchedEffect
        if (snap) shownTop.snapTo(top.toFloat()) else shownTop.animateTo(top.toFloat(), spring(stiffness = Spring.StiffnessMediumLow))
    }
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val free = constraints.copy(minWidth = 0, minHeight = 0)
        val placeable =
            if (frozen) {
                measurables.first().measure(free.copy(maxHeight = (constraints.maxHeight - lastTop[0]).coerceAtLeast(0)))
            } else {
                measurables.first().measure(free)
            }
        val top = if (frozen) lastTop[0] else ((constraints.maxHeight - placeable.height) / 2).coerceAtLeast(0)
        lastTop[0] = top
        // A new window size (or the first layout) puts it there at once
        val resized = lastSize[0] != constraints.maxWidth || lastSize[1] != constraints.maxHeight
        lastSize[0] = constraints.maxWidth
        lastSize[1] = constraints.maxHeight
        if (wanted?.first != top) wanted = top to (resized || wanted == null)
        val shown = if (resized || wanted?.second == true && shownTop.value != top.toFloat()) top else shownTop.value.roundToInt()
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative((constraints.maxWidth - placeable.width) / 2, shown)
        }
    }
}

/**
 * App logo shown on the Home screen.
 * In light mode: always golden tint (text mask + subtle SoftLight on logo).
 * In dark mode: accent color tint from the current theme.
 */
@Composable
internal fun LogoImage(modifier: Modifier = Modifier) {
    val isDark = JewelTheme.isDark
    val logoTint = logoTint()
    val tintAlpha = 0.25f
    // Mipmapped downscaling (Medium, on Skia): the PNGs are far larger than the logo is ever shown
    val logo = BitmapPainter(imageResource(Res.drawable.zayit_new_logo), filterQuality = FilterQuality.Medium)
    val logoText = BitmapPainter(imageResource(Res.drawable.zayit_new_logo_text), filterQuality = FilterQuality.Medium)

    Box(modifier) {
        // Base layer: full logo with original colors
        Image(
            logo,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        // Subtle tint overlay: SoftLight preserves transparency, tints only colored areas
        Image(
            logo,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            alpha = tintAlpha,
            colorFilter = ColorFilter.tint(logoTint, BlendMode.SrcIn),
        )
        // Text overlay: tint color painted through the text alpha mask
        Image(
            logoText,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(logoTint, BlendMode.SrcIn),
        )
    }
}

/** The logo's tint: the accent in dark, gold in light. */
@Composable
private fun logoTint(): Color =
    if (JewelTheme.isDark) JewelTheme.globalColors.outlines.focused else AccentColor.Gold.forMode(isDark = false)

/**
 * The logo's olive branch alone, in its own colors. Its PNG is pre-scaled (Lanczos) to twice the size
 * it is shown at: Skia's sampling aliases on big downscales, the full logo's detail turned to pixels.
 */
@Composable
internal fun LogoBranch(modifier: Modifier = Modifier) {
    Image(
        BitmapPainter(imageResource(Res.drawable.zayit_logo_branch), filterQuality = FilterQuality.High),
        contentDescription = null,
        modifier = modifier,
    )
}

@Composable
/**
 * Renders the suggestion list for places, authors and books, keeping the currently
 * focused row in view as the user navigates with the keyboard.
 * Uses native Jewel menu styling for consistent look and feel.
 */
private fun SuggestionsPanel(
    rows: ImmutableList<CatBookRow>,
    // Runs a row: its main action (open, search) or its Tab one (search inside). By row, not index:
    // a click must run the row it lands on even if the list changed since this lambda was made
    onRow: (row: CatBookRow, open: Boolean) -> Unit,
    onTextSearchAll: () -> Unit,
    textSearchLabel: String?,
    textSearchQuery: String?,
    focusedIndex: Int = -1,
    emptyMessage: String? = null,
    isLoading: Boolean = false,
    loadingMessage: String? = null,
) {
    val listState = rememberLazyListState()
    val menuStyle = JewelTheme.menuStyle

    LaunchedEffect(focusedIndex, rows.size) {
        if (focusedIndex >= 0) {
            val total = rows.size
            if (total > 0) {
                val visible = listState.layoutInfo.visibleItemsInfo
                val firstVisible = visible.firstOrNull()?.index
                val lastVisible = visible.lastOrNull()?.index
                // Scroll down when at last visible
                if (lastVisible != null && focusedIndex == lastVisible) {
                    val nextIndex = (focusedIndex + 1).coerceAtMost(total - 1)
                    if (nextIndex != focusedIndex) listState.scrollToItem(nextIndex)
                } else if (firstVisible != null && focusedIndex == firstVisible) {
                    // Scroll up when at first visible
                    val prevIndex = (focusedIndex - 1).coerceAtLeast(0)
                    if (prevIndex != focusedIndex) listState.scrollToItem(prevIndex)
                }
            }
        }
    }
    val isEmpty = rows.isEmpty()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(menuStyle.metrics.shadowSize, RoundedCornerShape(menuStyle.metrics.cornerSize))
                .clip(RoundedCornerShape(menuStyle.metrics.cornerSize))
                .border(menuStyle.metrics.borderWidth, menuStyle.colors.border, RoundedCornerShape(menuStyle.metrics.cornerSize))
                .background(menuStyle.colors.background)
                .heightIn(max = 220.dp)
                .padding(menuStyle.metrics.contentPadding),
    ) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading && !loadingMessage.isNullOrEmpty() && isEmpty) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = loadingMessage,
                            color = JewelTheme.globalColors.text.disabled,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                        )
                    }
                }
            } else if (isEmpty && !emptyMessage.isNullOrEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = emptyMessage,
                            color = JewelTheme.globalColors.text.disabled,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                        )
                    }
                }
            } else {
                items(rows.size) { rowIndex ->
                    val focused = rowIndex == focusedIndex
                    val row = rows[rowIndex]
                    val open = RowAction(KEY_ENTER, stringResource(Res.string.row_action_open)) { onRow(row, true) }
                    val searchInside = RowAction(KEY_TAB, stringResource(Res.string.row_action_search_inside)) { onRow(row, false) }
                    when (row) {
                        is CatBookRow.Jump ->
                            SuggestionRow(
                                parts = listOf(row.reference.book.title, row.reference.label),
                                onClick = open.onClick,
                                kind = SuggestionKind.PLACE,
                                highlighted = focused,
                                actions = listOf(open),
                            )

                        CatBookRow.TextSearch ->
                            SuggestionRow(
                                parts = listOfNotNull(textSearchLabel),
                                onClick = { onRow(row, true) },
                                kind = SuggestionKind.TEXT_SEARCH,
                                emphasis = textSearchQuery,
                                highlighted = focused,
                                actions =
                                    listOf(
                                        RowAction(KEY_ENTER, stringResource(Res.string.row_action_search_base)) { onRow(row, true) },
                                        RowAction(KEY_CTRL_ENTER, stringResource(Res.string.row_action_search_all), onTextSearchAll),
                                    ),
                            )

                        is CatBookRow.Author ->
                            SuggestionRow(
                                parts =
                                    listOf(
                                        AuthorNames.display(row.hit.name) +
                                            row.hit.alias
                                                ?.let { " ($it)" }
                                                .orEmpty(),
                                    ),
                                onClick = open.onClick,
                                kind = SuggestionKind.AUTHOR,
                                highlighted = focused,
                                detail = stringResource(Res.string.author_books_count, row.hit.bookCount),
                                actions = listOf(open, searchInside),
                            )

                        is CatBookRow.Book ->
                            SuggestionRow(
                                parts = dedupAdjacent(row.suggestion.path),
                                onClick = open.onClick,
                                kind = SuggestionKind.BOOK,
                                highlighted = focused,
                                actions = listOf(open, searchInside),
                            )
                    }
                }
            }
        }
    }
}

@Composable
/**
 * Renders the TOC suggestion list for the currently selected book, stripping the
 * duplicated book prefix from breadcrumb paths for compact display.
 * Uses native Jewel menu styling for consistent look and feel.
 */
private fun TocSuggestionsPanel(
    suggestions: List<Pair<TocSuggestion, List<String>>>,
    onPickToc: (TocSuggestion) -> Unit,
    textSearchLabel: String? = null,
    textSearchQuery: String? = null,
    onTextSearch: () -> Unit = {},
    focusedIndex: Int = -1,
    emptyMessage: String? = null,
    isLoading: Boolean = false,
    loadingMessage: String? = null,
) {
    val isEmpty = suggestions.isEmpty() && textSearchLabel == null
    val listState = rememberLazyListState()
    val menuStyle = JewelTheme.menuStyle

    LaunchedEffect(focusedIndex, suggestions.size) {
        if (focusedIndex >= 0 && suggestions.isNotEmpty()) {
            val visible = listState.layoutInfo.visibleItemsInfo
            val firstVisible = visible.firstOrNull()?.index
            val lastVisible = visible.lastOrNull()?.index
            if (lastVisible != null && focusedIndex == lastVisible) {
                val nextIndex = (focusedIndex + 1).coerceAtMost(suggestions.lastIndex)
                if (nextIndex != focusedIndex) listState.scrollToItem(nextIndex)
            } else if (firstVisible != null && focusedIndex == firstVisible) {
                val prevIndex = (focusedIndex - 1).coerceAtLeast(0)
                if (prevIndex != focusedIndex) listState.scrollToItem(prevIndex)
            }
        }
    }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(menuStyle.metrics.shadowSize, RoundedCornerShape(menuStyle.metrics.cornerSize))
                .clip(RoundedCornerShape(menuStyle.metrics.cornerSize))
                .border(menuStyle.metrics.borderWidth, menuStyle.colors.border, RoundedCornerShape(menuStyle.metrics.cornerSize))
                .background(menuStyle.colors.background)
                .heightIn(max = 220.dp)
                .padding(menuStyle.metrics.contentPadding),
    ) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading && !loadingMessage.isNullOrEmpty() && isEmpty) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = loadingMessage,
                            color = JewelTheme.globalColors.text.disabled,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                        )
                    }
                }
            } else if (isEmpty && !emptyMessage.isNullOrEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = emptyMessage,
                            color = JewelTheme.globalColors.text.disabled,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                        )
                    }
                }
            } else {
                items(suggestions.size) { index ->
                    val (ts, parts) = suggestions[index]
                    val open = RowAction(KEY_ENTER, stringResource(Res.string.row_action_open)) { onPickToc(ts) }
                    SuggestionRow(
                        parts = parts,
                        onClick = open.onClick,
                        kind = SuggestionKind.PLACE,
                        highlighted = index == focusedIndex,
                        actions = listOf(open),
                    )
                }
                if (textSearchLabel != null) {
                    item {
                        SuggestionRow(
                            parts = listOf(textSearchLabel),
                            onClick = onTextSearch,
                            kind = SuggestionKind.TEXT_SEARCH,
                            emphasis = textSearchQuery,
                            highlighted = suggestions.size == focusedIndex,
                            actions = listOf(RowAction(KEY_ENTER, stringResource(Res.string.row_action_search), onTextSearch)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun textSearchLabel(
    query: String,
    inBook: String?,
): String =
    if (inBook == null) {
        stringResource(Res.string.search_text_row, query)
    } else {
        stringResource(Res.string.search_text_row_in_book, query, inBook)
    }

/**
 * Collapses adjacent breadcrumb segments when the next segment strictly extends
 * the previous by a common separator (comma/space/colon/dash). This keeps
 * suggestions concise while preserving the most specific path.
 */
private fun dedupAdjacent(parts: List<String>): List<String> {
    if (parts.isEmpty()) return parts

    fun extends(
        prev: String,
        next: String,
    ): Boolean {
        val a = prev.trim()
        val b = next.trim()
        if (b.length <= a.length) return false
        if (!b.startsWith(a)) return false
        val ch = b[a.length]
        return ch == ',' || ch == ' ' || ch == ':' || ch == '-' || ch == '—'
    }

    val out = ArrayList<String>(parts.size)
    for (p in parts) {
        if (out.isEmpty()) {
            out += p
        } else {
            val last = out.last()
            when {
                p == last -> {
                    // exact duplicate, skip
                }

                extends(last, p) -> {
                    // Next is a refinement of previous; replace previous with next
                    out[out.lastIndex] = p
                }

                else -> out += p
            }
        }
    }
    return out
}

/**
 * Computes the TOC suggestions to actually show (and let the keyboard select), each
 * paired with its display breadcrumb. Entries whose breadcrumb collapses to nothing
 * once the book prefix is stripped are dropped — e.g. the book's own root entry, which
 * can still match a substring query (typing "יט" matches it inside "גיטין"). Sharing
 * this between the keyboard handlers and [TocSuggestionsPanel] keeps the rendered list,
 * the highlighted row and the picked entry in sync, so navigation opens the right place.
 */
private fun tocSuggestionsForDisplay(
    selectedBook: BookModel?,
    tocSuggestions: List<TocSuggestion>,
): List<Pair<TocSuggestion, List<String>>> =
    tocSuggestions.mapNotNull { ts ->
        val parts = stripBookPrefixFromTocPath(selectedBook, dedupAdjacent(ts.path))
        if (parts.isNotEmpty()) ts to parts else null
    }

/**
 * Strips the selected book's title if it redundantly appears as the first
 * breadcrumb in a TOC path, handling common punctuation right after the title.
 */
private fun stripBookPrefixFromTocPath(
    selectedBook: BookModel?,
    parts: List<String>,
): List<String> {
    if (selectedBook == null || parts.isEmpty()) return parts
    val bookTitle = selectedBook.title.trim()
    val first = parts.first().trim()
    if (first == bookTitle) return parts.drop(1)
    if (first.length > bookTitle.length && first.startsWith(bookTitle)) {
        val ch = first[bookTitle.length]
        if (ch == ',' || ch == ' ' || ch == ':' || ch == '-' || ch == '—') {
            var remainder = first.substring(bookTitle.length + 1)
            remainder = remainder.trim().trimStart(',', ' ', ':', '-', '—').trim()
            if (remainder.isNotEmpty()) {
                return listOf(remainder) + parts.drop(1)
            }
        }
    }
    return parts
}

private fun String.withBold(part: String?): AnnotatedString {
    val start = part?.takeIf { it.isNotEmpty() }?.let { indexOf(it) } ?: -1
    if (start < 0) return AnnotatedString(this)
    return buildAnnotatedString {
        append(this@withBold)
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + part!!.length)
    }
}

/** What a suggestion row leads to, shown by its icon. */
private enum class SuggestionKind { PLACE, TEXT_SEARCH, AUTHOR, BOOK }

@Composable
private fun SuggestionIcon(kind: SuggestionKind) {
    val tint = JewelTheme.globalColors.text.info
    val iconModifier = Modifier.size(14.dp)
    when (kind) {
        SuggestionKind.PLACE ->
            Image(rememberVectorPainter(JournalBookmark), null, iconModifier, colorFilter = ColorFilter.tint(tint))
        SuggestionKind.BOOK ->
            Image(rememberVectorPainter(bookOpenTabs(tint)), null, iconModifier, colorFilter = ColorFilter.tint(tint))
        SuggestionKind.TEXT_SEARCH -> Icon(AllIconsKeys.Actions.Find, null, iconModifier, tint = tint)
        SuggestionKind.AUTHOR ->
            Image(rememberVectorPainter(WritingHand), null, iconModifier, colorFilter = ColorFilter.tint(tint))
    }
}

@Composable
private fun SuggestionRow(
    parts: List<String>,
    onClick: () -> Unit,
    kind: SuggestionKind,
    highlighted: Boolean = false,
    // Shown bold where it appears in the row (the typed text of a text-search row)
    emphasis: String? = null,
    // Secondary text after the row's parts (an author's number of books)
    detail: String? = null,
    // What the row does, with its keys; shown on the highlighted or hovered row, and clickable
    actions: List<RowAction> = emptyList(),
) {
    val hScroll = rememberScrollState(0)
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val active = highlighted || isHovered
    val hasContent = parts.isNotEmpty()

    // Use Jewel-consistent hover color for native look
    val backgroundColor by animateColorAsState(
        targetValue =
            if (active) {
                JewelTheme.globalColors.outlines.focused
                    .copy(alpha = 0.12f)
            } else {
                Color.Transparent
            },
        animationSpec = tween(durationMillis = 150),
    )

    LaunchedEffect(active, parts) {
        if (active) {
            // Wait until we know the scrollable width to avoid any initial latency
            val max = snapshotFlow { hScroll.maxValue }.filter { it > 0 }.first()
            // Start from end (non-selected state shows end), then loop end -> start and jump to end again
            hScroll.scrollTo(max)
            // 2x slower (~20 px/s)
            val speedPxPerSec = 20f
            while (true) {
                val dist = hScroll.value // currently at max, distance to start
                val toStartMs = ((dist / speedPxPerSec) * 1000f).toInt().coerceIn(3000, 24000)
                hScroll.animateScrollTo(0, animationSpec = tween(durationMillis = toStartMs, easing = LinearEasing))
                delay(600.milliseconds)
                hScroll.scrollTo(max)
                delay(600.milliseconds)
            }
        } else {
            // Show the end for non-active rows
            val max = hScroll.maxValue
            if (max > 0) hScroll.scrollTo(max) else hScroll.scrollTo(0)
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(backgroundColor)
                .clickable(onClick = onClick)
                .pointerHoverIcon(PointerIcon.Hand)
                .hoverable(hoverSource)
                .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        SuggestionIcon(kind)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.weight(1f).horizontalScroll(hScroll),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                parts.forEachIndexed { index, text ->
                    if (index > 0) {
                        Text(
                            stringResource(Res.string.breadcrumb_separator),
                            color = JewelTheme.globalColors.text.disabled,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                        )
                    }
                    Text(
                        text.withBold(emphasis),
                        color = JewelTheme.globalColors.text.normal,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (detail != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        detail,
                        color = JewelTheme.globalColors.text.disabled,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        if (active && hasContent && actions.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                actions.forEach { RowActionChip(it) }
            }
        }
    }
}

@Composable
private fun SearchBar(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    onSubmit: () -> Unit = {},
    enabled: Boolean = true,
    suggestionsVisible: Boolean = false,
    bookSuggestions: ImmutableList<BookSuggestion> = persistentListOf(),
    onPickBook: (BookSuggestion) -> Unit = {},
    // Places the typed reference points to, listed first and opened directly
    jumpSuggestions: ImmutableList<ResolvedReference> = persistentListOf(),
    onPickJump: (ResolvedReference) -> Unit = {},
    // Authors the text may name; once one is picked, a chip shows it and the books are theirs
    authorSuggestions: ImmutableList<AuthorHit> = persistentListOf(),
    onPickAuthor: (AuthorHit) -> Unit = {},
    // Enter or a click opens a book / an author's page; Tab picks it to search inside (onPickBook / onPickAuthor)
    onOpenBook: (BookSuggestion) -> Unit = {},
    onOpenAuthor: (AuthorHit) -> Unit = {},
    selectedAuthor: String? = null,
    onClearAuthor: () -> Unit = {},
    // The "search this text" row: offered while text is typed, Ctrl+Enter runs it from anywhere
    textSearchEnabled: Boolean = false,
    textSearchInBook: String? = null,
    onTextSearch: (String) -> Unit = {},
    // TOC suggestions (for the second field)
    tocSuggestionsVisible: Boolean = false,
    tocSuggestions: List<TocSuggestion> = emptyList(),
    selectedBook: BookModel? = null,
    onPickToc: (TocSuggestion) -> Unit = {},
    onClearBook: (() -> Unit)? = null,
    canClearBookOnBackspace: () -> Boolean = { true },
    // Focus & popup control
    autoFocus: Boolean = true,
    focusRequester: FocusRequester? = null,
    onDismissSuggestions: () -> Unit = {},
    // Placeholder hints override (animated)
    // Synchronized placeholder override (renders plain text if provided)
    placeholderText: String? = null,
    // Once a book is picked, pressing Enter on a TOC entry also opens it
    submitOnEnterInReference: Boolean = false,
    // Advanced search toggle
    onGlobalExtendedChange: (Boolean) -> Unit = {},
    // Loading flags for predictive lists
    isBookLoading: Boolean = false,
    isTocLoading: Boolean = false,
) {
    // Suggestion state is shared across all tabs via the single SearchHomeViewModel, but every
    // open tab stays composed. A Popup renders in its own window and escapes the layout(0,0) trick
    // used to hide non-selected tabs, so without this guard each composed tab would draw a
    // duplicate suggestions popup. Only the selected tab is allowed to show the overlay.
    val isTabSelected = LocalTabSelected.current
    val scope = rememberCoroutineScope()
    // Hints from string resources
    val referenceHints =
        listOf(
            stringResource(Res.string.reference_hint_1),
            stringResource(Res.string.reference_hint_2),
            stringResource(Res.string.reference_hint_3),
            stringResource(Res.string.reference_hint_4),
            stringResource(Res.string.reference_hint_5),
        )

    val textHints =
        listOf(
            stringResource(Res.string.text_hint_1),
            stringResource(Res.string.text_hint_2),
            stringResource(Res.string.text_hint_3),
            stringResource(Res.string.text_hint_4),
            stringResource(Res.string.text_hint_5),
        )

    // The one bar takes references and texts alike: show both, in turn
    val hints = referenceHints.zip(textHints).flatMap { (reference, text) -> listOf(reference, text) }

    // Disable placeholder animation while user is typing
    val isUserTyping by remember { derivedStateOf { state.text.isNotEmpty() } }

    // Auto-focus the main search field on first composition
    val internalFocusRequester = remember { FocusRequester() }
    val effectiveFocusRequester = focusRequester ?: internalFocusRequester
    LaunchedEffect(Unit) {
        delay(200.milliseconds)
        if (enabled && autoFocus) effectiveFocusRequester.requestFocus()
    }

    // Predictive suggestions management for REFERENCE mode while keeping TextField style
    val hasUserText = state.text.isNotBlank()
    val queryLength = state.text.length
    val minBookPrefixLen = 2
    val minTocPrefixLen = 1
    var focusedIndex by remember { mutableIntStateOf(-1) }
    var popupVisible by remember { mutableStateOf(false) }
    // Bumped when the field gets the focus or a click: the suggestions open (again, after a click outside
    // closed them). None before the first one (a restored results tab); losing the focus to a click in
    // them doesn't hide them under the pointer
    var openRequests by remember { mutableIntStateOf(0) }
    var handledOpenRequests by remember { mutableIntStateOf(0) }
    // Rows of the book-stage list, in display order
    val query = state.text.toString().trim()
    val catBookRows =
        remember(query, jumpSuggestions, textSearchEnabled, authorSuggestions, bookSuggestions) {
            catBookRows(
                query,
                jumpSuggestions,
                textSearchEnabled && query.isNotEmpty(),
                authorSuggestions,
                bookSuggestions,
            )
        }
    val totalCatBook = catBookRows.size
    // Keyboard navigation must operate on the exact list that TocSuggestionsPanel
    // renders, otherwise the highlighted row and the picked entry desync and the
    // wrong reference opens (see [tocSuggestionsForDisplay]).
    val visibleTocSuggestions =
        remember(tocSuggestions, selectedBook) {
            tocSuggestionsForDisplay(selectedBook, tocSuggestions)
        }
    val totalToc = visibleTocSuggestions.size
    // The text-search row: after the jumps at the book stage, after the TOC entries once a book is picked
    val textRow = textSearchEnabled && hasUserText
    val textRowCount = if (textRow) 1 else 0
    val totalTocRows = totalToc + textRowCount
    val isTocMode = selectedBook != null
    val showBookStageSuggestions = suggestionsVisible && totalCatBook > 0 && !isTocMode
    val showTocSuggestions = (tocSuggestionsVisible || textRow) && totalTocRows > 0 && isTocMode
    val showBookLoading = !isTocMode && isBookLoading && hasUserText && queryLength >= minBookPrefixLen
    val showTocLoading = isTocMode && isTocLoading && hasUserText && queryLength >= minTocPrefixLen
    val showBookEmptyState =
        !isTocMode &&
            suggestionsVisible &&
            totalCatBook == 0 &&
            hasUserText &&
            queryLength >= minBookPrefixLen &&
            !showBookLoading
    val showTocEmptyState =
        isTocMode &&
            tocSuggestionsVisible &&
            totalToc == 0 &&
            hasUserText &&
            queryLength >= minTocPrefixLen &&
            !showTocLoading
    LaunchedEffect(
        suggestionsVisible,
        tocSuggestionsVisible,
        bookSuggestions,
        jumpSuggestions,
        authorSuggestions,
        textRow,
        tocSuggestions,
        isTocMode,
        showBookEmptyState,
        showTocEmptyState,
        showBookLoading,
        showTocLoading,
        openRequests,
    ) {
        val shouldOpen =
            when {
                showTocSuggestions -> true
                showBookStageSuggestions -> true
                showBookEmptyState -> true
                showTocEmptyState -> true
                showBookLoading -> true
                showTocLoading -> true
                else -> false
            }
        // A reopen request alone (a click in the field, which also closed the list as a click outside it)
        // keeps the highlighted row; new suggestions start from the first
        val keepRow = openRequests != handledOpenRequests && shouldOpen && focusedIndex >= 0
        handledOpenRequests = openRequests
        popupVisible = shouldOpen
        if (!keepRow) focusedIndex = if (shouldOpen && (showTocSuggestions || showBookStageSuggestions)) 0 else -1
    }

    var anchor by remember { mutableStateOf<AnchorBounds?>(null) }
    var backspaceStartedEmpty by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        // Local helpers to ensure popup is dismissed when committing a choice
        fun dismissPopup() {
            popupVisible = false
            onDismissSuggestions()
        }

        fun handlePickBook(book: BookSuggestion) {
            onPickBook(book)
            dismissPopup()
        }

        fun handlePickToc(toc: TocSuggestion) {
            onPickToc(toc)
            dismissPopup()
        }

        fun handlePickJump(jump: ResolvedReference) {
            onPickJump(jump)
            dismissPopup()
        }

        fun handlePickAuthor(author: AuthorHit) {
            onPickAuthor(author)
        }

        fun handleOpenAuthor(author: AuthorHit) {
            onOpenAuthor(author)
            dismissPopup()
        }

        fun handleOpenBook(book: BookSuggestion) {
            onOpenBook(book)
            dismissPopup()
        }

        // A search over the whole library covers the base books, or [extended] all of them
        fun handleTextSearch(extended: Boolean = false) {
            val query = state.text.toString().trim()
            if (query.isEmpty()) return
            onGlobalExtendedChange(extended)
            onTextSearch(query)
            dismissPopup()
        }

        // Commits the book-stage row at [index] (jumps, text search, authors, books); returns the book picked
        // Runs a book-stage row: its main action ([open]: open, search) or its Tab one (search inside)
        fun runCatBookRow(
            row: CatBookRow,
            open: Boolean,
        ) {
            when (row) {
                is CatBookRow.Jump -> handlePickJump(row.reference)
                CatBookRow.TextSearch -> handleTextSearch()
                is CatBookRow.Author -> if (open) handleOpenAuthor(row.hit) else handlePickAuthor(row.hit)
                is CatBookRow.Book -> if (open) handleOpenBook(row.suggestion) else handlePickBook(row.suggestion)
            }
        }

        fun pickCatBookRow(
            index: Int,
            open: Boolean,
        ) {
            catBookRows.getOrNull(index)?.let { runCatBookRow(it, open) }
        }

        fun handleSubmit() {
            onSubmit()
            dismissPopup()
        }

        fun submitAfterFrame(
            @StructuredScope scope: CoroutineScope,
        ) {
            scope.launch {
                withFrameNanos { }
                handleSubmit()
            }
        }

        // Opens a TOC entry as Enter does
        fun openToc(toc: TocSuggestion) {
            handlePickToc(toc)
            if (submitOnEnterInReference) submitAfterFrame(scope)
        }

        TextField(
            state = state,
            style = rememberPillTextFieldStyle(),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        anchor =
                            AnchorBounds(
                                windowOffset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt()),
                                size = IntSize(coords.size.width, coords.size.height),
                            )
                    }.onPreviewKeyEvent { ev ->
                        when {
                            ev.key == Key.Backspace && !isTocMode && selectedAuthor != null -> {
                                when (ev.type) {
                                    KeyEventType.KeyDown -> {
                                        backspaceStartedEmpty = state.text.isEmpty()
                                        false
                                    }

                                    KeyEventType.KeyUp -> {
                                        val shouldClear = backspaceStartedEmpty && state.text.isEmpty()
                                        backspaceStartedEmpty = false
                                        if (shouldClear) onClearAuthor()
                                        shouldClear
                                    }

                                    else -> false
                                }
                            }

                            ev.key == Key.Backspace && isTocMode -> {
                                when (ev.type) {
                                    KeyEventType.KeyDown -> {
                                        backspaceStartedEmpty = state.text.isEmpty()
                                        false
                                    }

                                    KeyEventType.KeyUp -> {
                                        val shouldClear =
                                            backspaceStartedEmpty &&
                                                state.text.isEmpty() &&
                                                onClearBook != null &&
                                                canClearBookOnBackspace()
                                        backspaceStartedEmpty = false
                                        if (shouldClear) {
                                            onClearBook()
                                            true
                                        } else {
                                            false
                                        }
                                    }

                                    else -> false
                                }
                            }
                            textSearchEnabled &&
                                ev.isCtrlPressed &&
                                (ev.key == Key.Enter || ev.key == Key.NumPadEnter) &&
                                ev.type == KeyEventType.KeyUp -> {
                                handleTextSearch(extended = true)
                                true
                            }

                            (ev.key == Key.Enter || ev.key == Key.NumPadEnter) && ev.type == KeyEventType.KeyUp -> {
                                // Run the focused row
                                when {
                                    isTocMode && focusedIndex in 0 until totalToc -> {
                                        handlePickToc(visibleTocSuggestions[focusedIndex].first)
                                        if (submitOnEnterInReference) {
                                            submitAfterFrame(scope)
                                        }
                                        true
                                    }

                                    isTocMode && textRow && focusedIndex == totalToc -> {
                                        handleTextSearch()
                                        true
                                    }

                                    !isTocMode && focusedIndex in 0 until totalCatBook -> {
                                        pickCatBookRow(focusedIndex, open = true)
                                        true
                                    }

                                    submitOnEnterInReference && selectedBook != null -> {
                                        submitAfterFrame(scope)
                                        true
                                    }

                                    textSearchEnabled -> {
                                        handleTextSearch()
                                        true
                                    }

                                    else -> true
                                }
                            }

                            // Arrows move through the open list as soon as they are pressed, repeating while
                            // held (like Chrome's omnibox); neither event reaches the field, whose caret stays put
                            (ev.key == Key.DirectionDown || ev.key == Key.DirectionUp) && popupVisible -> {
                                if (ev.type == KeyEventType.KeyDown) {
                                    val total = if (isTocMode) totalTocRows else totalCatBook
                                    val step = if (ev.key == Key.DirectionDown) 1 else -1
                                    if (total > 0) focusedIndex = (focusedIndex + step).coerceIn(0, total - 1)
                                }
                                true
                            }

                            ev.key == Key.Escape && ev.type == KeyEventType.KeyUp -> {
                                popupVisible = false
                                onDismissSuggestions()
                                true
                            }

                            // Consume Tab KeyDown when suggestions are visible
                            // to prevent default focus movement before our KeyUp handler runs
                            ev.key == Key.Tab && ev.type == KeyEventType.KeyDown && popupVisible -> {
                                true
                            }

                            ev.key == Key.Tab && ev.type == KeyEventType.KeyUp -> {
                                val handled =
                                    when {
                                        isTocMode && focusedIndex in 0 until totalToc -> {
                                            handlePickToc(visibleTocSuggestions[focusedIndex].first)
                                            true
                                        }

                                        !isTocMode && focusedIndex in 0 until totalCatBook -> {
                                            pickCatBookRow(focusedIndex, open = false)
                                            true
                                        }

                                        else -> false
                                    }
                                if (handled && submitOnEnterInReference && isTocMode) {
                                    submitAfterFrame(scope)
                                }
                                true
                            }

                            else -> false
                        }
                    }.focusRequester(effectiveFocusRequester)
                    .onFocusChanged { if (it.isFocused) openRequests++ }
                    // A click in the field (to move the caret) counts as outside the popup: it reopens it
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            openRequests++
                        }
                    },
            enabled = enabled,
            placeholder = {
                if (placeholderText != null) {
                    Text(
                        placeholderText,
                        style = TextStyle(fontSize = 13.sp, color = Color(0xFF9AA0A6)),
                        maxLines = 1,
                    )
                } else {
                    val windowInfo = LocalWindowInfo.current
                    val typewriterStyle = remember { TextStyle(fontSize = 13.sp, color = Color(0xFF9AA0A6)) }
                    key(selectedBook?.id) {
                        TypewriterPlaceholder(
                            hints = hints,
                            textStyle = typewriterStyle,
                            typingDelayMs = 120L,
                            deletingDelayMs = 55L,
                            holdDelayMs = 1600L,
                            preTypePauseMs = 500L,
                            postDeletePauseMs = 450L,
                            punctuationExtraDelayMs = 180L,
                            enabled = !isUserTyping && windowInfo.isWindowFocused,
                        )
                    }
                }
            },
            // Clears the typed text, keeping the picked book
            trailingIcon = {
                if (state.text.isNotEmpty()) {
                    Icon(
                        AllIconsKeys.Actions.Close,
                        stringResource(Res.string.search_clear),
                        Modifier
                            .clickable {
                                state.clearText()
                                effectiveFocusRequester.requestFocus()
                            }.pointerHoverIcon(PointerIcon.Hand),
                    )
                }
            },
            leadingIcon = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // A sign that it's a search field, not a button: Enter and the rows search
                    Icon(
                        key = AllIconsKeys.Actions.Find,
                        contentDescription = stringResource(Res.string.search_icon_description),
                        modifier = Modifier.size(16.dp),
                    )
                    if (selectedBook != null && onClearBook != null) {
                        SelectedBookChip(
                            title = selectedBook.title,
                            onClear = {
                                onClearBook()
                                effectiveFocusRequester.requestFocus()
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                    } else if (selectedAuthor != null) {
                        SelectedBookChip(
                            title = AuthorNames.display(selectedAuthor),
                            onClear = {
                                onClearAuthor()
                                effectiveFocusRequester.requestFocus()
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
            },
            textStyle = TextStyle(fontSize = 13.sp),
        )

        // Overlay suggestions anchored under the TextField
        val a = anchor
        val showOverlay =
            isTabSelected &&
                openRequests > 0 &&
                popupVisible &&
                a != null &&
                (
                    showTocSuggestions ||
                        showBookStageSuggestions ||
                        showBookEmptyState ||
                        showTocEmptyState ||
                        showBookLoading ||
                        showTocLoading
                )
        if (showOverlay) {
            val provider =
                remember(a) {
                    object : PopupPositionProvider {
                        override fun calculatePosition(
                            anchorBounds: IntRect,
                            windowSize: IntSize,
                            layoutDirection: LayoutDirection,
                            popupContentSize: IntSize,
                        ): IntOffset {
                            // Base position under the TextField using measured bounds (already in window coords)
                            val padding = 8
                            var x = anchorBounds.left
                            var y = anchorBounds.bottom + padding
                            // Clamp horizontally inside window
                            if (x + popupContentSize.width > windowSize.width) {
                                x = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                            }
                            // Clamp vertically inside window (prefer below, otherwise above)
                            if (y + popupContentSize.height > windowSize.height) {
                                val aboveY = a.windowOffset.y - popupContentSize.height - 4
                                y = aboveY.coerceAtLeast(0)
                            }
                            return IntOffset(x, y)
                        }
                    }
                }
            Popup(
                popupPositionProvider = provider,
                // A click outside closes it, as any menu; the field's focus brings it back
                properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
                onDismissRequest = { popupVisible = false },
            ) {
                val widthDp = with(LocalDensity.current) { a.size.width.toDp() }
                Box(Modifier.width(widthDp)) {
                    if (isTocMode && (showTocSuggestions || showTocEmptyState || showTocLoading)) {
                        TocSuggestionsPanel(
                            suggestions = visibleTocSuggestions,
                            onPickToc = ::openToc,
                            textSearchLabel = if (textRow) textSearchLabel(state.text.toString().trim(), textSearchInBook) else null,
                            textSearchQuery = state.text.toString().trim(),
                            onTextSearch = { handleTextSearch() },
                            focusedIndex = focusedIndex,
                            emptyMessage = if (showTocEmptyState) stringResource(Res.string.autocomplete_no_results) else null,
                            isLoading = showTocLoading,
                            loadingMessage = stringResource(Res.string.autocomplete_loading),
                        )
                    } else if (!isTocMode && (showBookStageSuggestions || showBookEmptyState || showBookLoading)) {
                        SuggestionsPanel(
                            rows = catBookRows,
                            onRow = { row, open -> runCatBookRow(row, open) },
                            onTextSearchAll = { handleTextSearch(extended = true) },
                            textSearchLabel =
                                if (textSearchEnabled &&
                                    state.text.isNotBlank()
                                ) {
                                    textSearchLabel(state.text.toString().trim(), null)
                                } else {
                                    null
                                },
                            textSearchQuery = state.text.toString().trim(),
                            focusedIndex = focusedIndex,
                            emptyMessage = if (showBookEmptyState) stringResource(Res.string.autocomplete_no_results) else null,
                            isLoading = showBookLoading,
                            loadingMessage = stringResource(Res.string.autocomplete_loading),
                        )
                    }
                }
            }
        }
    }
}

/** One thing a suggestion row does: its keys (`↵`, `Tab`) and what they do. */
@Immutable
private class RowAction(
    val keys: String,
    val label: String,
    val onClick: () -> Unit,
)

private const val KEY_ENTER = "↵"
private const val KEY_TAB = "Tab"
private const val KEY_CTRL_ENTER = "Ctrl+↵"

/** A row action as a quiet button: its label, then its key in grey; a neutral tint, darker on hover. */
@Composable
private fun RowActionChip(action: RowAction) {
    val shape = RoundedCornerShape(6.dp)
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val ink = JewelTheme.globalColors.text.normal
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .clip(shape)
                .background(ink.copy(alpha = if (hovered) 0.14f else 0.07f))
                .hoverable(hover)
                .clickable(onClick = action.onClick)
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(action.label, fontSize = 11.sp, color = ink, maxLines = 1, softWrap = false)
        Text(
            action.keys,
            fontSize = 10.sp,
            color = JewelTheme.globalColors.text.info,
            maxLines = 1,
            softWrap = false,
            // Keys read left to right (Ctrl+↵), whatever the layout
            style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
        )
    }
}

@Composable
private fun SelectedBookChip(
    title: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(JewelTheme.globalColors.panelBackground)
                .border(1.dp, JewelTheme.globalColors.borders.disabled, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .pointerHoverIcon(PointerIcon.Hand),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.normal,
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            key = AllIconsKeys.Windows.Close,
            contentDescription = stringResource(Res.string.remove_selected_book),
            modifier = Modifier.size(12.dp).clickable(onClick = onClear).pointerHoverIcon(PointerIcon.Hand),
            tint = JewelTheme.globalColors.text.disabled,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Preview
@Composable
private fun HomeViewPreview() {
    PreviewContainer {
        // Minimal stub state for preview; SearchHomeViewModel is not used here.
        val stubSearchUi = SearchHomeUiState()
        val stubCallbacks =
            HomeSearchCallbacks(
                onReferenceQueryChanged = {},
                onTocQueryChanged = {},
                onGlobalExtendedChange = {},
                onSubmitTextSearch = {},
                onOpenReference = {},
                onPickBook = {},
                onPickToc = {},
            )
        HomeView(
            onEvent = {},
            searchUi = stubSearchUi,
            searchCallbacks = stubCallbacks,
            homeUserLocation = HomeUserLocation.preview,
            modifier = Modifier,
        )
    }
}

/** [resource] decoded on the IO dispatcher, once for the app: null until then. */
@Composable
private fun rememberDecodedImage(resource: DrawableResource): ImageBitmap? {
    val environment = rememberResourceEnvironment()
    return produceState(decodedImages[resource], resource, environment) {
        value = decodedImages[resource]
            ?: withContext(Dispatchers.IO) { getDrawableResourceBytes(environment, resource).decodeToImageBitmap() }
                .also { decodedImages[resource] = it }
    }.value
}

private val decodedImages = ConcurrentHashMap<DrawableResource, ImageBitmap>()
