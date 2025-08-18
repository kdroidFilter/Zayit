package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
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
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state.BookContentUiState
import io.github.kdroidfilter.seforimapp.icons.*
import io.github.kdroidfilter.seforimapp.texteffects.TypewriterPlaceholder
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
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
    uiState: BookContentUiState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    VerticallyScrollableContainer(
        scrollState = listState,
    ) {
        Box(
            modifier = modifier.padding(16.dp).fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            // Keep state outside LazyColumn so it persists across item recompositions
            val searchState = remember { TextFieldState() }
            var selectedFilter by remember { mutableStateOf(SearchFilter.TEXT) }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.width(600.dp)
            ) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        WelcomeUser(username = "אליהו")
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
                            onFilterChange = { selectedFilter = it }
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
                                SearchLevelsPanel()
                                ReferenceByCategorySection(modifier)
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
    Text("שלום $username !", textAlign = TextAlign.Center, fontSize = 36.sp)
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
private fun SearchLevelsPanel(modifier: Modifier = Modifier) {
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
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    val maxIndex = (filterCards.size - 1).coerceAtLeast(0)
    val selectedIndex = sliderPosition.coerceIn(0f, maxIndex.toFloat()).toInt()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        filterCards.forEachIndexed { index, filterCard ->
            SearchLevelCard(
                data = filterCard,
                selected = index == selectedIndex,
                onClick = { sliderPosition = index.toFloat() }
            )
        }
    }

    Slider(
        value = sliderPosition,
        onValueChange = { newValue -> sliderPosition = newValue },
        valueRange = 0f..maxIndex.toFloat(),
        steps = (filterCards.size - 2).coerceAtLeast(0),
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
    )
}


///**
// * Expandable section to search by category and optionally by book.
// * Mirrors the original behavior and emits CategorySelected events on selection changes.
// */
//@Composable
//private fun CategoryAndBookPicker(
//    uiState: BookContentUiState,
//    onEvent: (BookContentEvent) -> Unit,
//    modifier: Modifier = Modifier
//) {
//    var isExpanded by remember { mutableStateOf(false) }
//    val interactionSource = remember { MutableInteractionSource() }
//
//    GroupHeader(
//        text = stringResource(Res.string.search_by_category_or_book),
//        modifier =
//        modifier
//            .clickable(indication = null, interactionSource = interactionSource) {
//                isExpanded = !isExpanded
//            }
//            .hoverable(interactionSource)
//            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))),
//        startComponent = {
//            if (isExpanded) {
//                Icon(AllIconsKeys.General.ChevronDown, "Chevron")
//            } else {
//                Icon(AllIconsKeys.General.ChevronLeft, "Chevron")
//            }
//        },
//    )
//
//    if (!isExpanded) return
//
//    // Cascading combo boxes for categories and books from the library database
//    val navigation = uiState.navigation
//
//    // Selected category indices per level; changing a level clears deeper selections
//    val selectedCategoryIndices = remember { mutableStateListOf<Int>() }
//    var selectedBookIndex by remember { mutableIntStateOf(-1) }
//
//    // Reset selection path if root categories list changes (e.g., after loading)
//    LaunchedEffect(navigation.rootCategories) {
//        selectedCategoryIndices.clear()
//        selectedBookIndex = -1
//    }
//
//    // Build levels of categories based on current selection path
//    val categoryLevels = remember(
//        navigation.rootCategories,
//        navigation.categoryChildren,
//        selectedCategoryIndices.toList()
//    ) {
//        buildList {
//            var currentLevel: List<Category> = navigation.rootCategories
//            add(currentLevel)
//            var depth = 0
//            while (depth < selectedCategoryIndices.size) {
//                val idx = selectedCategoryIndices[depth]
//                if (idx !in currentLevel.indices) break
//                val selectedCat = currentLevel[idx]
//                val children = navigation.categoryChildren[selectedCat.id] ?: emptyList()
//                if (children.isEmpty()) break
//                add(children)
//                currentLevel = children
//                depth++
//            }
//        }
//    }
//
//    FlowRow(
//        modifier = Modifier.fillMaxWidth(),
//        horizontalArrangement = Arrangement.spacedBy(8.dp),
//        verticalArrangement = Arrangement.spacedBy(8.dp)
//    ) {
//        // Render a ListComboBox for each category level
//        categoryLevels.forEachIndexed { level, items ->
//            val selIndex = selectedCategoryIndices.getOrNull(level) ?: -1
//            // Jewel's ListComboBox doesn't support -1 when the list is non-empty; coerce to 0 safely
//            val safeSelIndex = if (items.isNotEmpty() && selIndex !in items.indices) 0 else selIndex
//            ListComboBox(
//                items = items,
//                selectedIndex = safeSelIndex,
//                modifier = Modifier.widthIn(max = 260.dp),
//                onSelectedItemChange = { newIndex ->
//                    // Ensure list has positions up to this level
//                    while (selectedCategoryIndices.size <= level) selectedCategoryIndices.add(-1)
//                    // Update this level
//                    selectedCategoryIndices[level] = newIndex
//                    // Trim deeper levels
//                    while (selectedCategoryIndices.size > level + 1) selectedCategoryIndices.removeLast()
//                    // Reset book selection when category changes
//                    selectedBookIndex = -1
//                    // Fire event with the newly selected category if valid
//                    if (newIndex in items.indices) {
//                        val cat = items[newIndex]
//                        onEvent(
//                            BookContentEvent.CategorySelected(
//                                cat
//                            )
//                        )
//                    }
//                },
//                itemKeys = { _, item -> item.id },
//                itemContent = { item, isSelected, isActive ->
//                    SimpleListItem(
//                        text = item.title,
//                        selected = isSelected,
//                        active = isActive,
//                        iconContentDescription = item.title,
//                        icon = AllIconsKeys.Nodes.Folder,
//                        colorFilter = null,
//                    )
//                },
//            )
//        }
//
//        // Optional: books combobox for the deepest selected category
//        val deepestCategory: Category? = run {
//            if (categoryLevels.isEmpty()) null else {
//                var cat: Category? = null
//                var items = categoryLevels.first()
//                var level = 0
//                while (level < selectedCategoryIndices.size) {
//                    val idx = selectedCategoryIndices[level]
//                    if (idx !in items.indices) break
//                    cat = items[idx]
//                    val children = navigation.categoryChildren[cat.id] ?: emptyList()
//                    if (children.isEmpty()) break
//                    items = children
//                    level++
//                }
//                cat
//            }
//        }
//
//        val booksInDeepest = remember(navigation.booksInCategory, deepestCategory) {
//            navigation.booksInCategory
//                .filter { it.categoryId == deepestCategory?.id }
//                .sortedBy { it.title }
//        }
//
//        if (deepestCategory != null && booksInDeepest.isNotEmpty()) {
//            // Coerce invalid selection to 0 to avoid ListComboBox index errors
//            val safeBookIndex = if (selectedBookIndex !in booksInDeepest.indices) 0 else selectedBookIndex
//            ListComboBox(
//                items = booksInDeepest,
//                selectedIndex = safeBookIndex,
//                modifier = Modifier.widthIn(max = 320.dp),
//                onSelectedItemChange = { newIndex ->
//                    selectedBookIndex = newIndex
//                    if (newIndex in booksInDeepest.indices) {
//                        val book = booksInDeepest[newIndex]
//                        println("${book.id} selected")
//                    }
//                },
//                itemKeys = { _, item -> item.id },
//                itemContent = { item, _, _ ->
//                    Row(
//                        modifier = Modifier.fillMaxWidth(),
//                        verticalAlignment = Alignment.CenterVertically,
//                        horizontalArrangement = Arrangement.spacedBy(4.dp)
//                    ) {
//                        Icon(
//                            imageVector = Book_2,
//                            contentDescription = null,
//                            tint = JewelTheme.contentColor,
//                            modifier = Modifier.size(18.dp).padding(start = 4.dp)
//                        )
//                        Text(text = item.title)
//                    }
//                },
//            )
//        }
//    }
//}

@Composable
private fun ReferenceByCategorySection(modifier: Modifier = Modifier) {
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

    val referenceSearchState = remember { TextFieldState() }
    SearchBar(
        state = referenceSearchState,
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
    showToggle: Boolean = true
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
        modifier = modifier.width(600.dp).height(40.dp),
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
            .height(144.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = shape)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
            uiState = io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state.BookContentUiState(),
            onEvent = {},
            modifier = Modifier
        )
    }
}