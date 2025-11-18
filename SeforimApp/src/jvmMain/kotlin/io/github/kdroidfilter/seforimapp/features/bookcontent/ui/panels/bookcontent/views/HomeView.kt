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
import androidx.compose.ui.zIndex
import io.github.kdroidfilter.seforimapp.catalog.PrecomputedCatalog
import io.github.kdroidfilter.seforimapp.core.presentation.components.CatalogDropdown
import io.github.kdroidfilter.seforimapp.core.presentation.components.CustomToggleableChip
import io.github.kdroidfilter.seforimapp.core.presentation.theme.AppColors
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
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
import org.jetbrains.skiko.Cursor
import seforimapp.seforimapp.generated.resources.*
import kotlin.math.roundToInt
import io.github.kdroidfilter.seforimlibrary.core.models.Book as BookModel

// Suggestion models for the scope picker
private data class CategorySuggestion(val category: Category, val path: List<String>)
private data class BookSuggestion(val book: BookModel, val path: List<String>)
private data class TocSuggestion(val toc: TocEntry, val path: List<String>)
private data class AnchorBounds(val windowOffset: IntOffset, val size: IntSize)

data class SearchFilterCard(
    val icons: ImageVector,
    val label: StringResource,
    val desc: StringResource,
    val explanation: StringResource
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
    val onLevelIndexChange: (Int) -> Unit,
    val onGlobalExtendedChange: (Boolean) -> Unit,
    val onSubmitTextSearch: (String) -> Unit,
    val onOpenReference: () -> Unit,
    val onPickCategory: (Category) -> Unit,
    val onPickBook: (BookModel) -> Unit,
    val onPickToc: (TocEntry) -> Unit
)

@OptIn(ExperimentalJewelApi::class, ExperimentalLayoutApi::class)
@Composable
/**
 * Home screen for the Book Content feature.
 *
 * Renders the welcome header, the main search bar with a mode toggle (Text vs Reference),
 * and the Category/Book/TOC scope picker. State is sourced from the SearchHomeViewModel
 * through the Metro DI graph and kept outside of the LazyColumn to avoid losing focus or
 * field contents during recomposition.
 */
fun HomeView(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    searchUi: SearchHomeUiState,
    searchCallbacks: HomeSearchCallbacks,
    modifier: Modifier = Modifier
) {
    // Global zoom level from AppSettings; used to scale Home view uniformly
    val rawTextSize by AppSettings.textSizeFlow.collectAsState()
    // Apply a gentler zoom curve on the Home screen only: keep default size identical,
    // but soften +/- zoom steps so they feel less strong here than globally.
    val homeScale = remember(rawTextSize) {
        val ratio = rawTextSize / AppSettings.DEFAULT_TEXT_SIZE
        val softenFactor = 0.3f
        1f + (ratio - 1f) * softenFactor
    }
    Box(modifier = Modifier.fillMaxSize().zIndex(1f).padding(16.dp), contentAlignment = Alignment.TopStart) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val buttonWidth = 130.dp
            val spacing = 8.dp
            val totalButtons = 7
            // Compute how many catalog buttons we can show fully without overflow.
            val maxVisible = run {
                var count = totalButtons
                while (count > 1) {
                    val required = buttonWidth * count + spacing * (count - 1)
                    if (required <= maxWidth) break
                    count--
                }
                count
            }

            var rendered = 0

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
            ) {
                if (rendered < maxVisible) {
                    CatalogDropdown(
                        spec = PrecomputedCatalog.Dropdowns.TANAKH,
                        onEvent = onEvent,
                        modifier = Modifier.widthIn(max = buttonWidth),
                        popupWidthMultiplier = 1.50f
                    )
                    rendered++
                }
                if (rendered < maxVisible) {
                    CatalogDropdown(
                        spec = PrecomputedCatalog.Dropdowns.MISHNA,
                        onEvent = onEvent,
                        modifier = Modifier.widthIn(max = buttonWidth)
                    )
                    rendered++
                }
                if (rendered < maxVisible) {
                    CatalogDropdown(
                        spec = PrecomputedCatalog.Dropdowns.BAVLI,
                        onEvent = onEvent,
                        modifier = Modifier.widthIn(max = buttonWidth),
                        popupWidthMultiplier = 1.1f
                    )
                    rendered++
                }
                if (rendered < maxVisible) {
                    CatalogDropdown(
                        spec = PrecomputedCatalog.Dropdowns.YERUSHALMI,
                        onEvent = onEvent,
                        modifier = Modifier.widthIn(max = buttonWidth),
                        popupWidthMultiplier = 1.1f
                    )
                    rendered++
                }
                if (rendered < maxVisible) {
                    CatalogDropdown(
                        spec = PrecomputedCatalog.Dropdowns.MISHNE_TORAH,
                        onEvent = onEvent,
                        modifier = Modifier.widthIn(max = buttonWidth),
                        popupWidthMultiplier = 1.5f
                    )
                    rendered++
                }
                if (rendered < maxVisible) {
                    CatalogDropdown(
                        spec = PrecomputedCatalog.Dropdowns.TUR_QUICK_LINKS,
                        onEvent = onEvent,
                        modifier = Modifier.widthIn(max = buttonWidth),
                        maxPopupHeight = 130.dp
                    )
                    rendered++
                }
                if (rendered < maxVisible) {
                    CatalogDropdown(
                        spec = PrecomputedCatalog.Dropdowns.SHULCHAN_ARUCH,
                        onEvent = onEvent,
                        modifier = Modifier.widthIn(max = buttonWidth),
                        maxPopupHeight = 130.dp,
                        popupWidthMultiplier = 1.1f
                    )
                }
            }
        }
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
            contentAlignment = Alignment.Center
        ) {
            // Keep a fixed logical width for the Home content (600.dp) and clamp the
            // scale if the window is too narrow so that the scaled content never
            // overflows horizontally. Padding is derived from the effective scale so
            // it stops growing once the layout itself is clamped.
            val baseWidth = 600.dp
            val maxScaleForWidth = (maxWidth.value / baseWidth.value).coerceAtLeast(0f)
            val clampedScale = homeScale
                .coerceAtMost(maxScaleForWidth)
                .coerceAtLeast(0f)
            val paddingScale = (1f + (clampedScale - 1f) * 5f)
                .coerceAtLeast(0.2f)

            Box(
                modifier = modifier
                    .padding(top = 56.dp * paddingScale)
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Keep state outside LazyColumn so it persists across item recompositions
                val scope = rememberCoroutineScope()
                val searchState = remember { TextFieldState() }
                val referenceSearchState = remember { TextFieldState() }
                val tocSearchState = remember { TextFieldState() }
                var skipNextReferenceQuery by remember { mutableStateOf(false) }
                var skipNextTocQuery by remember { mutableStateOf(false) }
                // Shared focus requester so Tab from the first field can move focus to the TOC field
                val tocFieldFocusRequester = remember { FocusRequester() }
                // Shared focus requester for the MAIN search bar so other UI (e.g., level changes)
                // can reliably return focus to it, allowing immediate Enter to submit.
                val mainSearchFocusRequester = remember { FocusRequester() }
                var scopeExpanded by remember { mutableStateOf(false) }
                // Forward reference input changes to the ViewModel (VM handles debouncing and suggestions)
                LaunchedEffect(Unit) {
                    snapshotFlow { referenceSearchState.text.toString() }
                        .collect { qRaw ->
                            if (skipNextReferenceQuery) {
                                skipNextReferenceQuery = false
                            } else {
                                searchCallbacks.onReferenceQueryChanged(qRaw)
                            }
                        }
                }
                // Forward toc input changes to the ViewModel (ignored until a book is selected)
                LaunchedEffect(Unit) {
                    snapshotFlow { tocSearchState.text.toString() }
                        .collect { qRaw ->
                            if (skipNextTocQuery) {
                                skipNextTocQuery = false
                            } else {
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
            val bookOnlyHintsGlobal = listOf(
                stringResource(Res.string.reference_book_hint_1),
                stringResource(Res.string.reference_book_hint_2),
                stringResource(Res.string.reference_book_hint_3),
                stringResource(Res.string.reference_book_hint_4),
                stringResource(Res.string.reference_book_hint_5)
            )

            // Main search field focus handled inside SearchBar via autoFocus

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                // Keep aspect ratio by applying uniform scale to the whole Home content,
                // while keeping it within the available width.
                modifier = Modifier
                    .width(baseWidth)
                    .graphicsLayer(scaleX = clampedScale, scaleY = clampedScale)
            ) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            WelcomeUser(username = searchUi.userDisplayName)
                        }
                    }
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            LogoImage()
                        }
                    }
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            // In REFERENCE mode, repurpose the first TextField as the predictive
                            // Book picker (with Category/Book suggestions). Enter should NOT open.
                            val isReferenceMode = searchUi.selectedFilter == SearchFilter.REFERENCE
                            // When switching to REFERENCE mode, focus the first (top) text field
                            LaunchedEffect(searchUi.selectedFilter) {
                                // When switching modes, always focus the top text field
                                if (searchUi.selectedFilter == SearchFilter.REFERENCE ||
                                    searchUi.selectedFilter == SearchFilter.TEXT) {
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
                            val mappedCategorySuggestionsForBar = searchUi.categorySuggestions.map { cs ->
                                CategorySuggestion(cs.category, cs.path)
                            }
                            val mappedBookSuggestionsForBar = searchUi.bookSuggestions.map { bs ->
                                BookSuggestion(bs.book, bs.path)
                            }
                            val breadcrumbSeparatorTop = stringResource(Res.string.breadcrumb_separator)
                            SearchBar(
                                state = if (isReferenceMode) referenceSearchState else searchState,
                                selectedFilter = searchUi.selectedFilter,
                                onFilterChange = { searchCallbacks.onFilterChange(it) },
                                onSubmit = if (isReferenceMode) { { openReference() } } else { { launchSearch() } },
                                onTab = {
                                    if (isReferenceMode) {
                                        // After choosing the first suggestion, focus the TOC field
                                        scope.launch {
                                            // small delay to ensure state (selectedBook) is updated and field enabled
                                            delay(120)
                                            tocFieldFocusRequester.requestFocus()
                                        }
                                    } else {
                                        // Text mode: expand the scope section as before
                                        scopeExpanded = true
                                    }
                                },
                                modifier = Modifier,
                                showIcon = !isReferenceMode,
                                focusRequester = mainSearchFocusRequester,
                                // Suggestions: in REFERENCE mode show only books; in TEXT mode none here
                                suggestionsVisible = if (isReferenceMode) searchUi.suggestionsVisible else false,
                                categorySuggestions = emptyList(),
                                bookSuggestions = if (isReferenceMode) mappedBookSuggestionsForBar else emptyList(),
                                placeholderHints = if (isReferenceMode) bookOnlyHintsGlobal else null,
                                placeholderText = null,
                                submitOnEnterInReference = isReferenceMode,
                                globalExtended = searchUi.globalExtended,
                                onGlobalExtendedChange = { searchCallbacks.onGlobalExtendedChange(it) },
                                parentScale = homeScale,
                                onPickCategory = { picked ->
                                    // Update VM and reflect breadcrumb in the bar input
                                    searchCallbacks.onPickCategory(picked.category)
                                    val full = dedupAdjacent(picked.path).joinToString(breadcrumbSeparatorTop)
                                    skipNextReferenceQuery = true
                                    referenceSearchState.edit { replace(0, length, full) }
                                },
                                onPickBook = { picked ->
                                    // Update VM and reflect breadcrumb in the bar input
                                    searchCallbacks.onPickBook(picked.book)
                                    val full = dedupAdjacent(picked.path).joinToString(breadcrumbSeparatorTop)
                                    skipNextReferenceQuery = true
                                    referenceSearchState.edit { replace(0, length, full) }
                                }
                            )
                        }
                    }
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(
                                modifier = Modifier.heightIn(min = 250.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // DRY: Compute shared mappings + handlers once for both modes.
                                // The UI below uses these in a single ReferenceByCategorySection call
                                // and then renders a mode-specific footer (levels slider or open button).
                                val breadcrumbSeparator = stringResource(Res.string.breadcrumb_separator)
                                val mappedCategorySuggestions = searchUi.categorySuggestions.map { cs ->
                                    CategorySuggestion(cs.category, cs.path)
                                }
                                val mappedBookSuggestions = searchUi.bookSuggestions.map { bs ->
                                    BookSuggestion(bs.book, bs.path)
                                }
                                val mappedTocSuggestions = searchUi.tocSuggestions.map { ts ->
                                    TocSuggestion(ts.toc, ts.path)
                                }

                                val isReferenceMode = searchUi.selectedFilter == SearchFilter.REFERENCE
                                // Pick submit action based on the active search mode.
                                val onSubmitAction: () -> Unit = if (isReferenceMode) {
                                    { openReference() }
                                } else {
                                    { launchSearch() }
                                }
                                // In reference mode, selecting a suggestion should commit immediately.
                                val afterPickSubmit = isReferenceMode

                                ReferenceByCategorySection(
                                    modifier,
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
                                    onSubmit = onSubmitAction,
                                    submitAfterPick = afterPickSubmit,
                                    submitOnEnterIfSelection = !isReferenceMode,
                                    // Hide the left Category/Book field in REFERENCE mode
                                    showCategoryBookField = !isReferenceMode,
                                    tocFieldFocusRequester = tocFieldFocusRequester,
                                    tocPreviewHints = searchUi.tocPreviewHints,
                                    showHeader = !isReferenceMode,
                                    onPickCategory = { picked ->
                                        searchCallbacks.onPickCategory(picked.category)
                                        val full = dedupAdjacent(picked.path).joinToString(breadcrumbSeparator)
                                        skipNextReferenceQuery = true
                                        referenceSearchState.edit { replace(0, length, full) }
                                    },
                                    onPickBook = { picked ->
                                        searchCallbacks.onPickBook(picked.book)
                                        val full = dedupAdjacent(picked.path).joinToString(breadcrumbSeparator)
                                        skipNextReferenceQuery = true
                                        referenceSearchState.edit { replace(0, length, full) }
                                    },
                                    onPickToc = { picked ->
                                        searchCallbacks.onPickToc(picked.toc)
                                        val dedup = dedupAdjacent(picked.path)
                                        val stripped = stripBookPrefixFromTocPath(searchUi.selectedScopeBook, dedup)
                                        val display = stripped.joinToString(breadcrumbSeparator)
                                        skipNextTocQuery = true
                                        tocSearchState.edit { replace(0, length, display) }
                                    },
                                    parentScale = homeScale
                                )

                                if (!isReferenceMode) {
                                    SearchLevelsPanel(
                                        selectedIndex = searchUi.selectedLevelIndex,
                                        onSelectedIndexChange = {
                                            searchCallbacks.onLevelIndexChange(it)
                                            // After changing search level, return focus to the main field
                                            // so the user can immediately press Enter to search.
                                            mainSearchFocusRequester.requestFocus()
                                        }
                                    )
                                } else {
                                    val canOpen = searchUi.selectedScopeBook != null || searchUi.selectedScopeToc != null
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    ) {
                                        DefaultButton(
                                            onClick = { openReference() },
                                            enabled = canOpen,
                                        ) {
                                            Text(stringResource(Res.string.open_book))
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
}

@Composable
private fun WelcomeUser(username: String) {
    Text(
        stringResource(Res.string.home_welcome_user, username),
        textAlign = TextAlign.Center,
        fontSize = 36.sp
    )
}

/** App logo shown on the Home screen. */
@Composable
private fun LogoImage(modifier: Modifier = Modifier) {
    Image(
        painterResource(Res.drawable.zayit_transparent),
        contentDescription = null,
        modifier = modifier.size(256.dp)
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
    modifier: Modifier = Modifier,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit
) {
    val filterCards: List<SearchFilterCard> = listOf(
        SearchFilterCard(
            Target,
            Res.string.search_level_1_value,
            Res.string.search_level_1_description,
            Res.string.search_level_1_explanation
        ),
        SearchFilterCard(
            Link,
            Res.string.search_level_2_value,
            Res.string.search_level_2_description,
            Res.string.search_level_2_explanation
        ),
        SearchFilterCard(
            Format_letter_spacing,
            Res.string.search_level_3_value,
            Res.string.search_level_3_description,
            Res.string.search_level_3_explanation
        ),
        SearchFilterCard(
            Article,
            Res.string.search_level_4_value,
            Res.string.search_level_4_description,
            Res.string.search_level_4_explanation
        ),
        SearchFilterCard(
            Book,
            Res.string.search_level_5_value,
            Res.string.search_level_5_description,
            Res.string.search_level_5_explanation
        )
    )

    // Synchronize cards with slider position
    var sliderPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) { sliderPosition = selectedIndex.toFloat() }
    val maxIndex = (filterCards.size - 1).coerceAtLeast(0)
    val coercedSelected = sliderPosition.coerceIn(0f, maxIndex.toFloat()).toInt()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        filterCards.forEachIndexed { index, filterCard ->
            SearchLevelCard(
                data = filterCard,
                selected = index == coercedSelected,
                onClick = {
                    sliderPosition = index.toFloat()
                    onSelectedIndexChange(index)
                }
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
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
    )
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
    showCategoryBookField: Boolean = true,
    tocPreviewHints: List<String> = emptyList(),
    showHeader: Boolean = true,
    tocFieldFocusRequester: FocusRequester? = null,
    onPickCategory: (CategorySuggestion) -> Unit = {},
    onPickBook: (BookSuggestion) -> Unit = {},
    onPickToc: (TocSuggestion) -> Unit = {},
    // Scale applied to Home content; forwarded to inner SearchBars
    parentScale: Float = 1f,
) {
    val interactionSource = remember { MutableInteractionSource() }

    if (showHeader) {
        GroupHeader(
            text = stringResource(Res.string.search_by_category_or_book),
            modifier =
                modifier
                    .clickable(indication = null, interactionSource = interactionSource) {
                        onExpandedChange(!isExpanded)
                    }
                    .hoverable(interactionSource)
                    .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))),
            startComponent = {
                if (isExpanded) {
                    Icon(AllIconsKeys.General.ChevronDown, stringResource(Res.string.chevron_icon_description))
                } else {
                    Icon(AllIconsKeys.General.ChevronLeft, stringResource(Res.string.chevron_icon_description))
                }
            },
        )
        if (!isExpanded) return
    }

    val refState = state ?: remember { TextFieldState() }
    val tocTfState = tocState ?: remember { TextFieldState() }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showCategoryBookField) {
                // Left: Category/Book SearchBar with predictive suggestions (same look as ref mode)
                Column(Modifier.weight(1f)) {
                    val bookHints = listOf(
                        stringResource(Res.string.reference_book_hint_1),
                        stringResource(Res.string.reference_book_hint_2),
                        stringResource(Res.string.reference_book_hint_3),
                        stringResource(Res.string.reference_book_hint_4),
                        stringResource(Res.string.reference_book_hint_5)
                    )
                    SearchBar(
                        state = refState,
                        selectedFilter = SearchFilter.REFERENCE, // force reference behavior for suggestions
                        onFilterChange = {},
                        showToggle = false,
                        showIcon = false,
                        enabled = true,
                        suggestionsVisible = suggestionsVisible,
                        categorySuggestions = categorySuggestions,
                        bookSuggestions = bookSuggestions,
                        selectedBook = selectedBook,
                        selectedCategory = selectedCategory,
                        placeholderHints = bookHints,
                        onPickCategory = { picked ->
                            onPickCategory(picked)
                            if (submitAfterPick) onSubmit()
                        },
                        onPickBook = { picked ->
                            onPickBook(picked)
                            if (submitAfterPick) onSubmit()
                        },
                        onSubmit = onSubmit,
                        submitOnEnterIfSelection = submitOnEnterIfSelection,
                        autoFocus = false,
                        parentScale = parentScale
                    )
                }
            }

            // Right: TOC field — always visible but disabled until a book is selected
            Column(Modifier.weight(1f)) {
                val defaultTocFocusRequester = remember { FocusRequester() }
                val tocFocusRequester = tocFieldFocusRequester ?: defaultTocFocusRequester
                val tocOnlyHints = listOf(
                    stringResource(Res.string.reference_toc_hint_1),
                    stringResource(Res.string.reference_toc_hint_2),
                    stringResource(Res.string.reference_toc_hint_3),
                    stringResource(Res.string.reference_toc_hint_4),
                    stringResource(Res.string.reference_toc_hint_5)
                )
                SearchBar(
                    state = tocTfState,
                    selectedFilter = SearchFilter.REFERENCE,
                    onFilterChange = {},
                    showToggle = false,
                    showIcon = false,
                    enabled = selectedBook != null,
                    suggestionsVisible = false,
                    categorySuggestions = emptyList(),
                    bookSuggestions = emptyList(),
                    // TOC suggestions overlay
                    tocSuggestionsVisible = tocSuggestionsVisible && selectedBook != null,
                    tocSuggestions = tocSuggestions,
                    selectedBook = selectedBook,
                    onPickToc = { picked ->
                        onPickToc(picked)
                        if (submitAfterPick) onSubmit()
                    },
                    autoFocus = false,
                    focusRequester = tocFocusRequester,
                    // Remove animated placeholder for second field
                    placeholderHints = null,
                    placeholderText = "",
                    parentScale = parentScale
                )
                // Focus the second field when a book is picked only in non-reference mode
                LaunchedEffect(selectedBook?.id, showCategoryBookField) {
                    if (selectedBook != null) {
                        // Clear previous TOC query
                        tocTfState.edit { replace(0, length, "") }
                        if (showCategoryBookField) {
                            // In text-mode flow, auto-advance focus to TOC field
                            tocFocusRequester.requestFocus()
                        }
                    }
                }
            }
        }
    }
}

@Composable
/**
 * Renders the suggestion list for categories and books, keeping the currently
 * focused row in view as the user navigates with the keyboard.
 */
private fun SuggestionsPanel(
    categorySuggestions: List<CategorySuggestion>,
    bookSuggestions: List<BookSuggestion>,
    onPickCategory: (CategorySuggestion) -> Unit,
    onPickBook: (BookSuggestion) -> Unit,
    focusedIndex: Int = -1
) {
    val listState = rememberLazyListState()
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
                }
                // Scroll up when at first visible
                else if (firstVisible != null && focusedIndex == firstVisible) {
                    val prevIndex = (focusedIndex - 1).coerceAtLeast(0)
                    if (prevIndex != focusedIndex) listState.scrollToItem(prevIndex)
                }
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
            .background(JewelTheme.globalColors.panelBackground)
            .heightIn(max = 320.dp)
            .padding(8.dp)
    ) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categorySuggestions.size) { idx ->
                val cat = categorySuggestions[idx]
                val dedupPath = dedupAdjacent(cat.path)
                SuggestionRow(
                    parts = dedupPath,
                    onClick = { onPickCategory(cat) },
                    highlighted = idx == focusedIndex
                )
            }
            items(bookSuggestions.size) { i ->
                val rowIndex = categorySuggestions.size + i
                val book = bookSuggestions[i]
                val dedupPath = dedupAdjacent(book.path)
                SuggestionRow(
                    parts = dedupPath,
                    onClick = { onPickBook(book) },
                    highlighted = rowIndex == focusedIndex
                )
            }
        }
    }
}

@Composable
/**
 * Renders the TOC suggestion list for the currently selected book, stripping the
 * duplicated book prefix from breadcrumb paths for compact display.
 */
private fun TocSuggestionsPanel(
    tocSuggestions: List<TocSuggestion>,
    onPickToc: (TocSuggestion) -> Unit,
    focusedIndex: Int = -1,
    selectedBook: BookModel? = null
) {
    val listState = rememberLazyListState()
    LaunchedEffect(focusedIndex, tocSuggestions.size) {
        if (focusedIndex >= 0 && tocSuggestions.isNotEmpty()) {
            val visible = listState.layoutInfo.visibleItemsInfo
            val firstVisible = visible.firstOrNull()?.index
            val lastVisible = visible.lastOrNull()?.index
            if (lastVisible != null && focusedIndex == lastVisible) {
                val nextIndex = (focusedIndex + 1).coerceAtMost(tocSuggestions.lastIndex)
                if (nextIndex != focusedIndex) listState.scrollToItem(nextIndex)
            } else if (firstVisible != null && focusedIndex == firstVisible) {
                val prevIndex = (focusedIndex - 1).coerceAtLeast(0)
                if (prevIndex != focusedIndex) listState.scrollToItem(prevIndex)
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
            .background(JewelTheme.globalColors.panelBackground)
            .heightIn(max = 320.dp)
            .padding(8.dp)
    ) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(tocSuggestions.size) { index ->
                val ts = tocSuggestions[index]
                val dedupPath = dedupAdjacent(ts.path)
                val parts = stripBookPrefixFromTocPath(selectedBook, dedupPath)
                SuggestionRow(
                    parts = parts,
                    onClick = { onPickToc(ts) },
                    highlighted = index == focusedIndex
                )
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
    fun extends(prev: String, next: String): Boolean {
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
private fun stripBookPrefixFromTocPath(selectedBook: BookModel?, parts: List<String>): List<String> {
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
private fun SuggestionRow(parts: List<String>, onClick: () -> Unit, highlighted: Boolean = false) {
    val hScroll = rememberScrollState(0)
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val active = highlighted || isHovered
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) AppColors.HOVER_HIGHLIGHT else Color.Transparent)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(hoverSource)
            .horizontalScroll(hScroll)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        parts.forEachIndexed { index, text ->
            if (index > 0) Text(
                stringResource(Res.string.breadcrumb_separator),
                color = JewelTheme.globalColors.text.disabled,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
            Text(
                text,
                color = JewelTheme.globalColors.text.normal,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
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
) {
    // Hints from string resources
    val referenceHints = listOf(
        stringResource(Res.string.reference_hint_1),
        stringResource(Res.string.reference_hint_2),
        stringResource(Res.string.reference_hint_3),
        stringResource(Res.string.reference_hint_4),
        stringResource(Res.string.reference_hint_5)
    )

    val textHints = listOf(
        stringResource(Res.string.text_hint_1),
        stringResource(Res.string.text_hint_2),
        stringResource(Res.string.text_hint_3),
        stringResource(Res.string.text_hint_4),
        stringResource(Res.string.text_hint_5)
    )

    val hints = placeholderHints ?: if (selectedFilter == SearchFilter.REFERENCE) referenceHints else textHints

    // Restart animation cleanly when switching filter
    var filterVersion by remember { mutableStateOf(0) }
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
    var focusedIndex by remember { mutableIntStateOf(-1) }
    var popupVisible by remember { mutableStateOf(false) }
    val categoriesCount = categorySuggestions.size
    val totalCatBook = categoriesCount + bookSuggestions.size
    val totalToc = tocSuggestions.size
    val usingToc = totalToc > 0 || tocSuggestionsVisible
    LaunchedEffect(
        selectedFilter,
        suggestionsVisible,
        tocSuggestionsVisible,
        categorySuggestions,
        bookSuggestions,
        tocSuggestions
    ) {
        val shouldOpen =
            selectedFilter == SearchFilter.REFERENCE &&
            ((suggestionsVisible && totalCatBook > 0) || (tocSuggestionsVisible && totalToc > 0))
        popupVisible = shouldOpen
        focusedIndex = if (shouldOpen) 0 else -1
    }

    var anchor by remember { mutableStateOf<AnchorBounds?>(null) }
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
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    anchor = AnchorBounds(
                        windowOffset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt()),
                        size = IntSize(coords.size.width, coords.size.height)
                    )
                }
                .onPreviewKeyEvent { ev ->
                    val isRef = selectedFilter == SearchFilter.REFERENCE
                    when {
                        // Alt toggles between Reference and Text modes
                        (ev.key == Key.AltLeft || ev.key == Key.AltRight) && ev.type == KeyEventType.KeyUp -> {
                            val next = if (selectedFilter == SearchFilter.REFERENCE) SearchFilter.TEXT else SearchFilter.REFERENCE
                            onFilterChange(next)
                            true
                        }
                        (ev.key == Key.Enter || ev.key == Key.NumPadEnter) && ev.type == KeyEventType.KeyUp -> {
                            if (isRef) {
                                // Commit current suggestion, don't open
                                if (usingToc) {
                                    if (focusedIndex in 0 until totalToc) {
                                        handlePickToc(tocSuggestions[focusedIndex])
                                    }
                                } else {
                                    if (focusedIndex in 0 until totalCatBook) {
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
                                    } else {
                                        // No suggestion focused/visible: if a selection exists, allow submit (text-mode behavior)
                                        if (submitOnEnterIfSelection && (selectedBook != null || selectedCategory != null)) {
                                            handleSubmit()
                                        }
                                    }
                                }
                                true
                            } else {
                                handleSubmit(); true
                            }
                        }
                        isRef && ev.key == Key.DirectionDown && ev.type == KeyEventType.KeyUp -> {
                            val total = if (usingToc) totalToc else totalCatBook
                            if (total > 0) focusedIndex = (focusedIndex + 1).coerceAtMost(total - 1)
                            true
                        }
                        isRef && ev.key == Key.DirectionUp && ev.type == KeyEventType.KeyUp -> {
                            val total = if (usingToc) totalToc else totalCatBook
                            if (total > 0) focusedIndex = (focusedIndex - 1).coerceAtLeast(0)
                            true
                        }
                        isRef && ev.key == Key.Escape && ev.type == KeyEventType.KeyUp -> {
                            popupVisible = false
                            onDismissSuggestions()
                            true
                        }
                        ev.key == Key.Tab && ev.type == KeyEventType.KeyUp -> {
                            // In reference mode and when a Tab handler is provided (top bar),
                            // select the first book suggestion and then focus the TOC field.
                            if (isRef && onTab != null) {
                                val firstBook = bookSuggestions.firstOrNull()
                                if (firstBook != null) {
                                    handlePickBook(firstBook)
                                }
                                onTab()
                                true
                            } else {
                                // Otherwise, let default focus traversal happen
                                onTab?.invoke(); false
                            }
                        }
                        else -> false
                    }
                }
                .focusRequester(effectiveFocusRequester),
            enabled = enabled,
            placeholder = {
                if (placeholderText != null) {
                    Text(
                        placeholderText,
                        style = TextStyle(fontSize = 13.sp, color = Color(0xFF9AA0A6)),
                        maxLines = 1
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
                            enabled = !isUserTyping
                        )
                    }
                }
            },
            trailingIcon = if (showToggle) ({
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                            withPadding = false
                        )
                    }
                    IntegratedSwitch(
                        selectedFilter = selectedFilter,
                        onFilterChange = { filter ->
                            // Switch mode and refocus the text field so Enter works right away
                            onFilterChange(filter)
                            effectiveFocusRequester.requestFocus()
                        }
                    )
                }
            }) else null,
            leadingIcon = {
                if (!showIcon) return@TextField
                IconButton({ handleSubmit() }) {
                    Icon(
                        key = AllIconsKeys.Actions.Find,
                        contentDescription = stringResource(Res.string.search_icon_description),
                        modifier = Modifier.size(16.dp).pointerHoverIcon(PointerIcon.Hand),
                    )
                }
            },
            textStyle = TextStyle(fontSize = 13.sp),
        )

        // Overlay suggestions anchored under the TextField
        val a = anchor
        val showOverlay = selectedFilter == SearchFilter.REFERENCE && popupVisible && a != null &&
            ((usingToc && totalToc > 0 && (selectedBook != null)) || (!usingToc && totalCatBook > 0))
        if (showOverlay) {
            val provider = remember(a) {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        // Base position under the TextField. We do not apply the scale
                        // here because the popup content itself is scaled with a top-left
                        // origin, which keeps alignment with the unscaled anchor offset.
                        var x = a.windowOffset.x
                        var y = a.windowOffset.y + a.size.height + 4
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
                properties = PopupProperties(focusable = false)
            ) {
                val widthDp = with(LocalDensity.current) { a.size.width.toDp() }
                // Scale popup content by the same factor as the Home view to keep
                // typography and spacing consistent with the rest of the UI.
                Box(
                    Modifier
                        .width(widthDp)
                        .graphicsLayer(
                            scaleX = parentScale,
                            scaleY = parentScale,
                            // Ensure scaling originates from the top-left so the popup
                            // remains anchored under the TextField.
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        )
                ) {
                    if (usingToc) {
                        TocSuggestionsPanel(
                            tocSuggestions = tocSuggestions,
                            onPickToc = ::handlePickToc,
                            focusedIndex = focusedIndex,
                    selectedBook = selectedBook
                )
                    } else {
                        SuggestionsPanel(
                            categorySuggestions = categorySuggestions,
                            bookSuggestions = bookSuggestions,
                            onPickCategory = ::handlePickCategory,
                            onPickBook = ::handlePickBook,
                            focusedIndex = focusedIndex
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IntegratedSwitch(
    selectedFilter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(JewelTheme.globalColors.panelBackground)
            .border(
                width = 1.dp,
                color = JewelTheme.globalColors.borders.disabled,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        SearchFilter.entries.forEach { filter ->
            Tooltip(
                tooltip = {
                    Text(
                        text = when (filter) {
                            SearchFilter.REFERENCE -> stringResource(Res.string.search_mode_reference_explicit)
                            SearchFilter.TEXT -> stringResource(Res.string.search_mode_text_explicit)
                        },
                        fontSize = 13.sp
                    )
                }
            ) {
                FilterButton(
                    text = when (filter) {
                        SearchFilter.REFERENCE -> stringResource(Res.string.search_mode_reference)
                        SearchFilter.TEXT -> stringResource(Res.string.search_mode_text)
                    },
                    isSelected = selectedFilter == filter,
                    onClick = { onFilterChange(filter) }
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
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF0E639C) else Color.Transparent,
        animationSpec = tween(200),
        label = "backgroundColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFFCCCCCC),
        animationSpec = tween(200),
        label = "textColor"
    )

    Text(
        text = text,
        modifier = modifier
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
        fontFamily = FontFamily.Monospace
    )
}


@Composable
private fun SearchLevelCard(
    data: SearchFilterCard,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val backgroundColor = if (selected) Color(0xFF0E639C) else Color.Transparent

    val borderColor =
        if (selected) JewelTheme.globalColors.borders.focused else JewelTheme.globalColors.borders.disabled

    Box(
        modifier = modifier
            .width(96.dp)
            .height(110.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = shape)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val contentColor = if (selected) Color.White else JewelTheme.contentColor
            Icon(
                data.icons,
                contentDescription = stringResource(data.label),
                modifier = Modifier.size(40.dp),
                tint = contentColor
            )
            Text(
                stringResource(data.label),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = contentColor
            )
            Text(
                stringResource(data.desc),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = contentColor
            )
        }
    }
}


@androidx.compose.desktop.ui.tooling.preview.Preview
@Composable
fun HomeViewPreview() {
    PreviewContainer {
        // Minimal stub state for preview; SearchHomeViewModel is not used here.
        val stubSearchUi = SearchHomeUiState()
        val stubCallbacks = HomeSearchCallbacks(
            onReferenceQueryChanged = {},
            onTocQueryChanged = {},
            onFilterChange = {},
            onLevelIndexChange = {},
            onGlobalExtendedChange = {},
            onSubmitTextSearch = {},
            onOpenReference = {},
            onPickCategory = {},
            onPickBook = {},
            onPickToc = {}
        )
        HomeView(
            uiState = BookContentState(),
            onEvent = {},
            searchUi = stubSearchUi,
            searchCallbacks = stubCallbacks,
            modifier = Modifier
        )
    }
}
