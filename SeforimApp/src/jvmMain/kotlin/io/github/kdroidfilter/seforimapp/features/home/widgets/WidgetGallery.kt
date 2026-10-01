package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.icons.Trash
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widgets_added
import seforimapp.seforimapp.generated.resources.home_widgets_done
import seforimapp.seforimapp.generated.resources.home_widgets_edit
import seforimapp.seforimapp.generated.resources.home_widgets_gallery_hint
import seforimapp.seforimapp.generated.resources.home_widgets_gallery_title
import seforimapp.seforimapp.generated.resources.home_widgets_removed
import seforimapp.seforimapp.generated.resources.home_widgets_reset
import seforimapp.seforimapp.generated.resources.home_widgets_trash_hint
import seforimapp.seforimapp.generated.resources.home_widgets_trash_release
import seforimapp.seforimapp.generated.resources.home_widgets_undo
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

private const val PREVIEW_SCALE = 0.42f
private const val GHOST_SCALE = 0.6f

/** Room the gallery panel takes at the bottom of the Home, for the page to scroll its last widgets above it. */
val WIDGET_GALLERY_HEIGHT = 320.dp

/**
 * The widget gallery floating over the bottom of the Home, as on macOS, and the widget dragged out of it: click a
 * widget to add it at the end, or drag it onto the grid (onto a widget to take its place). Escape or "Done" closes.
 */
@Composable
fun BoxScope.HomeWidgetsOverlay(
    state: HomeWidgetsState,
    placed: List<WidgetPlacement>,
) {
    val drag = state.drag
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.matchParentSize().onGloballyPositioned { origin = it.positionInRoot() })
    if (state.editingWidgets) {
        WidgetGallery(state, placed, Modifier.align(Alignment.BottomCenter))
    }
    if (!state.editingWidgets && drag.movingId == null && drag.newWidget == null) {
        EditWidgetsButton(state, Modifier.align(Alignment.BottomEnd).padding(20.dp))
    }
    // Only while a placed widget is moved, above the page's auto-scroll band so aiming at it doesn't scroll
    if (drag.movingId != null) {
        TrashZone(drag, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
    }
    state.lastRemoved?.let { removed ->
        UndoBar(
            removed = removed,
            state = state,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (state.editingWidgets) WIDGET_GALLERY_HEIGHT else 24.dp),
        )
    }
    // The dragged widget follows the pointer, above everything
    drag.newWidget?.let { dragged ->
        val grid = dragged.grid
        Box(
            Modifier
                .absoluteOffset {
                    val half = Offset(grid.width().toPx(), grid.height().toPx()) * (GHOST_SCALE / 2)
                    val at = drag.pointer - origin - half
                    IntOffset(at.x.roundToInt(), at.y.roundToInt())
                }.alpha(0.9f)
                .shadow(16.dp, RoundedCornerShape(18.dp * GHOST_SCALE)),
        ) {
            WidgetPreview(dragged.widget, grid, state, GHOST_SCALE)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetGallery(
    state: HomeWidgetsState,
    placed: List<WidgetPlacement>,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // Fades away while a widget is dragged out, to show the grid it goes to
    val alpha by animateFloatAsState(if (state.drag.newWidget != null || state.drag.movingId != null) 0.15f else 1f)
    Column(
        modifier
            .padding(16.dp)
            .widthIn(max = 960.dp)
            .fillMaxWidth()
            .height(WIDGET_GALLERY_HEIGHT - 32.dp)
            .alpha(alpha)
            .onGloballyPositioned { state.drag.galleryBounds = it.boundsInRoot() }
            .shadow(24.dp, shape)
            .clip(shape)
            .background(JewelTheme.globalColors.panelBackground)
            .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent {
                (it.type == KeyEventType.KeyDown && it.key == Key.Escape).also { escape ->
                    if (escape) state.editingWidgets = false
                }
            }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.home_widgets_gallery_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(Res.string.home_widgets_gallery_hint),
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.info,
                )
            }
            RoundIconButton(
                icon = AllIconsKeys.Actions.Rollback,
                label = stringResource(Res.string.home_widgets_reset),
                onClick = { state.layout.reset() },
            )
            RoundIconButton(
                icon = AllIconsKeys.Actions.Checked,
                label = stringResource(Res.string.home_widgets_done),
                onClick = { state.editingWidgets = false },
                primary = true,
            )
        }
        VerticallyScrollableContainer(Modifier.fillMaxSize()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                itemVerticalAlignment = Alignment.Bottom,
            ) {
                availableHomeWidgets.filter { it.isSupported }.forEach { widget ->
                    GalleryItem(widget, state, added = placed.any { it.widget.id == widget.id })
                }
            }
        }
    }
}

@Composable
private fun GalleryItem(
    widget: HomeWidget,
    state: HomeWidgetsState,
    added: Boolean,
) {
    val drag = state.drag
    var size by remember(widget) { mutableStateOf(widget.defaultSize) }
    val grid = widget.sizes.getValue(size)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .alpha(if (added) 0.4f else 1f)
                .onGloballyPositioned { bounds = it.boundsInRoot() },
        ) {
            WidgetPreview(widget, grid, state, PREVIEW_SCALE)
            // On top of the preview, so its own buttons never get the click
            Box(
                Modifier
                    .matchParentSize()
                    .then(if (added) Modifier else Modifier.pointerHoverIcon(PointerIcon.Hand))
                    .pointerInput(added) { detectTapGestures { if (!added) state.layout.add(widget, size) } }
                    .pointerInput(added, size) {
                        if (added) return@pointerInput
                        detectDragGestures(
                            onDragStart = {
                                drag.newWidget = WidgetPlacement(widget, size)
                                drag.moveTo(bounds.topLeft + it)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                drag.moveTo(drag.pointer + amount)
                            },
                            onDragEnd = {
                                // Dropped back on the gallery: nothing; else where its placeholder shows
                                if (drag.overGrid) {
                                    val target = availableHomeWidgets.firstOrNull { it.id == drag.targetId }
                                    state.layout.add(widget, size, before = target)
                                }
                                drag.end()
                            },
                            onDragCancel = { drag.end() },
                        )
                    },
            )
        }
        Text(stringResource(widget.title), fontWeight = FontWeight.SemiBold)
        when {
            added ->
                Text(stringResource(Res.string.home_widgets_added), fontSize = 11.sp, color = JewelTheme.globalColors.text.info)
            widget.sizes.size > 1 ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WidgetSize.entries.filter { it in widget.sizes }.forEach { option ->
                        SizeChip(stringResource(option.label), selected = option == size, onClick = { size = option })
                    }
                }
        }
    }
}

/** The widget's [HomeWidget.Preview] at its real size, scaled down by [scale]. */
@Composable
internal fun WidgetPreview(
    widget: HomeWidget,
    grid: GridSize,
    state: HomeWidgetsState,
    scale: Float,
) {
    Box(
        propagateMinConstraints = true,
        modifier =
            Modifier
                .clip(RoundedCornerShape(18.dp * scale))
                .layout { measurable, _ ->
                    val width = grid.width().roundToPx()
                    val height = grid.height().roundToPx()
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout((width * scale).roundToInt(), (height * scale).roundToInt()) {
                        placeable.placeWithLayer(0, 0) {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                    }
                },
    ) {
        // No live Filament scene in a thumbnail, should a Preview fall back to its content
        CompositionLocalProvider(LocalTabSelected provides false) {
            widget.Preview(state, Modifier)
        }
    }
}

@Composable
private fun SizeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Text(
        text = label,
        fontSize = 11.sp,
        modifier =
            Modifier
                .clip(shape)
                .background(
                    if (selected) {
                        JewelTheme.globalColors.outlines.focused
                            .copy(alpha = 0.25f)
                    } else {
                        JewelTheme.globalColors.panelBackground
                    },
                ).border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** A pencil floating in the Home's corner, always at hand, that opens the edit mode and its gallery. */
@Composable
private fun EditWidgetsButton(
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
) {
    RoundIconButton(
        icon = AllIconsKeys.Actions.Edit,
        label = stringResource(Res.string.home_widgets_edit),
        onClick = { state.editingWidgets = true },
        modifier = modifier.shadow(8.dp, CircleShape),
    )
}

/** A round icon button whose [label] shows as a tooltip; [primary] fills it with the accent, for the main action. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundIconButton(
    icon: IconKey,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val accent = JewelTheme.globalColors.outlines.focused
    Tooltip(tooltip = { Text(label) }, modifier = modifier) {
        IconButton(
            onClick = onClick,
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (primary) accent else JewelTheme.globalColors.panelBackground)
                    .border(1.dp, if (primary) accent else JewelTheme.globalColors.borders.normal, CircleShape)
                    .semantics { contentDescription = label },
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (primary) Color.White else JewelTheme.globalColors.text.normal,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The drop zone a moved widget is removed in: red and larger once the pointer is over it. */
@Composable
private fun TrashZone(
    drag: WidgetDrag,
    modifier: Modifier = Modifier,
) {
    val over = drag.overTrash
    val scale by animateFloatAsState(if (over) 1.12f else 1f)
    val danger = Color(0xFFE5484D)
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .onGloballyPositioned { drag.trashBounds = it.boundsInRoot() }
            .testTag("widget-trash")
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.shadow(16.dp, shape)
            .clip(shape)
            .background(if (over) danger else JewelTheme.globalColors.panelBackground)
            .border(1.5.dp, danger.copy(alpha = if (over) 1f else 0.6f), shape)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val tint = if (over) Color.White else danger
        Image(Trash, contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = Modifier.size(22.dp))
        Text(
            stringResource(if (over) Res.string.home_widgets_trash_release else Res.string.home_widgets_trash_hint),
            color = tint,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** "X removed · Undo", for a few seconds after a widget is removed. */
@Composable
private fun UndoBar(
    removed: RemovedWidget,
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(removed) {
        delay(UNDO_DELAY)
        state.forgetRemoved(removed)
    }
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .shadow(12.dp, shape)
            .clip(shape)
            .background(JewelTheme.globalColors.panelBackground)
            .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
            .padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.home_widgets_removed, stringResource(removed.placement.widget.title)))
        Link(stringResource(Res.string.home_widgets_undo), onClick = state::undoRemove)
    }
}

private val UNDO_DELAY = 6.seconds
