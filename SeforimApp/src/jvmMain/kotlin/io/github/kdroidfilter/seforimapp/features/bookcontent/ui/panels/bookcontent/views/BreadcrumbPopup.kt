package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.kdroidfilter.seforimapp.core.presentation.components.ChevronIcon
import io.github.kdroidfilter.seforimapp.core.presentation.components.PopupSearchField
import io.github.kdroidfilter.seforimapp.core.presentation.components.PopupShape
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.revealItem
import io.github.kdroidfilter.seforimapp.icons.Book_2
import io.github.kdroidfilter.seforimapp.icons.JournalText
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_placeholder

// Outside clicks dismiss the popup but still reach the bar, so another segment can be clicked directly
@OptIn(ExperimentalComposeUiApi::class)
private val BreadcrumbPopupProperties =
    PopupProperties(focusable = true, dismissOnClickOutside = true, consumePointerInputOutside = false)

/**
 * The child popup of a breadcrumb segment: a filterable flat list, as IntelliJ's navigation bar.
 * Up/Down move, Enter chooses, Left/Right move to the neighboring segment, Escape closes.
 */
@Composable
internal fun BreadcrumbPopup(
    model: BreadcrumbPopupModel,
    onChoose: (BreadcrumbNode) -> Unit,
    onShift: (Int) -> Unit,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
) {
    Popup(
        popupPositionProvider = AboveAnchorStartPositionProvider,
        onDismissRequest = onDismiss,
        properties = BreadcrumbPopupProperties,
    ) {
        BreadcrumbPopupContent(model = model, onChoose = onChoose, onShift = onShift, onCancel = onCancel)
    }
}

@Composable
private fun BreadcrumbPopupContent(
    model: BreadcrumbPopupModel,
    onChoose: (BreadcrumbNode) -> Unit,
    onShift: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    var query by remember(model) { mutableStateOf("") }
    var selectedKey by remember(model) { mutableStateOf(model.items.getOrNull(model.initialSelectedIndex)?.key) }
    val filtered =
        remember(model, query) {
            val needle = query.trim()
            if (needle.isEmpty()) model.items else model.items.filter { it.title.contains(needle, ignoreCase = true) }
        }
    // Keep the selection when it survives the filter, otherwise fall back to the first match
    val selectedIndex = filtered.indexOfFirst { it.key == selectedKey }.takeIf { it >= 0 } ?: 0

    val listState = rememberLazyListState()
    LaunchedEffect(model) { listState.scrollToItem((model.initialSelectedIndex - VISIBLE_CONTEXT_ROWS).coerceAtLeast(0)) }
    LaunchedEffect(selectedIndex, filtered) {
        if (listState.layoutInfo.visibleItemsInfo.isNotEmpty()) listState.revealItem(selectedIndex)
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        modifier =
            Modifier
                .widthIn(min = 220.dp, max = 360.dp)
                .background(JewelTheme.globalColors.panelBackground, PopupShape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, PopupShape)
                .padding(6.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            filtered.getOrNull((selectedIndex + 1).coerceAtMost(filtered.lastIndex))?.let { selectedKey = it.key }
                            true
                        }
                        Key.DirectionUp -> {
                            filtered.getOrNull((selectedIndex - 1).coerceAtLeast(0))?.let { selectedKey = it.key }
                            true
                        }
                        Key.Escape -> {
                            onCancel()
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            filtered.getOrNull(selectedIndex)?.let(onChoose)
                            true
                        }
                        // Arrows follow the visual direction; they only move the caret once a filter is typed
                        Key.DirectionLeft, Key.DirectionRight -> {
                            if (query.isNotEmpty()) return@onPreviewKeyEvent false
                            val towardsEnd = (event.key == Key.DirectionLeft) == isRtl
                            onShift(if (towardsEnd) 1 else -1)
                            true
                        }
                        else -> false
                    }
                },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PopupSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(Res.string.search_placeholder),
            focusRequester = focusRequester,
        )
        VerticallyScrollableContainer(
            scrollState = listState as ScrollableState,
            modifier = Modifier.heightIn(max = 380.dp),
        ) {
            // Room for the scrollbar, so that it never covers the rows
            LazyColumn(state = listState, modifier = Modifier.padding(end = 11.dp)) {
                itemsIndexed(filtered, key = { _, node -> node.key }) { index, node ->
                    BreadcrumbPopupRow(
                        node = node,
                        isSelected = index == selectedIndex,
                        onClick = { onChoose(node) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbPopupRow(
    node: BreadcrumbNode,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val background = highlightBackground(interactionSource, isSelected)
    val tint = JewelTheme.globalColors.text.normal

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .highlightClickable(interactionSource, background, RowShape, onClick)
                .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (node) {
            is BreadcrumbNode.CategoryNode ->
                Icon(key = AllIconsKeys.Nodes.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
            is BreadcrumbNode.BookNode ->
                Icon(imageVector = Book_2, contentDescription = null, modifier = Modifier.size(16.dp), tint = tint.copy(alpha = 0.7f))
            is BreadcrumbNode.TocNode ->
                Icon(imageVector = JournalText, contentDescription = null, modifier = Modifier.size(15.dp), tint = tint.copy(alpha = 0.7f))
        }
        Text(
            text = node.title,
            fontSize = 13.sp,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Folders drill down into a next popup
        if (node.isFolder()) ChevronIcon(expanded = false, size = 10.dp, tint = JewelTheme.globalColors.text.info)
    }
}

private fun BreadcrumbNode.isFolder(): Boolean =
    when (this) {
        is BreadcrumbNode.CategoryNode -> true
        is BreadcrumbNode.BookNode -> false
        is BreadcrumbNode.TocNode -> entry.hasChildren
    }

private val RowShape = RoundedCornerShape(5.dp)

/** Hover and selection background shared by the bar segments and the popup rows. */
@Composable
internal fun highlightBackground(
    interactionSource: MutableInteractionSource,
    isSelected: Boolean,
): Color {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val accent = JewelTheme.globalColors.outlines.focused
    return when {
        isSelected -> accent.copy(alpha = 0.16f)
        isHovered -> accent.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
}

internal fun Modifier.highlightClickable(
    interactionSource: MutableInteractionSource,
    background: Color,
    shape: Shape,
    onClick: () -> Unit,
): Modifier =
    hoverable(interactionSource)
        .background(background, shape)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)

// Rows kept visible above the preselected item when the popup opens
private const val VISIBLE_CONTEXT_ROWS = 3

/** Places the popup above its anchor (the bar sits at the bottom), aligned to the anchor's start edge. */
private object AboveAnchorStartPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Rtl) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val y = anchorBounds.top - popupContentSize.height - 4
        return IntOffset(
            x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y = y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}
