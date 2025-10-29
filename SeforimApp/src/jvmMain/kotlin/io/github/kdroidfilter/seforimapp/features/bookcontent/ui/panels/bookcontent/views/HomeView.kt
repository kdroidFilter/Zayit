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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.search.SearchFilter
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
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
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.comboBoxStyle
import org.jetbrains.skiko.Cursor
import seforimapp.seforimapp.generated.resources.*
import io.github.kdroidfilter.seforimlibrary.core.models.Book as BookModel

// SearchFilter moved to features.search.SearchFilter per architecture guidelines.

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
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    VerticallyScrollableContainer(
        scrollState = listState as ScrollableState,
    ) {
        Box(
            modifier = modifier.padding(16.dp).fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Keep state outside LazyColumn so it persists across item recompositions
            val appGraph = LocalAppGraph.current
            val searchVm = remember { appGraph.searchHomeViewModel }
            val searchUi = searchVm.uiState.collectAsState().value
            val scope = rememberCoroutineScope()
            val searchState = remember { TextFieldState() }
            val referenceSearchState = remember { TextFieldState() }
            val tocSearchState = remember { TextFieldState() }
            var scopeExpanded by remember { mutableStateOf(false) }
            // Forward reference input changes to the ViewModel (VM handles debouncing and suggestions)
            LaunchedEffect(Unit) {
                snapshotFlow { referenceSearchState.text.toString() }
                    .collect { qRaw -> searchVm.onReferenceQueryChanged(qRaw) }
            }
            // Forward toc input changes to the ViewModel (ignored until a book is selected)
            LaunchedEffect(Unit) {
                snapshotFlow { tocSearchState.text.toString() }
                    .collect { qRaw -> searchVm.onTocQueryChanged(qRaw) }
            }
            fun launchSearch() {
                val query = searchState.text.toString().trim()
                if (query.isBlank() || searchUi.selectedFilter != SearchFilter.TEXT) return
                scope.launch { searchVm.submitSearch(query) }
            }
            fun openReference() {
                scope.launch { searchVm.openSelectedReferenceInCurrentTab() }
            }

            // Main search field focus handled inside SearchBar via autoFocus

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.width(600.dp)
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
                        SearchBar(
                            state = searchState,
                            selectedFilter = searchUi.selectedFilter,
                            onFilterChange = { searchVm.onFilterChange(it) },
                            onSubmit = { launchSearch() },
                            onTab = {
                                scopeExpanded = true
                            },
                            modifier = Modifier
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
                                tocSuggestionsVisible = searchUi.tocSuggestionsVisible,
                                tocSuggestions = mappedTocSuggestions,
                                onSubmit = onSubmitAction,
                                submitAfterPick = afterPickSubmit,
                                onPickCategory = { picked ->
                                    searchVm.onPickCategory(picked.category)
                                    val full = dedupAdjacent(picked.path).joinToString(breadcrumbSeparator)
                                    referenceSearchState.edit { replace(0, length, full) }
                                },
                                onPickBook = { picked ->
                                    searchVm.onPickBook(picked.book)
                                    val full = dedupAdjacent(picked.path).joinToString(breadcrumbSeparator)
                                    referenceSearchState.edit { replace(0, length, full) }
                                },
                                onPickToc = { picked ->
                                    searchVm.onPickToc(picked.toc)
                                    val dedup = dedupAdjacent(picked.path)
                                    val stripped = stripBookPrefixFromTocPath(searchUi.selectedScopeBook, dedup)
                                    val display = stripped.joinToString(breadcrumbSeparator)
                                    tocSearchState.edit { replace(0, length, display) }
                                }
                            )

                            if (!isReferenceMode) {
                                SearchLevelsPanel(
                                    selectedIndex = searchUi.selectedLevelIndex,
                                    onSelectedIndexChange = { searchVm.onLevelIndexChange(it) }
                                )
                            } else {
                                val canOpen = searchUi.selectedScopeBook != null || searchUi.selectedScopeToc != null
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    DefaultButton(onClick = { openReference() }, enabled = canOpen) {
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
    tocSuggestionsVisible: Boolean = false,
    tocSuggestions: List<TocSuggestion> = emptyList(),
    onSubmit: () -> Unit = {},
    submitAfterPick: Boolean = false,
    onPickCategory: (CategorySuggestion) -> Unit = {},
    onPickBook: (BookSuggestion) -> Unit = {},
    onPickToc: (TocSuggestion) -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }

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

    val refState = state ?: remember { TextFieldState() }
    val tocTfState = tocState ?: remember { TextFieldState() }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Left: Category/Book Editable ComboBox with predictive suggestions
            Column(Modifier.weight(1f)) {
                val leftPopupManager = remember {
                    PopupManager(onPopupVisibleChange = {}, name = "ReferenceScopePopup")
                }
                var leftFocusedIndex by remember { mutableIntStateOf(-1) }
                val leftCategoriesCount = categorySuggestions.size
                val leftTotal = leftCategoriesCount + bookSuggestions.size
                LaunchedEffect(suggestionsVisible, categorySuggestions, bookSuggestions) {
                    val visible = suggestionsVisible && (categorySuggestions.isNotEmpty() || bookSuggestions.isNotEmpty())
                    leftPopupManager.setPopupVisible(visible)
                    leftFocusedIndex = if (visible) 0 else -1
                }
                EditableComboBox(
                    textFieldState = refState,
                    modifier = Modifier
                        .fillMaxWidth(),
                    enabled = true,
                    outline = Outline.None,
                    interactionSource = remember { MutableInteractionSource() },
                    style = JewelTheme.comboBoxStyle,
                    textStyle = TextStyle(fontSize = 13.sp),
                    onArrowDownPress = {
                        if (leftTotal > 0) {
                            leftFocusedIndex = (leftFocusedIndex + 1).coerceAtMost(leftTotal - 1)
                        }
                    },
                    onArrowUpPress = {
                        if (leftTotal > 0) {
                            leftFocusedIndex = (leftFocusedIndex - 1).coerceAtLeast(0)
                        }
                    },
                    onEnterPress = {
                        if (leftFocusedIndex in 0 until leftTotal) {
                            if (leftFocusedIndex < leftCategoriesCount) {
                                val picked = categorySuggestions[leftFocusedIndex]
                                onPickCategory(picked)
                                if (submitAfterPick) onSubmit()
                            } else {
                                val idx = leftFocusedIndex - leftCategoriesCount
                                val picked = bookSuggestions.getOrNull(idx)
                                if (picked != null) {
                                    onPickBook(picked)
                                    if (submitAfterPick) onSubmit()
                                }
                            }
                        } else {
                            onSubmit()
                        }
                    },
                    popupManager = leftPopupManager,
                    popupContent = {
                        SuggestionsPanel(
                            categorySuggestions = categorySuggestions,
                            bookSuggestions = bookSuggestions,
                            onPickCategory = onPickCategory,
                            onPickBook = onPickBook,
                            focusedIndex = leftFocusedIndex
                        )
                    }
                )
            }

            // Right: TOC Editable ComboBox with predictive suggestions
            Column(Modifier.weight(1f)) {
                val rightPopupManager = remember {
                    PopupManager(onPopupVisibleChange = {}, name = "TocScopePopup")
                }
                var rightFocusedIndex by remember { mutableIntStateOf(-1) }
                LaunchedEffect(tocSuggestionsVisible, tocSuggestions, selectedBook?.id) {
                    val visible = selectedBook != null && tocSuggestionsVisible && tocSuggestions.isNotEmpty()
                    rightPopupManager.setPopupVisible(visible)
                    rightFocusedIndex = if (visible) 0 else -1
                }
                EditableComboBox(
                    textFieldState = tocTfState,
                    modifier = Modifier
                        .fillMaxWidth(),
                    enabled = selectedBook != null,
                    outline = Outline.None,
                    interactionSource = remember { MutableInteractionSource() },
                    style = JewelTheme.comboBoxStyle,
                    textStyle = TextStyle(fontSize = 13.sp),
                    onArrowDownPress = {
                        if (tocSuggestions.isNotEmpty()) {
                            rightFocusedIndex = (rightFocusedIndex + 1).coerceAtMost(tocSuggestions.lastIndex)
                        }
                    },
                    onArrowUpPress = {
                        if (tocSuggestions.isNotEmpty()) {
                            rightFocusedIndex = (rightFocusedIndex - 1).coerceAtLeast(0)
                        }
                    },
                    onEnterPress = {
                        if (rightFocusedIndex in tocSuggestions.indices) {
                            onPickToc(tocSuggestions[rightFocusedIndex])
                            if (submitAfterPick) onSubmit()
                        } else {
                            onSubmit()
                        }
                    },
                    popupManager = rightPopupManager,
                    popupContent = {
                        TocSuggestionsPanel(
                            tocSuggestions = tocSuggestions,
                            onPickToc = onPickToc,
                            focusedIndex = rightFocusedIndex,
                            selectedBook = selectedBook
                        )
                    }
                )
            }
        }
    }

    // Once a book is selected, clear TOC field and move focus to it
    LaunchedEffect(selectedBook?.id) {
        if (selectedBook != null) {
            // Clear previous TOC query when the book changes
            tocTfState.edit { replace(0, length, "") }
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
            .background(if (active) Color(0x220E639C) else Color.Transparent)
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
    enabled: Boolean = true
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

    val hints = if (selectedFilter == SearchFilter.REFERENCE) referenceHints else textHints

    // Restart animation cleanly when switching filter
    var filterVersion by remember { mutableStateOf(0) }
    LaunchedEffect(selectedFilter) { filterVersion++ }

    // Disable placeholder animation while user is typing
    val isUserTyping by remember { derivedStateOf { state.text.isNotEmpty() } }

    // Auto-focus the main search field on first composition
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(200)
        if (enabled) focusRequester.requestFocus()
    }

    TextField(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .onPreviewKeyEvent { ev ->
                when {
                    (ev.key == Key.Enter || ev.key == Key.NumPadEnter) && ev.type == KeyEventType.KeyUp -> {
                        onSubmit(); true
                    }
                    ev.key == Key.Tab && ev.type == KeyEventType.KeyUp -> {
                        onTab?.invoke(); true
                    }
                    else -> false
                }
            }
            .focusRequester(focusRequester),
        enabled = enabled,
        placeholder = {
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
        },
        trailingIcon = if (showToggle) ({
            IntegratedSwitch(
                selectedFilter = selectedFilter,
                onFilterChange = onFilterChange
            )
        }) else null,
        leadingIcon = {
            if (!showIcon) return@TextField
            IconButton({ onSubmit() }) {
                Icon(
                    key = AllIconsKeys.Actions.Find,
                    contentDescription = stringResource(Res.string.search_icon_description),
                    modifier = Modifier.size(16.dp).pointerHoverIcon(PointerIcon.Hand),
                )
            }
        },
        textStyle = TextStyle(fontSize = 13.sp),
    )
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
        HomeView(
            uiState = BookContentState(),
            onEvent = {},
            modifier = Modifier
        )
    }
}
