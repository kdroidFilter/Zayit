package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.takeOrElse
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.lazy.*
import org.jetbrains.jewel.foundation.modifier.onMove
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.EditableComboBox
import org.jetbrains.jewel.ui.component.PopupManager
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.theme.comboBoxStyle

@OptIn(ExperimentalJewelApi::class)
@Composable
fun PredictiveListComboBox(
    items: List<String>,
    selectedIndex: Int,
    onSelectedItemChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    onPopupVisibleChange: (visible: Boolean) -> Unit = {},
    itemKeys: (Int, String) -> Any = { _, item -> item },
    listState: SelectableLazyListState =
        rememberSelectableLazyListState(selectedIndex.takeIfInBoundsOrZero(items.indices)),
) {
    // Use the current selected item's text as initial query (keeps parity with EditableListComboBox)
    val textFieldState = rememberTextFieldState(items.getOrNull(selectedIndex).orEmpty())
    // Hovered index in the FILTERED list (-1 = none)
    var hoveredFilteredIndex by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()

    // Keep the SelectableLazyListState's selected key aligned to selectedIndex on first composition.
    LaunchedEffect(itemKeys) {
        listState.selectedKeys =
            items.getOrNull(selectedIndex)?.let { setOf(itemKeys(selectedIndex, it)) } ?: emptySet()
    }

    // Current query drives the filtering. Case-insensitive "startsWith".
    val query = textFieldState.text
    val filteredIndices = remember(query, items) {
        if (query.isBlank()) items.indices.toList()
        else items.indices.filter { i -> items[i].startsWith(query, ignoreCase = true) }
    }

    // If the current hovered index is out of bounds after filtering, reset it.
    LaunchedEffect(filteredIndices) {
        if (hoveredFilteredIndex !in filteredIndices.indices) hoveredFilteredIndex = -1
    }

    // Helper: commit a selection by ORIGINAL index (in 'items').
    fun setSelectedOriginalIndex(originalIndex: Int) {
        if (originalIndex < 0 || originalIndex > items.lastIndex) return

        // IMPORTANT: edit the text field BEFORE changing list selection to avoid nested edits
        textFieldState.edit { replace(0, length, items[originalIndex]) }

        val key = itemKeys(originalIndex, items[originalIndex])
        if (listState.selectedKeys.size != 1 || key !in listState.selectedKeys) {
            listState.selectedKeys = setOf(key)
        }
        onSelectedItemChange(originalIndex)
        scope.launch { listState.lazyListState.scrollToItem(filteredIndices.indexOf(originalIndex).coerceAtLeast(0)) }
    }

    // Map from selectedKeys -> filtered index, returns -1 if not in filtered set.
    fun selectedFilteredIndex(): Int {
        if (listState.selectedKeys.isEmpty()) return -1
        val selectedKey = listState.selectedKeys.first()
        for (fi in filteredIndices.indices) {
            val oi = filteredIndices[fi]
            if (itemKeys(oi, items[oi]) == selectedKey) return fi
        }
        return -1
    }

    val contentPadding = style.metrics.popupContentPadding
    val popupMaxHeight = maxPopupHeight.takeOrElse { style.metrics.maxPopupHeight }

    val popupManager = remember {
        PopupManager(
            onPopupVisibleChange = { visible ->
                // Reset preview hover and notify the caller
                hoveredFilteredIndex = -1
                onPopupVisibleChange(visible)
            },
            name = "PredictiveListComboBoxPopup",
        )
    }

    EditableComboBox(
        textFieldState = textFieldState,
        modifier = modifier,
        enabled = enabled,
        outline = outline,
        interactionSource = interactionSource,
        style = style,
        textStyle = textStyle,
        onArrowDownPress = {
            if (filteredIndices.isEmpty()) return@EditableComboBox
            var current = selectedFilteredIndex()

            // If we are hovering, treat that as the current selection (unless it's the last)
            if (hoveredFilteredIndex >= 0 && hoveredFilteredIndex < filteredIndices.lastIndex) {
                current = hoveredFilteredIndex
                hoveredFilteredIndex = -1
            }

            val nextFiltered = (current + 1).coerceAtMost(filteredIndices.lastIndex)
            setSelectedOriginalIndex(filteredIndices[nextFiltered])
        },
        onArrowUpPress = {
            if (filteredIndices.isEmpty()) return@EditableComboBox
            var current = selectedFilteredIndex()

            // If we are hovering, treat that as the current selection (unless it's the first)
            if (hoveredFilteredIndex > 0) {
                current = hoveredFilteredIndex
                hoveredFilteredIndex = -1
            }

            val prevFiltered = (current - 1).coerceAtLeast(0)
            setSelectedOriginalIndex(filteredIndices[prevFiltered])
        },
        onEnterPress = {
            // If the text exactly matches an item, select it.
            val exactIdx = items.indexOf(textFieldState.text)
            if (exactIdx != -1) {
                setSelectedOriginalIndex(exactIdx)
            } else if (filteredIndices.isNotEmpty()) {
                // Otherwise select the first filtered suggestion.
                setSelectedOriginalIndex(filteredIndices.first())
            }
        },
        popupManager = popupManager,
        popupContent = {
            // Close popup if there is nothing to show and query is not empty
            // (optional behavior; you can remove this if you prefer empty list UI)
            if (query.isNotBlank() && filteredIndices.isEmpty()) {
                // Nothing matches: show an empty popup with proper height constraints to avoid jumps,
                // or simply do nothing (popup stays open but empty). Here we keep it minimal:
                Box(modifier = Modifier.heightIn(max = popupMaxHeight).padding(contentPadding))
                return@EditableComboBox
            }

            FilteredPopupContent(
                items = items,
                filteredIndices = filteredIndices,
                currentlySelectedOriginalIndex = selectedIndex,
                previewSelectedFilteredIndex = hoveredFilteredIndex,
                listState = listState,
                popupMaxHeight = popupMaxHeight,
                contentPadding = contentPadding,
                onHoveredFilteredIndexChange = { fi ->
                    if (fi >= 0 && hoveredFilteredIndex != fi) hoveredFilteredIndex = fi
                },
                onSelectedOriginalIndexChange = ::setSelectedOriginalIndex,
                itemKeys = itemKeys,
            )
        },
    )
}

@Composable
private fun FilteredPopupContent(
    items: List<String>,
    filteredIndices: List<Int>,
    currentlySelectedOriginalIndex: Int,
    previewSelectedFilteredIndex: Int,
    listState: SelectableLazyListState,
    popupMaxHeight: Dp,
    contentPadding: PaddingValues,
    onHoveredFilteredIndexChange: (Int) -> Unit,
    onSelectedOriginalIndexChange: (Int) -> Unit,
    itemKeys: (Int, String) -> Any,
) {
    VerticallyScrollableContainer(
        scrollState = listState.lazyListState as ScrollableState,
        modifier = Modifier.heightIn(max = popupMaxHeight),
    ) {
        SelectableLazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = popupMaxHeight)
                .padding(contentPadding)
                .testTag("Jewel.PredictiveComboBox.List"),
            selectionMode = SelectionMode.Single,
            state = listState,
            onSelectedIndexesChange = { selectedFilteredIndexes ->
                val filteredIndex = selectedFilteredIndexes.firstOrNull() ?: return@SelectableLazyColumn
                val originalIndex = filteredIndices.getOrNull(filteredIndex) ?: return@SelectableLazyColumn
                onSelectedOriginalIndexChange(originalIndex)
            },
        ) {
            itemsIndexed(
                items = filteredIndices,
                key = { filteredIdx, originalIdx -> itemKeys(originalIdx, items[originalIdx]) },
                itemContent = { filteredIdx, originalIdx ->
                    val originalText = items[originalIdx]
                    Box(
                        modifier = Modifier.thenIf(!listState.isScrollInProgress) {
                            onMove {
                                if (previewSelectedFilteredIndex != filteredIdx) {
                                    onHoveredFilteredIndexChange(filteredIdx)
                                }
                            }
                        }
                    ) {
                        val key = itemKeys(originalIdx, originalText)
                        val isActuallySelected = listState.selectedKeys.contains(key)
                        val showAsSelected =
                            (isActuallySelected && previewSelectedFilteredIndex < 0) ||
                                previewSelectedFilteredIndex == filteredIdx

                        // We assume items are "active" while popup is visible (same rationale as the base impl)
                        SimpleListItem(
                            text = originalText,
                            selected = showAsSelected,
                            active = true,
                            iconContentDescription = originalText
                        )
                    }
                },
            )
        }
    }

    // Ensure the "current selection" (by original index) is scrolled into the filtered view.
    LaunchedEffect(filteredIndices) {
        val filteredIndexToShow = filteredIndices.indexOf(currentlySelectedOriginalIndex).let {
            if (it >= 0) it else 0
        }
        if (filteredIndices.isNotEmpty()) {
            listState.lazyListState.scrollToItem(filteredIndexToShow)
        }
    }
}

// Keep your existing helper:
private fun Int.takeIfInBoundsOrZero(acceptedIndices: IntRange) = if (this in acceptedIndices) this else 0
