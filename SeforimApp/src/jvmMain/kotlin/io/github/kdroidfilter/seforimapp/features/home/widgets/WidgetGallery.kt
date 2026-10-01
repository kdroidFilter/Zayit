package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
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
import seforimapp.seforimapp.generated.resources.home_widgets_all_placed
import seforimapp.seforimapp.generated.resources.home_widgets_done
import seforimapp.seforimapp.generated.resources.home_widgets_edit
import seforimapp.seforimapp.generated.resources.home_widgets_gallery_hint
import seforimapp.seforimapp.generated.resources.home_widgets_gallery_title
import seforimapp.seforimapp.generated.resources.home_widgets_removed
import seforimapp.seforimapp.generated.resources.home_widgets_reset
import seforimapp.seforimapp.generated.resources.home_widgets_undo
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

private const val PREVIEW_SCALE = 0.42f
private const val GHOST_SCALE = 0.6f

/** The gallery shows widgets at their size on a full-width grid. */
private val GALLERY_PITCH = CellPitch(MAX_GRID_WIDTH)

/** Room the gallery panel takes at the bottom of the Home, for the page to scroll its last widgets above it. */
val WIDGET_GALLERY_HEIGHT = 320.dp

/**
 * The widget gallery floating over the bottom of the Home, as on macOS, and the widget dragged out of it: click a
 * widget to add it in the first vacant area, or drag it onto the grid where it should go. Escape or "Done" closes.
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
    // A widget held or landing, above everything: its live picture, where it is
    drag.lifted?.let { lifted ->
        Box(
            Modifier
                // From the top-left in either direction: a start alignment would mirror it right to left
                .align(AbsoluteAlignment.TopLeft)
                .absoluteOffset { (lifted.at - origin).round() }
                .size(with(LocalDensity.current) { lifted.size.toSize().toDpSize() })
                .testTag("widget-lifted")
                .drawBehind { drawLayer(lifted.layer) },
        )
    }
    // The dragged widget follows the pointer, above everything
    drag.newWidget?.let { dragged ->
        val span = dragged.defaultSpan
        Box(
            Modifier
                .align(AbsoluteAlignment.TopLeft)
                .absoluteOffset {
                    val half = Offset(GALLERY_PITCH.width(span.w).toPx(), GALLERY_PITCH.height(span.h).toPx()) * (GHOST_SCALE / 2)
                    val at = drag.pointer - origin - half
                    IntOffset(at.x.roundToInt(), at.y.roundToInt())
                }.alpha(0.9f)
                .shadow(16.dp, RoundedCornerShape(18.dp * GHOST_SCALE)),
        ) {
            WidgetPreview(dragged, span, state, GHOST_SCALE)
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
                icon = AllIconsKeys.General.Reset,
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
                // The ones not on the page yet: one already there is moved or resized there, not added again
                val addable = availableHomeWidgets.filter { widget -> widget.isSupported && placed.none { it.widget.id == widget.id } }
                if (addable.isEmpty()) {
                    Text(stringResource(Res.string.home_widgets_all_placed), color = JewelTheme.globalColors.text.info)
                }
                // One back while the gallery is open (removed from the page) pops in, as on iOS
                val offeredAtOpen = remember { addable.map { it.id }.toSet() }
                addable.forEach { widget -> key(widget.id) { GalleryItem(widget, state, appearing = widget.id !in offeredAtOpen) } }
            }
        }
    }
}

@Composable
private fun GalleryItem(
    widget: HomeWidget,
    state: HomeWidgetsState,
    appearing: Boolean,
) {
    val drag = state.drag
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val appear = remember { Animatable(if (appearing) 0f else 1f) }
    LaunchedEffect(Unit) { if (appear.value < 1f) appear.animateTo(1f, PopInSpec) }
    Column(
        modifier =
            Modifier.testTag("gallery-${widget.id}").graphicsLayer {
                scaleX = popIn(appear.value)
                scaleY = popIn(appear.value)
                alpha = appear.value.coerceIn(0f, 1f)
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.onGloballyPositioned { bounds = it.boundsInRoot() }) {
            WidgetPreview(widget, widget.defaultSpan, state, PREVIEW_SCALE)
            // On top of the preview, so its own buttons never get the click
            Box(
                Modifier
                    .matchParentSize()
                    .pointerHoverIcon(PointerIcon.Hand)
                    .pointerInput(widget) { detectTapGestures { state.layout.add(widget) } }
                    .pointerInput(widget) {
                        cancellableDrag(
                            root = { bounds.topLeft + it },
                            onStart = { drag.startAdd(widget, it) },
                            onMove = drag::moveTo,
                            onEnd = {
                                // Dropped back on the gallery: nothing; else where its outline shows
                                if (drag.overGrid) drag.drop()
                                drag.end()
                            },
                            onCancel = drag::end,
                        )
                    },
            )
        }
        Text(stringResource(widget.title), fontWeight = FontWeight.SemiBold)
    }
}

/** The widget's [HomeWidget.Preview] at its real size, scaled down by [scale]. */
@Composable
internal fun WidgetPreview(
    widget: HomeWidget,
    span: CellSpan,
    state: HomeWidgetsState,
    scale: Float,
) {
    Box(
        propagateMinConstraints = true,
        modifier =
            Modifier
                .clip(RoundedCornerShape(18.dp * scale))
                .layout { measurable, _ ->
                    val width = GALLERY_PITCH.width(span.w).roundToPx()
                    val height = GALLERY_PITCH.height(span.h).roundToPx()
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
