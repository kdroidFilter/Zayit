@file:OptIn(ExperimentalJewelApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.components.CustomToggleableChip
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.components.CatalogRow
import io.github.kdroidfilter.seforimapp.features.search.SearchFilter
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeUiState
import io.github.kdroidfilter.seforimapp.icons.*
import io.github.kdroidfilter.seforimapp.texteffects.TypewriterPlaceholder
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.menuStyle
import org.jetbrains.skiko.Cursor
import seforimapp.seforimapp.generated.resources.*
import kotlin.math.roundToInt
import io.github.kdroidfilter.seforimlibrary.core.models.Book as BookModel

// Suggestion models for the scope picker
@Immutable
private data class CategorySuggestion(
    val category: Category,
    val path: List<String>,
)

@Immutable
private data class BookSuggestion(
    val book: BookModel,
    val path: List<String>,
)

@Immutable
private data class TocSuggestion(
    val toc: TocEntry,
    val path: List<String>,
)

private data class AnchorBounds(
    val windowOffset: IntOffset,
    val size: IntSize,
)

data class SearchFilterCard(
    val icons: ImageVector,
    val label: StringResource,
    val desc: StringResource,
    val explanation: StringResource,
)

/**
 * Callbacks used by [HomeView] to delegate all search-related
 * interactions to the SearchHomeViewModel without referencing it
 * directly inside UI code.
 */
data class HomeSearchCallbacks(
    val onReferenceQueryChanged: (String) -> Unit,
    val onTocQueryChanged: (String) -> Unit,
    val onFilterChange: (SearchFilter) -> Unit,
    val onGlobalExtendedChange: (Boolean) -> Unit,
    val onSubmitTextSearch: (String) -> Unit,
    val onOpenReference: () -> Unit,
    val onPickCategory: (Category) -> Unit,
    val onPickBook: (BookModel) -> Unit,
    val onPickToc: (TocEntry) -> Unit,
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
    homeCelestialWidgetsState: HomeCelestialWidgetsState? = null,
) {
    CatalogRow(onEvent = onEvent)

    Box(modifier = modifier.fillMaxSize()) {
        HomeBody(
            searchUi = searchUi,
            searchCallbacks = searchCallbacks,
            homeCelestialWidgetsState = homeCelestialWidgetsState,
        )
    }
}

/**
 * Home screen for the Book Content feature.
 *
 * Renders the welcome header, the main search bar with a mode toggle (Text vs Reference),
 * and the Category/Book/TOC scope picker. State is sourced from the SearchHomeViewModel
 * through the Metro DI graph and kept outside of the LazyColumn to avoid losing focus or
 * field contents during recomposition.
 */
@OptIn(ExperimentalJewelApi::class, ExperimentalLayoutApi::class)
@Composable
private fun HomeBody(
    searchUi: SearchHomeUiState,
    searchCallbacks: HomeSearchCallbacks,
    homeCelestialWidgetsState: HomeCelestialWidgetsState?,
) {
    // Global zoom level from AppSettings; used to scale Home view uniformly
    val rawTextSize by AppSettings.textSizeFlow.collectAsState()
    // Whether to show zmanim widgets
    val showZmanimWidgets by AppSettings.showZmanimWidgetsFlow.collectAsState()
    // Apply a gentler zoom curve on the Home screen only: keep default size identical,
    // but soften +/- zoom steps so they feel less strong here than globally.
    val homeScale =
        remember(rawTextSize) {
            val ratio = rawTextSize / AppSettings.DEFAULT_TEXT_SIZE
            val softenFactor = 0.3f
            1f + (ratio - 1f) * softenFactor
        }

    val celestialWidgetsState =
        if (homeCelestialWidgetsState != null) {
            homeCelestialWidgetsState
        } else {
            val viewModel: HomeCelestialWidgetsViewModel =
                metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
            val state by viewModel.state.collectAsState()
            state
        }

    val listState = rememberLazyListState()
    // Delay the first render of the Home content slightly so that
    // layout constraints and zoom are settled before it appears.
    var showHomeContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // A small delay avoids the visual jump when the 600.dp container
        // is first centered and scaled.
        delay(10)
        showHomeContent = true
    }

    VerticallyScrollableContainer(
        scrollState = listState as ScrollableState,
    ) {
        if (!showHomeContent) {
            // Reserve space but do not draw the Home content yet.
            Box(modifier = Modifier.fillMaxSize())
            return@VerticallyScrollableContainer
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // Keep a fixed logical width for the Home content (600.dp) and clamp the
            // scale if the window is too narrow so that the scaled content never
            // overflows horizontally. Padding is derived from the effective scale so
            // it stops growing once the layout itself is clamped.
            val baseWidth = 600.dp
            val maxScaleForWidth = (maxWidth.value / baseWidth.value).coerceAtLeast(0f)
            val clampedScale = homeScale.coerceAtMost(maxScaleForWidth).coerceAtLeast(0f)

            // Scale paddings and spacing proportionally so zoomed content has adequate room
            val scaledTopPadding = (56.dp * clampedScale).coerceAtLeast(56.dp)
            val scaledSpacing = (16.dp * clampedScale).coerceAtLeast(16.dp)

            Box(
                modifier = Modifier.padding(top = scaledTopPadding).fillMaxSize().padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Keep state outside LazyColumn so it persists across item recompositions
                val scope = rememberCoroutineScope()
                val searchState = remember { TextFieldState() }
                val referenceSearchState = remember { TextFieldState() }
                val tocSearchState = remember { TextFieldState() }
                var skipNextReferenceQuery by remember { mutableStateOf(false) }
                var skipNextTocQuery by remember { mutableStateOf(false) }
                var tocEditedSinceBook by remember { mutableStateOf(false) }
                // Shared focus requester for the MAIN search bar so other UI (e.g., level changes)
                // can reliably return focus to it, allowing immediate Enter to submit.
                val mainSearchFocusRequester = remember { FocusRequester() }
                // Focus requester for the secondary search bar in ReferenceByCategorySection
                val referenceSectionFocusRequester = remember { FocusRequester() }
                var scopeExpanded by remember { mutableStateOf(false) }
                // Forward reference input changes to the ViewModel (VM handles debouncing and suggestions)
                LaunchedEffect(Unit) {
                    snapshotFlow { referenceSearchState.text.toString() }.collect { qRaw ->
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
                        if (skipNextTocQuery) {
                            skipNextTocQuery = false
                            tocEditedSinceBook = qRaw.isNotBlank()
                        } else {
                            tocEditedSinceBook = qRaw.isNotBlank()
                            searchCallbacks.onTocQueryChanged(qRaw)
                        }
                    }
                }

                fun launchSearch() {
                    val query = searchState.text.toString().trim()
                    if (query.isBlank() || searchUi.selectedFilter != SearchFilter.TEXT) return
                    searchCallbacks.onSubmitTextSearch(query)
                }

                fun openReference() {
                    searchCallbacks.onOpenReference()
                }

                // Book-only placeholder hints for the first field (reference mode)
                val bookOnlyHintsGlobal =
                    listOf(
                        stringResource(Res.string.reference_book_hint_1),
                        stringResource(Res.string.reference_book_hint_2),
                        stringResource(Res.string.reference_book_hint_3),
                        stringResource(Res.string.reference_book_hint_4),
                        stringResource(Res.string.reference_book_hint_5),
                    )

                // Main search field focus handled inside SearchBar via autoFocus

                val homeContentModifier =
                    Modifier.width(baseWidth).graphicsLayer(
                        scaleX = clampedScale,
                        scaleY = clampedScale,
                    )

                // Logo and search bar base heights - used to reserve correct layout space
                val logoBaseSize = 220.dp
                val searchBarBaseHeight = 40.dp

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(scaledSpacing),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item {
                        // Reserve scaled height so the visually scaled logo doesn't overflow
                        Box(
                            Modifier.fillMaxWidth().height(logoBaseSize * clampedScale),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(homeContentModifier, contentAlignment = Alignment.Center) {
                                LogoImage()
                            }
                        }
                    }
                    item {
                        // Reserve scaled height for the search bar
                        Box(
                            Modifier.fillMaxWidth().height(searchBarBaseHeight * clampedScale),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(homeContentModifier) {
                                // In REFERENCE mode, repurpose the first TextField as the predictive
                                // Book picker (with Category/Book suggestions). Enter should NOT open.
                                val isReferenceMode = searchUi.selectedFilter == SearchFilter.REFERENCE
                                // When switching to REFERENCE mode, focus the first (top) text field
                                LaunchedEffect(searchUi.selectedFilter) {
                                    // When switching modes, always focus the top text field
                                    if (searchUi.selectedFilter == SearchFilter.REFERENCE || searchUi.selectedFilter == SearchFilter.TEXT) {
                                        // small delay to ensure composition is settled
                                        delay(100)
                                        mainSearchFocusRequester.requestFocus()

                                        // Preserve what the user typed when toggling modes. If the destination
                                        // field is empty, copy the current text from the other field so users
                                        // don't have to retype after realizing they were in the wrong mode.
                                        when (searchUi.selectedFilter) {
                                            SearchFilter.TEXT -> {
                                                val from = referenceSearchState.text.toString()
                                                if (from.isNotBlank() && searchState.text.isEmpty()) {
                                                    searchState.edit { replace(0, length, from) }
                                                }
                                            }

                                            SearchFilter.REFERENCE -> {
                                                val from = searchState.text.toString()
                                                if (from.isNotBlank() && referenceSearchState.text.isEmpty()) {
                                                    referenceSearchState.edit { replace(0, length, from) }
                                                }
                                            }
                                        }
                                    }
                                }
                                LaunchedEffect(searchUi.selectedScopeBook?.id, isReferenceMode) {
                                    if (isReferenceMode && searchUi.selectedScopeBook != null) {
                                        delay(80)
                                        mainSearchFocusRequester.requestFocus()
                                    }
                                }
                                val mappedBookSuggestionsForBar =
                                    searchUi.bookSuggestions.map { bs ->
                                        BookSuggestion(bs.book, bs.path)
                                    }
                                val mappedTocSuggestionsForBar =
                                    searchUi.tocSuggestions.map { ts ->
                                        TocSuggestion(ts.toc, ts.path)
                                    }
                                val breadcrumbSeparatorTop = stringResource(Res.string.breadcrumb_separator)
                                val isTocInTopBar = isReferenceMode && searchUi.selectedScopeBook != null
                                val tocHintsForBar =
                                    searchUi.tocPreviewHints.ifEmpty {
                                        listOf(
                                            stringResource(Res.string.reference_toc_hint_1),
                                            stringResource(Res.string.reference_toc_hint_2),
                                            stringResource(Res.string.reference_toc_hint_3),
                                            stringResource(Res.string.reference_toc_hint_4),
                                            stringResource(Res.string.reference_toc_hint_5),
                                        )
                                    }
                                SearchBar(
                                    state =
                                        when {
                                            isTocInTopBar -> tocSearchState
                                            isReferenceMode -> referenceSearchState
                                            else -> searchState
                                        },
                                    selectedFilter = searchUi.selectedFilter,
                                    onFilterChange = { searchCallbacks.onFilterChange(it) },
                                    onSubmit =
                                        if (isReferenceMode) {
                                            { openReference() }
                                        } else {
                                            { launchSearch() }
                                        },
                                    onTab = {
                                        if (!isReferenceMode) {
                                            // Text mode: expand the scope section and focus secondary bar
                                            scopeExpanded = true
                                            scope.launch {
                                                delay(100) // Wait for composition to settle
                                                referenceSectionFocusRequester.requestFocus()
                                            }
                                        }
                                    },
                                    modifier = Modifier,
                                    showIcon = !isReferenceMode,
                                    focusRequester = mainSearchFocusRequester,
                                    // Suggestions: in REFERENCE mode show only books; in TEXT mode none here
                                    suggestionsVisible = if (isReferenceMode && !isTocInTopBar) searchUi.suggestionsVisible else false,
                                    categorySuggestions = emptyList(),
                                    bookSuggestions = if (isReferenceMode && !isTocInTopBar) mappedBookSuggestionsForBar else emptyList(),
                                    tocSuggestionsVisible = isTocInTopBar && searchUi.tocSuggestionsVisible,
                                    tocSuggestions = if (isTocInTopBar) mappedTocSuggestionsForBar else emptyList(),
                                    selectedBook = searchUi.selectedScopeBook,
                                    placeholderHints =
                                        when {
                                            !isReferenceMode -> null
                                            isTocInTopBar -> tocHintsForBar
                                            else -> bookOnlyHintsGlobal
                                        },
                                    placeholderText = null,
                                    submitOnEnterInReference = isReferenceMode && isTocInTopBar,
                                    globalExtended = searchUi.globalExtended,
                                    onGlobalExtendedChange = { searchCallbacks.onGlobalExtendedChange(it) },
                                    parentScale = homeScale,
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
                                        scope.launch {
                                            delay(80)
                                            mainSearchFocusRequester.requestFocus()
                                        }
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
                        }
                    }
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(homeContentModifier) {
                                if (searchUi.selectedFilter == SearchFilter.REFERENCE) {
                                    Spacer(Modifier.height(32.dp))
                                } else {
                                    Column(
                                        modifier = Modifier.heightIn(min = 32.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        val breadcrumbSeparator = stringResource(Res.string.breadcrumb_separator)
                                        val mappedCategorySuggestions =
                                            searchUi.categorySuggestions.map { cs ->
                                                CategorySuggestion(cs.category, cs.path)
                                            }
                                        val mappedBookSuggestions =
                                            searchUi.bookSuggestions.map { bs ->
                                                BookSuggestion(bs.book, bs.path)
                                            }
                                        val mappedTocSuggestions =
                                            searchUi.tocSuggestions.map { ts ->
                                                TocSuggestion(ts.toc, ts.path)
                                            }

                                        ReferenceByCategorySection(
                                            state = referenceSearchState,
                                            tocState = tocSearchState,
                                            isExpanded = scopeExpanded,
                                            onExpandedChange = { scopeExpanded = it },
                                            suggestionsVisible = searchUi.suggestionsVisible,
                                            categorySuggestions = mappedCategorySuggestions,
                                            bookSuggestions = mappedBookSuggestions,
                                            selectedBook = searchUi.selectedScopeBook,
                                            selectedCategory = searchUi.selectedScopeCategory,
                                            tocSuggestionsVisible = searchUi.tocSuggestionsVisible,
                                            tocSuggestions = mappedTocSuggestions,
                                            onSubmit = { launchSearch() },
                                            submitAfterPick = false,
                                            submitOnEnterIfSelection = true,
                                            tocPreviewHints = searchUi.tocPreviewHints,
                                            showHeader = true,
                                            focusRequester = referenceSectionFocusRequester,
                                            onPickCategory = { picked ->
                                                searchCallbacks.onPickCategory(picked.category)
                                                val full = dedupAdjacent(picked.path).joinToString(breadcrumbSeparator)
                                                skipNextReferenceQuery = true
                                                referenceSearchState.edit { replace(0, length, full) }
                                            },
                                            onPickBook = { picked ->
                                                searchCallbacks.onPickBook(picked.book)
                                                skipNextReferenceQuery = true
                                                referenceSearchState.edit { replace(0, length, "") }
                                                skipNextTocQuery = true
                                                tocSearchState.edit { replace(0, length, "") }
                                                skipNextTocQuery = false
                                            },
                                            onPickToc = { picked ->
                                                searchCallbacks.onPickToc(picked.toc)
                                                val dedup = dedupAdjacent(picked.path)
                                                val stripped = stripBookPrefixFromTocPath(searchUi.selectedScopeBook, dedup)
                                                val display = stripped.joinToString(breadcrumbSeparator)
                                                skipNextTocQuery = true
                                                tocSearchState.edit { replace(0, length, display) }
                                            },
                                            onClearBook = {
                                                searchCallbacks.onReferenceQueryChanged("")
                                                searchCallbacks.onTocQueryChanged("")
                                                skipNextReferenceQuery = true
                                                skipNextTocQuery = true
                                                referenceSearchState.edit { replace(0, length, "") }
                                                tocSearchState.edit { replace(0, length, "") }
                                            },
                                            parentScale = homeScale,
                                            isBookLoading = searchUi.isReferenceLoading,
                                            isTocLoading = searchUi.isTocLoading,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (showZmanimWidgets) {
                        item {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, bottom = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                HomeCelestialWidgets(
                                    modifier = Modifier.fillMaxWidth(),
                                    userCommunityCode = searchUi.userCommunityCode,
                                    locationState = celestialWidgetsState,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** App logo shown on the Home screen. */
@Composable
private fun LogoImage(modifier: Modifier = Modifier) {
    Image(
        painterResource(Res.drawable.zayit_transparent),
        contentDescription = null,
        modifier = modifier.size(220.dp),
    )
}

/**
 * Panel showing the 5 text-search levels as selectable cards synchronized
 * with a slider. Encapsulates its own local selection state.
 */
@Composable
/**
 * Displays the five text-search levels as selectable cards synchronized with
 * a slider. The slider and cards mirror the same selection index.
 */
private fun SearchLevelsPanel(
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filterCards: List<SearchFilterCard> =
        listOf(
            SearchFilterCard(
                Target,
                Res.string.search_level_1_value,
                Res.string.search_level_1_description,
                Res.string.search_level_1_explanation,
            ),
            SearchFilterCard(
                Link,
                Res.string.search_level_2_value,
                Res.string.search_level_2_description,
                Res.string.search_level_2_explanation,
            ),
            SearchFilterCard(
                Format_letter_spacing,
                Res.string.search_level_3_value,
                Res.string.search_level_3_description,
                Res.string.search_level_3_explanation,
            ),
            SearchFilterCard(
                Article,
                Res.string.search_level_4_value,
                Res.string.search_level_4_description,
                Res.string.search_level_4_explanation,
            ),
            SearchFilterCard(
                Book,
                Res.string.search_level_5_value,
                Res.string.search_level_5_description,
                Res.string.search_level_5_explanation,
            ),
        )

    // Synchronize cards with slider position
    var sliderPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) { sliderPosition = selectedIndex.toFloat() }
    val maxIndex = (filterCards.size - 1).coerceAtLeast(0)
    val coercedSelected = sliderPosition.coerceIn(0f, maxIndex.toFloat()).toInt()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            filterCards.forEachIndexed { index, filterCard ->
                SearchLevelCard(
                    data = filterCard,
                    selected = index == coercedSelected,
                    onClick = {
                        sliderPosition = index.toFloat()
                        onSelectedIndexChange(index)
                    },
                )
            }
        }

        Slider(
            value = sliderPosition,
            onValueChange = { newValue ->
                sliderPosition = newValue
                onSelectedIndexChange(newValue.coerceIn(0f, maxIndex.toFloat()).toInt())
            },
            valueRange = 0f..maxIndex.toFloat(),
            steps = (filterCards.size - 2).coerceAtLeast(0),
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        )
    }
}

@Composable
/**
 * Category/Book/TOC scope picker with predictive suggestions.
 *
 * Left field: Categories and Books. Right field: TOC of the selected book.
 * Both inputs support keyboard navigation (↑/↓/Enter) and mouse selection.
 *
 * The caller controls suggestion visibility and supplies current suggestions.
 * When [submitAfterPick] is true (reference mode), selecting a suggestion triggers [onSubmit].
 */
private fun ReferenceByCategorySection(
    modifier: Modifier = Modifier,
    state: TextFieldState? = null,
    tocState: TextFieldState? = null,
    isExpanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    suggestionsVisible: Boolean = false,
    categorySuggestions: List<CategorySuggestion> = emptyList(),
    bookSuggestions: List<BookSuggestion> = emptyList(),
    selectedBook: BookModel? = null,
    selectedCategory: Category? = null,
    tocSuggestionsVisible: Boolean = false,
    tocSuggestions: List<TocSuggestion> = emptyList(),
    onSubmit: () -> Unit = {},
    submitAfterPick: Boolean = false,
    submitOnEnterIfSelection: Boolean = false,
    tocPreviewHints: List<String> = emptyList(),
    showHeader: Boolean = true,
    onPickCategory: (CategorySuggestion) -> Unit = {},
    onPickBook: (BookSuggestion) -> Unit = {},
    onPickToc: (TocSuggestion) -> Unit = {},
    onClearBook: () -> Unit = {},
    // Scale applied to Home content; forwarded to inner SearchBars
    parentScale: Float = 1f,
    isBookLoading: Boolean = false,
    isTocLoading: Boolean = false,
    // Focus requester for the internal search bar
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val refState = state ?: remember { TextFieldState() }
    val tocTfState = tocState ?: remember { TextFieldState() }
    val isTocMode = selectedBook != null
    val breadcrumbSeparator = stringResource(Res.string.breadcrumb_separator)
    val bookHints =
        listOf(
            stringResource(Res.string.reference_book_hint_1),
            stringResource(Res.string.reference_book_hint_2),
            stringResource(Res.string.reference_book_hint_3),
            stringResource(Res.string.reference_book_hint_4),
            stringResource(Res.string.reference_book_hint_5),
        )
    val tocHints =
        tocPreviewHints.ifEmpty {
            listOf(
                stringResource(Res.string.reference_toc_hint_1),
                stringResource(Res.string.reference_toc_hint_2),
                stringResource(Res.string.reference_toc_hint_3),
                stringResource(Res.string.reference_toc_hint_4),
                stringResource(Res.string.reference_toc_hint_5),
            )
        }
    val activeState = if (isTocMode) tocTfState else refState

    Column(modifier.fillMaxWidth()) {
        if (showHeader) {
            GroupHeader(
                text = stringResource(Res.string.search_by_category_or_book),
                modifier =
                    Modifier
                        .clickable(indication = null, interactionSource = interactionSource) {
                            onExpandedChange(!isExpanded)
                        }.hoverable(interactionSource)
                        .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))),
                startComponent = {
                    if (isExpanded) {
                        Icon(AllIconsKeys.General.ChevronDown, stringResource(Res.string.chevron_icon_description))
                    } else {
                        Icon(AllIconsKeys.General.ChevronLeft, stringResource(Res.string.chevron_icon_description))
                    }
                },
            )
        }

        if (!showHeader || isExpanded) {
            SearchBar(
                state = activeState,
                selectedFilter = SearchFilter.REFERENCE,
                onFilterChange = {},
                showToggle = false,
                showIcon = false,
                enabled = true,
                suggestionsVisible = suggestionsVisible && !isTocMode,
                categorySuggestions = if (isTocMode) emptyList() else categorySuggestions,
                bookSuggestions = if (isTocMode) emptyList() else bookSuggestions,
                selectedBook = selectedBook,
                selectedCategory = selectedCategory,
                placeholderHints = if (isTocMode) tocHints else bookHints,
                tocSuggestionsVisible = tocSuggestionsVisible && isTocMode,
                tocSuggestions = if (isTocMode) tocSuggestions else emptyList(),
                onPickCategory = { picked ->
                    onPickCategory(picked)
                },
                onPickBook = { picked ->
                    onPickBook(picked)
                    refState.edit { replace(0, length, "") }
                    tocTfState.edit { replace(0, length, "") }
                },
                onPickToc = { picked ->
                    onPickToc(picked)
                    val dedup = dedupAdjacent(picked.path)
                    val display = stripBookPrefixFromTocPath(selectedBook, dedup).joinToString(breadcrumbSeparator)
                    tocTfState.edit { replace(0, length, display) }
                    if (submitAfterPick) onSubmit()
                },
                onSubmit = onSubmit,
                submitOnEnterIfSelection = submitOnEnterIfSelection,
                submitOnEnterInReference = submitAfterPick,
                autoFocus = false,
                onClearBook = {
                    onClearBook()
                    refState.edit { replace(0, length, "") }
                    tocTfState.edit { replace(0, length, "") }
                },
                parentScale = parentScale,
                isBookLoading = isBookLoading,
                isTocLoading = isTocLoading,
                focusRequester = focusRequester,
            )
        }
    }
}

@Composable
/**
 * Renders the suggestion list for categories and books, keeping the currently
 * focused row in view as the user navigates with the keyboard.
 * Uses native Jewel menu styling for consistent look and feel.
 */
private fun SuggestionsPanel(
    categorySuggestions: List<CategorySuggestion>,
    bookSuggestions: List<BookSuggestion>,
    onPickCategory: (CategorySuggestion) -> Unit,
    onPickBook: (BookSuggestion) -> Unit,
    focusedIndex: Int = -1,
    emptyMessage: String? = null,
    isLoading: Boolean = false,
    loadingMessage: String? = null,
) {
    val listState = rememberLazyListState()
    val menuStyle = JewelTheme.menuStyle

    LaunchedEffect(focusedIndex, categorySuggestions.size, bookSuggestions.size) {
        if (focusedIndex >= 0) {
            val total = categorySuggestions.size + bookSuggestions.size
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
    val isEmpty = categorySuggestions.isEmpty() && bookSuggestions.isEmpty()
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
                items(categorySuggestions.size) { idx ->
                    val cat = categorySuggestions[idx]
                    val dedupPath = dedupAdjacent(cat.path)
                    SuggestionRow(
                        parts = dedupPath,
                        onClick = { onPickCategory(cat) },
                        highlighted = idx == focusedIndex,
                        showTabHint = idx == focusedIndex,
                    )
                }
                items(bookSuggestions.size) { i ->
                    val rowIndex = categorySuggestions.size + i
                    val book = bookSuggestions[i]
                    val dedupPath = dedupAdjacent(book.path)
                    SuggestionRow(
                        parts = dedupPath,
                        onClick = { onPickBook(book) },
                        highlighted = rowIndex == focusedIndex,
                        showTabHint = rowIndex == focusedIndex,
                    )
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
    tocSuggestions: List<TocSuggestion>,
    onPickToc: (TocSuggestion) -> Unit,
    focusedIndex: Int = -1,
    selectedBook: BookModel? = null,
    emptyMessage: String? = null,
    isLoading: Boolean = false,
    loadingMessage: String? = null,
) {
    val filteredSuggestions =
        remember(tocSuggestions, selectedBook) {
            tocSuggestions.mapNotNull { ts ->
                val dedupPath = dedupAdjacent(ts.path)
                val parts = stripBookPrefixFromTocPath(selectedBook, dedupPath)
                if (parts.isNotEmpty()) ts to parts else null
            }
        }
    val isEmpty = filteredSuggestions.isEmpty()
    val listState = rememberLazyListState()
    val menuStyle = JewelTheme.menuStyle

    LaunchedEffect(focusedIndex, filteredSuggestions.size) {
        if (focusedIndex >= 0 && filteredSuggestions.isNotEmpty()) {
            val visible = listState.layoutInfo.visibleItemsInfo
            val firstVisible = visible.firstOrNull()?.index
            val lastVisible = visible.lastOrNull()?.index
            if (lastVisible != null && focusedIndex == lastVisible) {
                val nextIndex = (focusedIndex + 1).coerceAtMost(filteredSuggestions.lastIndex)
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
                items(filteredSuggestions.size) { index ->
                    val (ts, parts) = filteredSuggestions[index]
                    SuggestionRow(
                        parts = parts,
                        onClick = { onPickToc(ts) },
                        highlighted = index == focusedIndex,
                        showTabHint = index == focusedIndex,
                    )
                }
            }
        }
    }
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

@Composable
private fun SuggestionRow(
    parts: List<String>,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    showTabHint: Boolean = false,
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
                delay(600)
                hScroll.scrollTo(max)
                delay(600)
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
                        text,
                        color = JewelTheme.globalColors.text.normal,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (showTabHint && hasContent) {
            Spacer(Modifier.width(12.dp))
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, JewelTheme.globalColors.text.info, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(Res.string.tab_hint_select),
                    color = JewelTheme.globalColors.text.info,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    state: TextFieldState,
    selectedFilter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier,
    showToggle: Boolean = true,
    showIcon: Boolean = true,
    onSubmit: () -> Unit = {},
    onTab: (() -> Unit)? = null,
    enabled: Boolean = true,
    // Reference-mode suggestion parameters (ignored in TEXT mode)
    suggestionsVisible: Boolean = false,
    categorySuggestions: List<CategorySuggestion> = emptyList(),
    bookSuggestions: List<BookSuggestion> = emptyList(),
    onPickCategory: (CategorySuggestion) -> Unit = {},
    onPickBook: (BookSuggestion) -> Unit = {},
    // TOC suggestions (for the second field)
    tocSuggestionsVisible: Boolean = false,
    tocSuggestions: List<TocSuggestion> = emptyList(),
    selectedBook: BookModel? = null,
    selectedCategory: Category? = null,
    onPickToc: (TocSuggestion) -> Unit = {},
    onClearBook: (() -> Unit)? = null,
    canClearBookOnBackspace: () -> Boolean = { true },
    // Focus & popup control
    autoFocus: Boolean = true,
    focusRequester: FocusRequester? = null,
    onDismissSuggestions: () -> Unit = {},
    // Placeholder hints override (animated)
    placeholderHints: List<String>? = null,
    // Synchronized placeholder override (renders plain text if provided)
    placeholderText: String? = null,
    // In text-mode left field, allow Enter to submit when a selection exists and no suggestion is focused
    submitOnEnterIfSelection: Boolean = false,
    // In reference-mode first field, pressing Enter should also submit when a book is picked
    submitOnEnterInReference: Boolean = false,
    // Advanced search toggle
    globalExtended: Boolean = false,
    onGlobalExtendedChange: (Boolean) -> Unit = {},
    // Scale applied to Home content; used to keep the popup consistent
    parentScale: Float = 1f,
    // Loading flags for predictive lists
    isBookLoading: Boolean = false,
    isTocLoading: Boolean = false,
) {
    val isReference = selectedFilter == SearchFilter.REFERENCE
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

    val tocHints =
        listOf(
            stringResource(Res.string.reference_toc_hint_1),
            stringResource(Res.string.reference_toc_hint_2),
            stringResource(Res.string.reference_toc_hint_3),
            stringResource(Res.string.reference_toc_hint_4),
            stringResource(Res.string.reference_toc_hint_5),
        )

    val textHints =
        listOf(
            stringResource(Res.string.text_hint_1),
            stringResource(Res.string.text_hint_2),
            stringResource(Res.string.text_hint_3),
            stringResource(Res.string.text_hint_4),
            stringResource(Res.string.text_hint_5),
        )

    val hints =
        placeholderHints ?: when {
            isReference && selectedBook != null -> tocHints
            isReference -> referenceHints
            else -> textHints
        }

    // Restart animation cleanly when switching filter
    var filterVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectedFilter) { filterVersion++ }

    // Disable placeholder animation while user is typing
    val isUserTyping by remember { derivedStateOf { state.text.isNotEmpty() } }

    // Auto-focus the main search field on first composition
    val internalFocusRequester = remember { FocusRequester() }
    val effectiveFocusRequester = focusRequester ?: internalFocusRequester
    LaunchedEffect(Unit) {
        delay(200)
        if (enabled && autoFocus) effectiveFocusRequester.requestFocus()
    }

    // Predictive suggestions management for REFERENCE mode while keeping TextField style
    val hasUserText = state.text.isNotBlank()
    val queryLength = state.text.length
    val minBookPrefixLen = 2
    val minTocPrefixLen = 1
    var focusedIndex by remember { mutableIntStateOf(-1) }
    var popupVisible by remember { mutableStateOf(false) }
    val categoriesCount = categorySuggestions.size
    val totalCatBook = categoriesCount + bookSuggestions.size
    val totalToc = tocSuggestions.size
    val isTocMode = isReference && selectedBook != null
    val showCategorySuggestions = suggestionsVisible && totalCatBook > 0 && !isTocMode
    val showTocSuggestions = tocSuggestionsVisible && totalToc > 0 && isTocMode
    val showBookLoading = isReference && !isTocMode && isBookLoading && hasUserText && queryLength >= minBookPrefixLen
    val showTocLoading = isReference && isTocMode && isTocLoading && hasUserText && queryLength >= minTocPrefixLen
    val showBookEmptyState =
        isReference &&
            !isTocMode &&
            suggestionsVisible &&
            totalCatBook == 0 &&
            hasUserText &&
            queryLength >= minBookPrefixLen &&
            !showBookLoading
    val showTocEmptyState =
        isReference &&
            isTocMode &&
            tocSuggestionsVisible &&
            totalToc == 0 &&
            hasUserText &&
            queryLength >= minTocPrefixLen &&
            !showTocLoading
    LaunchedEffect(
        selectedFilter,
        suggestionsVisible,
        tocSuggestionsVisible,
        categorySuggestions,
        bookSuggestions,
        tocSuggestions,
        isTocMode,
        showBookEmptyState,
        showTocEmptyState,
        showBookLoading,
        showTocLoading,
    ) {
        val shouldOpen =
            when {
                showTocSuggestions -> true
                showCategorySuggestions -> true
                showBookEmptyState -> true
                showTocEmptyState -> true
                showBookLoading -> true
                showTocLoading -> true
                else -> false
            }
        popupVisible = shouldOpen
        focusedIndex = if (shouldOpen && (showTocSuggestions || showCategorySuggestions)) 0 else -1
    }

    var anchor by remember { mutableStateOf<AnchorBounds?>(null) }
    var backspaceStartedEmpty by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        // Local helpers to ensure popup is dismissed when committing a choice
        fun dismissPopup() {
            popupVisible = false
            onDismissSuggestions()
        }

        fun handlePickCategory(cat: CategorySuggestion) {
            onPickCategory(cat)
            dismissPopup()
        }

        fun handlePickBook(book: BookSuggestion) {
            onPickBook(book)
            dismissPopup()
        }

        fun handlePickToc(toc: TocSuggestion) {
            onPickToc(toc)
            dismissPopup()
        }

        fun handleSubmit() {
            onSubmit()
            // If we were showing an overlay, close it after submission
            if (selectedFilter == SearchFilter.REFERENCE) dismissPopup()
        }
        TextField(
            state = state,
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
                        val isRef = isReference
                        when {
                            isRef && ev.key == Key.Backspace && isTocMode -> {
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
                            // Alt toggles between Reference and Text modes
                            (ev.key == Key.AltLeft || ev.key == Key.AltRight) && ev.type == KeyEventType.KeyUp -> {
                                val next =
                                    if (selectedFilter == SearchFilter.REFERENCE) SearchFilter.TEXT else SearchFilter.REFERENCE
                                onFilterChange(next)
                                true
                            }

                            (ev.key == Key.Enter || ev.key == Key.NumPadEnter) && ev.type == KeyEventType.KeyUp -> {
                                if (isRef) {
                                    // Commit current suggestion, don't open
                                    when {
                                        isTocMode && focusedIndex in 0 until totalToc -> {
                                            handlePickToc(tocSuggestions[focusedIndex])
                                            if (submitOnEnterInReference) {
                                                scope.launch {
                                                    withFrameNanos { }
                                                    handleSubmit()
                                                }
                                            }
                                            true
                                        }

                                        !isTocMode && focusedIndex in 0 until totalCatBook -> {
                                            if (focusedIndex < categoriesCount) {
                                                val picked = categorySuggestions[focusedIndex]
                                                handlePickCategory(picked)
                                            } else {
                                                val idx = focusedIndex - categoriesCount
                                                val picked = bookSuggestions.getOrNull(idx)
                                                if (picked != null) {
                                                    handlePickBook(picked)
                                                    if (submitOnEnterInReference) handleSubmit()
                                                }
                                            }
                                            true
                                        }

                                        submitOnEnterIfSelection && (selectedBook != null || selectedCategory != null) -> {
                                            handleSubmit()
                                            true
                                        }

                                        submitOnEnterInReference && selectedBook != null -> {
                                            scope.launch {
                                                withFrameNanos { }
                                                handleSubmit()
                                            }
                                            true
                                        }

                                        else -> true
                                    }
                                } else {
                                    handleSubmit()
                                    true
                                }
                            }

                            isRef && ev.key == Key.DirectionDown && ev.type == KeyEventType.KeyUp -> {
                                val total = if (isTocMode) totalToc else totalCatBook
                                if (total > 0) focusedIndex = (focusedIndex + 1).coerceAtMost(total - 1)
                                true
                            }

                            isRef && ev.key == Key.DirectionUp && ev.type == KeyEventType.KeyUp -> {
                                val total = if (isTocMode) totalToc else totalCatBook
                                if (total > 0) focusedIndex = (focusedIndex - 1).coerceAtLeast(0)
                                true
                            }

                            isRef && ev.key == Key.Escape && ev.type == KeyEventType.KeyUp -> {
                                popupVisible = false
                                onDismissSuggestions()
                                true
                            }

                            // Consume Tab KeyDown in REFERENCE mode when suggestions are visible
                            // to prevent default focus movement before our KeyUp handler runs
                            isRef && ev.key == Key.Tab && ev.type == KeyEventType.KeyDown && popupVisible -> {
                                true
                            }

                            ev.key == Key.Tab && ev.type == KeyEventType.KeyUp -> {
                                if (isRef) {
                                    val handled =
                                        when {
                                            isTocMode && focusedIndex in 0 until totalToc -> {
                                                handlePickToc(tocSuggestions[focusedIndex])
                                                true
                                            }

                                            !isTocMode && focusedIndex in 0 until totalCatBook -> {
                                                if (focusedIndex < categoriesCount) {
                                                    handlePickCategory(categorySuggestions[focusedIndex])
                                                } else {
                                                    val idx = focusedIndex - categoriesCount
                                                    bookSuggestions.getOrNull(idx)?.let { handlePickBook(it) }
                                                }
                                                true
                                            }

                                            else -> false
                                        }
                                    if (handled) {
                                        if (submitOnEnterInReference && isTocMode) {
                                            scope.launch {
                                                withFrameNanos { }
                                                handleSubmit()
                                            }
                                        }
                                        true
                                    } else {
                                        onTab?.invoke()
                                        true
                                    }
                                } else {
                                    onTab?.invoke()
                                    false
                                }
                            }

                            else -> false
                        }
                    }.focusRequester(effectiveFocusRequester),
            enabled = enabled,
            placeholder = {
                if (placeholderText != null) {
                    Text(
                        placeholderText,
                        style = TextStyle(fontSize = 13.sp, color = Color(0xFF9AA0A6)),
                        maxLines = 1,
                    )
                } else {
                    key(filterVersion) {
                        TypewriterPlaceholder(
                            hints = hints,
                            textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF9AA0A6)),
                            typingDelayPerChar = 155L,
                            deletingDelayPerChar = 45L,
                            holdDelayMs = 1600L,
                            preTypePauseMs = 500L,
                            postDeletePauseMs = 450L,
                            speedMultiplier = 1.15f, // a tad slower overall
                            enabled = !isUserTyping,
                        )
                    }
                }
            },
            trailingIcon =
                if (showToggle) {
                    (
                        {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Chip visible seulement en mode TEXT
                                if (selectedFilter == SearchFilter.TEXT) {
                                    CustomToggleableChip(
                                        checked = globalExtended,
                                        onClick = { newChecked ->
                                            // Apply change and immediately return focus to the text field
                                            onGlobalExtendedChange(newChecked)
                                            effectiveFocusRequester.requestFocus()
                                        },
                                        tooltipText = stringResource(Res.string.search_extended_tooltip),
                                        withPadding = false,
                                    )
                                }
                                IntegratedSwitch(
                                    selectedFilter = selectedFilter,
                                    onFilterChange = { filter ->
                                        // Switch mode and refocus the text field so Enter works right away
                                        onFilterChange(filter)
                                        effectiveFocusRequester.requestFocus()
                                    },
                                )
                            }
                        }
                    )
                } else {
                    null
                },
            leadingIcon = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showIcon) {
                        IconButton({ handleSubmit() }) {
                            Icon(
                                key = AllIconsKeys.Actions.Find,
                                contentDescription = stringResource(Res.string.search_icon_description),
                                modifier = Modifier.size(16.dp).pointerHoverIcon(PointerIcon.Hand),
                            )
                        }
                    }
                    if (isReference && selectedBook != null && onClearBook != null) {
                        SelectedBookChip(
                            title = selectedBook.title,
                            onClear = {
                                onClearBook()
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
            isReference &&
                popupVisible &&
                a != null &&
                (
                    showTocSuggestions ||
                        showCategorySuggestions ||
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
                            val padding = (8 * parentScale).roundToInt()
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
                properties = PopupProperties(focusable = false),
            ) {
                val widthDp = with(LocalDensity.current) { a.size.width.toDp() }
                // Scale popup content by the same factor as the Home view to keep
                // typography and spacing consistent with the rest of the UI.
                Box(
                    Modifier.width(widthDp).graphicsLayer(
                        scaleX = parentScale,
                        scaleY = parentScale,
                        // Ensure scaling originates from the top-left so the popup
                        // remains anchored under the TextField.
                        transformOrigin =
                            androidx.compose.ui.graphics
                                .TransformOrigin(0f, 0f),
                    ),
                ) {
                    if (isTocMode && (showTocSuggestions || showTocEmptyState || showTocLoading)) {
                        TocSuggestionsPanel(
                            tocSuggestions = tocSuggestions,
                            onPickToc = ::handlePickToc,
                            focusedIndex = focusedIndex,
                            selectedBook = selectedBook,
                            emptyMessage = if (showTocEmptyState) stringResource(Res.string.autocomplete_no_results) else null,
                            isLoading = showTocLoading,
                            loadingMessage = stringResource(Res.string.autocomplete_loading),
                        )
                    } else if (!isTocMode && (showCategorySuggestions || showBookEmptyState || showBookLoading)) {
                        SuggestionsPanel(
                            categorySuggestions = categorySuggestions,
                            bookSuggestions = bookSuggestions,
                            onPickCategory = ::handlePickCategory,
                            onPickBook = ::handlePickBook,
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
@Composable
private fun IntegratedSwitch(
    selectedFilter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(20.dp))
                .background(JewelTheme.globalColors.panelBackground)
                .border(
                    width = 1.dp,
                    color = JewelTheme.globalColors.borders.disabled,
                    shape = RoundedCornerShape(20.dp),
                ).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        SearchFilter.entries.forEach { filter ->
            Tooltip(
                tooltip = {
                    Text(
                        text =
                            when (filter) {
                                SearchFilter.REFERENCE -> stringResource(Res.string.search_mode_reference_explicit)
                                SearchFilter.TEXT -> stringResource(Res.string.search_mode_text_explicit)
                            },
                        fontSize = 13.sp,
                    )
                },
            ) {
                FilterButton(
                    text =
                        when (filter) {
                            SearchFilter.REFERENCE -> stringResource(Res.string.search_mode_reference)
                            SearchFilter.TEXT -> stringResource(Res.string.search_mode_text)
                        },
                    isSelected = selectedFilter == filter,
                    onClick = { onFilterChange(filter) },
                )
            }
        }
    }
}

@Composable
private fun FilterButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF0E639C) else Color.Transparent,
        animationSpec = tween(200),
        label = "backgroundColor",
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFFCCCCCC),
        animationSpec = tween(200),
        label = "textColor",
    )

    Text(
        text = text,
        modifier =
            modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .clip(RoundedCornerShape(18.dp))
                .background(backgroundColor)
                .clickable(indication = null, interactionSource = MutableInteractionSource()) { onClick() }
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .defaultMinSize(minWidth = 45.dp),
        color = textColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun SearchLevelCard(
    data: SearchFilterCard,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val backgroundColor = if (selected) Color(0xFF0E639C) else Color.Transparent

    val borderColor =
        if (selected) JewelTheme.globalColors.borders.focused else JewelTheme.globalColors.borders.disabled

    Box(
        modifier =
            modifier
                .width(96.dp)
                .height(110.dp)
                .clip(shape)
                .background(backgroundColor)
                .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = shape)
                .clickable(onClick = onClick)
                .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val contentColor = if (selected) Color.White else JewelTheme.contentColor
            Icon(
                data.icons,
                contentDescription = stringResource(data.label),
                modifier = Modifier.size(40.dp),
                tint = contentColor,
            )
            Text(
                stringResource(data.label),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = contentColor,
            )
            Text(
                stringResource(data.desc),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = contentColor,
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun HomeViewPreview() {
    PreviewContainer {
        // Minimal stub state for preview; SearchHomeViewModel is not used here.
        val stubSearchUi = SearchHomeUiState()
        val stubCallbacks =
            HomeSearchCallbacks(
                onReferenceQueryChanged = {},
                onTocQueryChanged = {},
                onFilterChange = {},
                onGlobalExtendedChange = {},
                onSubmitTextSearch = {},
                onOpenReference = {},
                onPickCategory = {},
                onPickBook = {},
                onPickToc = {},
            )
        HomeView(
            onEvent = {},
            searchUi = stubSearchUi,
            searchCallbacks = stubCallbacks,
            homeCelestialWidgetsState = HomeCelestialWidgetsState.preview,
            modifier = Modifier,
        )
    }
}
