package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import io.github.kdroidfilter.seforimlibrary.core.models.Book as BookModel

// SearchFilter moved to features.search.SearchFilter per architecture guidelines.

// Suggestion models for the scope picker
private data class CategorySuggestion(val category: Category, val path: List<String>)
private data class BookSuggestion(val book: BookModel, val path: List<String>)

data class SearchFilterCard(
    val icons: ImageVector,
    val label: StringResource,
    val desc: StringResource,
    val explanation: StringResource
)

@OptIn(ExperimentalJewelApi::class, ExperimentalLayoutApi::class)
@Composable
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
            val searchVm = remember { appGraph.searchHomeViewModel() }
            val searchUi = searchVm.uiState.collectAsState().value
            val scope = rememberCoroutineScope()
            val searchState = remember { TextFieldState() }
            val referenceSearchState = remember { TextFieldState() }
            // Forward reference input changes to the ViewModel (VM handles debouncing and suggestions)
            LaunchedEffect(Unit) {
                snapshotFlow { referenceSearchState.text.toString() }
                    .collect { qRaw -> searchVm.onReferenceQueryChanged(qRaw) }
            }
            fun launchSearch() {
                val query = searchState.text.toString().trim()
                if (query.isBlank() || searchUi.selectedFilter != SearchFilter.TEXT) return
                scope.launch { searchVm.submitSearch(query) }
            }

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
                            onSubmit = { launchSearch() }
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
                            if (searchUi.selectedFilter == SearchFilter.TEXT) {
                                ReferenceByCategorySection(
                                    modifier,
                                    state = referenceSearchState,
                                    suggestionsVisible = searchUi.suggestionsVisible,
                                    categorySuggestions = searchUi.categorySuggestions.map { cs ->
                                        CategorySuggestion(
                                            cs.category,
                                            cs.path
                                        )
                                    },
                                    bookSuggestions = searchUi.bookSuggestions.map { bs ->
                                        BookSuggestion(
                                            bs.book,
                                            bs.path
                                        )
                                    },

                                    onSubmit = { launchSearch() },
                                    onPickCategory = { picked ->
                                        searchVm.onPickCategory(picked.category)
                                        referenceSearchState.edit { replace(0, length, picked.category.title) }
                                    },
                                    onPickBook = { picked ->
                                        searchVm.onPickBook(picked.book)
                                        referenceSearchState.edit { replace(0, length, picked.book.title) }
                                    }
                                )
                                SearchLevelsPanel(
                                    selectedIndex = searchUi.selectedLevelIndex,
                                    onSelectedIndexChange = { searchVm.onLevelIndexChange(it) }
                                )
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
private fun ReferenceByCategorySection(
    modifier: Modifier = Modifier,
    state: TextFieldState? = null,
    suggestionsVisible: Boolean = false,
    categorySuggestions: List<CategorySuggestion> = emptyList(),
    bookSuggestions: List<BookSuggestion> = emptyList(),
    onSubmit: () -> Unit = {},
    onPickCategory: (CategorySuggestion) -> Unit = {},
    onPickBook: (BookSuggestion) -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    GroupHeader(
        text = stringResource(Res.string.search_by_category_or_book),
        modifier =
            modifier
                .clickable(indication = null, interactionSource = interactionSource) {
                    isExpanded = !isExpanded
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
    Column(Modifier.fillMaxWidth()) {
        SearchBar(
            state = refState,
            selectedFilter = SearchFilter.REFERENCE,
            onFilterChange = {},
            showToggle = false,
            showIcon = false,
            modifier = Modifier.fillMaxWidth(1f),
            onSubmit = onSubmit
        )
        if (suggestionsVisible && (categorySuggestions.isNotEmpty() || bookSuggestions.isNotEmpty())) {
            Spacer(Modifier.height(8.dp))
            SuggestionsPanel(
                categorySuggestions = categorySuggestions,
                bookSuggestions = bookSuggestions,
                onPickCategory = onPickCategory,
                onPickBook = onPickBook
            )
        }
    }
}

@Composable
private fun SuggestionsPanel(
    categorySuggestions: List<CategorySuggestion>,
    bookSuggestions: List<BookSuggestion>,
    onPickCategory: (CategorySuggestion) -> Unit,
    onPickBook: (BookSuggestion) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
            .background(JewelTheme.globalColors.panelBackground)
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Categories first
        categorySuggestions.forEach { cat ->
            SuggestionRow(
                parts = cat.path,
                onClick = { onPickCategory(cat) }
            )
        }
        // Then books
        bookSuggestions.forEach { book ->
            SuggestionRow(
                parts = book.path,
                onClick = { onPickBook(book) }
            )
        }
    }
}

@Composable
private fun SuggestionRow(parts: List<String>, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        parts.forEachIndexed { index, text ->
            if (index > 0) Text(
                stringResource(Res.string.breadcrumb_separator),
                color = JewelTheme.globalColors.text.disabled
            )
            Text(text, color = JewelTheme.globalColors.text.normal)
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
    onSubmit: () -> Unit = {}
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

    TextField(
        state = state,
        modifier = modifier
            .width(600.dp)
            .height(40.dp)
            .onPreviewKeyEvent { ev ->
                if ((ev.key == Key.Enter || ev.key == Key.NumPadEnter) && ev.type == KeyEventType.KeyUp) {
                    onSubmit(); true
                } else false
            },
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
