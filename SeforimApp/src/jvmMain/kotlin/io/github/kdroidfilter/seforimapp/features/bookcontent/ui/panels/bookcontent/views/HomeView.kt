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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.icons.*
import io.github.kdroidfilter.seforimapp.texteffects.TypewriterPlaceholder
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.skiko.Cursor
import seforimapp.seforimapp.generated.resources.*

// Enum pour les filtres
enum class SearchFilter() {
    REFERENCE,
    TEXT
}

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
            val appGraph = io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph.current
            val scope = rememberCoroutineScope()
            val tabStateManager = appGraph.tabStateManager
            val tabsViewModel = appGraph.tabsViewModel
            val searchState = remember { TextFieldState() }
            val referenceSearchState = remember { TextFieldState() }
            var selectedFilter by remember { mutableStateOf(SearchFilter.TEXT) }
            // Hoisted search level selection (default to middle level)
            var selectedLevelIndex by remember { mutableIntStateOf(2) }
            val nearLevels = listOf(1, 3, 5, 10, 20)
            fun launchSearch() {
                val query = searchState.text.toString().trim()
                if (query.isBlank() || selectedFilter != SearchFilter.TEXT) return
                // Get current tab id
                val currentTabs = tabsViewModel.tabs.value
                val currentIndex = tabsViewModel.selectedTabIndex.value
                val currentTabId = currentTabs.getOrNull(currentIndex)?.destination?.tabId ?: return
                val repository = appGraph.repository
                scope.launch {
                    // Clear previous filters
                    tabStateManager.saveState(currentTabId, io.github.kdroidfilter.seforimapp.features.search.SearchStateKeys.FILTER_CATEGORY_ID, 0L)
                    tabStateManager.saveState(currentTabId, io.github.kdroidfilter.seforimapp.features.search.SearchStateKeys.FILTER_BOOK_ID, 0L)

                    val ref = referenceSearchState.text.toString().trim()
                    if (ref.isNotEmpty()) {
                        // Try category first (priority), with relaxed matching
                        val cat = repository.findCategoryByTitlePreferExact(ref)
                        if (cat != null) {
                            tabStateManager.saveState(currentTabId, io.github.kdroidfilter.seforimapp.features.search.SearchStateKeys.FILTER_CATEGORY_ID, cat.id)
                        } else {
                            // Try book exact match
                            val book = repository.findBookByTitlePreferExact(ref)
                            if (book != null) {
                                tabStateManager.saveState(currentTabId, io.github.kdroidfilter.seforimapp.features.search.SearchStateKeys.FILTER_BOOK_ID, book.id)
                            }
                        }
                    }

                    // Persist search params for this tab to restore state
                    tabStateManager.saveState(currentTabId, io.github.kdroidfilter.seforimapp.features.search.SearchStateKeys.QUERY, query)
                    tabStateManager.saveState(currentTabId, io.github.kdroidfilter.seforimapp.features.search.SearchStateKeys.NEAR, nearLevels[selectedLevelIndex])
                    // Replace current tab destination to Search (no new tab)
                    tabsViewModel.replaceCurrentTabDestination(
                        io.github.kdroidfilter.seforim.tabs.TabsDestination.Search(query, currentTabId)
                    )
                }
            }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.width(600.dp)
            ) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val firstName = AppSettings.getUserFirstName().orEmpty()
                            val lastName = AppSettings.getUserLastName().orEmpty()
                            val displayName = "$firstName $lastName".trim()
                            WelcomeUser(username = displayName)
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
                                selectedFilter = selectedFilter,
                                onFilterChange = { selectedFilter = it },
                                onSubmit = { launchSearch() }
                            )
                        }
                    }
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(
                                modifier = Modifier.height(250.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (selectedFilter == SearchFilter.TEXT) {
                                SearchLevelsPanel(
                                    selectedIndex = selectedLevelIndex,
                                    onSelectedIndexChange = { selectedLevelIndex = it }
                                )
                                ReferenceByCategorySection(modifier, state = referenceSearchState)
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

/**
 * App logo shown on the Home screen.
 */
@Composable
private fun LogoImage(modifier: Modifier = Modifier) {
    Image(
        painterResource(Res.drawable.zayit_transparent),
        contentDescription = null,
        modifier = modifier.size(256.dp)
    )
}

/**
 * Panel showing the 5 text-search levels as selectable cards synchronized with a slider.
 * Encapsulates its own local selection state.
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
private fun ReferenceByCategorySection(modifier: Modifier = Modifier, state: TextFieldState? = null) {
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
                Icon(AllIconsKeys.General.ChevronDown, "Chevron")
            } else {
                Icon(AllIconsKeys.General.ChevronLeft, "Chevron")
            }
        },
    )

    if (!isExpanded) return

    val refState = state ?: remember { TextFieldState() }
    SearchBar(
        state = refState,
        selectedFilter = SearchFilter.REFERENCE,
        onFilterChange = {},
        showToggle = false,
        modifier = Modifier.fillMaxWidth(1f)
    )
}

@Composable
private fun SearchBar(
    state: TextFieldState,
    selectedFilter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier,
    showToggle: Boolean = true,
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
        textStyle = TextStyle(fontSize = 13.sp)
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
