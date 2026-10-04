package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.rememberCursorPositionProvider
import dev.nucleusframework.window.tao.TaoPointerIcons
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widgets_edit
import seforimapp.seforimapp.generated.resources.home_widgets_remove
import seforimapp.seforimapp.generated.resources.home_widgets_resize

/**
 * A widget on the grid: a right click opens its menu (resize, remove, edit widgets). Only in edit mode can it be
 * moved: the whole widget drags (its content inert), a "−" badge removes it, and a click or a move selects it for its
 * resize frame, as on Android: a handle on each side and corner resizes it cell by cell. Not [movable] (a window too
 * narrow for the grid), it can only be removed.
 */
@Composable
internal fun WidgetFrame(
    placement: WidgetPlacement,
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
    movable: Boolean = true,
    revealed: Boolean = true,
    picture: GraphicsLayer = rememberGraphicsLayer(),
    appearing: Boolean = false,
    onAppear: () -> Unit = {},
) {
    val widget = placement.widget
    val drag = state.drag
    var menuOpen by remember { mutableStateOf(false) }
    val editing = state.editingWidgets
    val selected = movable && editing && state.selectedWidget == widget.id
    val dragging = drag.movingId == widget.id
    val shape = RoundedCornerShape(18.dp)
    val grabIcon = if (dragging) TaoPointerIcons.Grabbing else TaoPointerIcons.Grab
    // Added (a click, an undo): popping in, as on iOS
    val appear = remember { Animatable(if (appearing) 0f else 1f) }
    val currentOnAppear by rememberUpdatedState(onAppear)
    LaunchedEffect(Unit) {
        if (appear.value < 1f) {
            appear.animateTo(1f, PopInSpec)
            currentOnAppear()
        }
    }
    // Lifted while moved; put down as it glides into its area, not at once
    val lift by animateFloatAsState(if (dragging) 1.03f else 1f, spring(stiffness = Spring.StiffnessMedium))
    val elevation by animateDpAsState(if (dragging) 16.dp else 0.dp, spring(stiffness = Spring.StiffnessMedium))

    // Moves the widget (the grid shows where it will land), then lets it go where it shows
    @Composable
    fun Modifier.moveHandle() =
        trackedDrag(
            onStart = {
                // Moved, it's the one selected: its resize frame shows as soon as it's put down
                state.selectedWidget = widget.id
                drag.startMove(widget.id, it)
            },
            onMove = { drag.pointer = it },
            onEnd = {
                if (drag.movingId == widget.id) drag.drop()
                drag.end()
            },
            onCancel = drag::end,
        )
    // A removed widget must not stay a drop target where it used to be
    DisposableEffect(widget.id) {
        onDispose {
            drag.bounds.remove(widget.id)
            if (drag.lifted?.id == widget.id) drag.lifted = null
        }
    }
    // Held or landing: drawn above the page from its live picture (see WidgetDrag.lifted), not here
    val lifted = dragging || widget.id in drag.landing
    SideEffect {
        if (lifted && drag.lifted?.id != widget.id) {
            drag.lifted = Lifted(widget.id, picture)
        } else if (!lifted && drag.lifted?.id == widget.id) {
            drag.lifted = null
        }
    }

    // Min constraints reach the widget, so it fills its cell
    Box(
        propagateMinConstraints = true,
        modifier =
            modifier
                .drawWithContent {
                    picture.record { this@drawWithContent.drawContent() }
                    if (drag.lifted?.id != widget.id || !lifted) drawLayer(picture)
                }.onGloballyPositioned { coordinates ->
                    drag.lifted?.takeIf { it.id == widget.id }?.let {
                        it.at = coordinates.positionInRoot()
                        it.size = coordinates.size
                    }
                }.testTag("widget-${widget.id}")
                // Unclipped: a widget scrolled half out of view is still grabbed from its real top
                .onGloballyPositioned { drag.bounds[widget.id] = Rect(it.positionInRoot(), it.size.toSize()) }
                // Initial pass: seen before the widget's own handlers, which may consume the press
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                menuOpen = true
                            }
                        }
                    }
                }.graphicsLayer {
                    scaleX = lift * popIn(appear.value)
                    scaleY = lift * popIn(appear.value)
                    alpha = appear.value.coerceIn(0f, 1f)
                }
                // Not clipped: the "−" badge and the resize handles stick out of the widget, lifted or not
                .shadow(elevation, shape, clip = false),
    ) {
        // Not yet in (a Home composed from scratch, see rememberRevealedWidgets): its place, empty
        if (revealed) widget.Content(state, Modifier) else PanelCard(Modifier.fillMaxSize()) {}
        // matchParentSize: the handles take the widget's size instead of passing it their min constraints
        Box(Modifier.matchParentSize()) {
            if (editing) {
                // Swallows clicks meant for the widget: a click selects it, a press and move moves it
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (movable) Modifier.pointerHoverIcon(grabIcon).moveHandle() else Modifier)
                        .pointerInput(widget.id) { detectTapGestures { if (movable) state.selectedWidget = widget.id } },
                )
                if (selected) ResizeFrame(widget.id, drag, shape)
                // On its corner; inside it while selected, clear of the resize frame's corner handle
                val badgeOffset = if (selected) 12.dp else (-8).dp
                RemoveBadge(
                    onClick = { state.removeWidget(widget) },
                    modifier = Modifier.align(Alignment.TopStart).offset(badgeOffset, badgeOffset).testTag("widget-remove-${widget.id}"),
                )
            }
        }
        if (menuOpen) {
            WidgetMenu(placement, state, movable, onDismiss = { menuOpen = false })
        }
    }
}

@Composable
private fun RemoveBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.home_widgets_remove)
    Box(
        modifier
            .size(24.dp)
            .shadow(3.dp, CircleShape)
            .background(Color(0xFF8E8E93), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .pointerHoverIcon(PointerIcon.Default),
        contentAlignment = Alignment.Center,
    ) {
        // The macOS "−": a white bar, drawn so no font can miss it
        Box(Modifier.size(width = 10.dp, height = 2.dp).background(Color.White, RoundedCornerShape(1.dp)))
    }
}

@Composable
private fun WidgetMenu(
    placement: WidgetPlacement,
    state: HomeWidgetsState,
    movable: Boolean,
    onDismiss: () -> Unit,
) {
    val widget = placement.widget
    // Resource strings must be resolved outside MenuScope
    val resizeLabel = stringResource(Res.string.home_widgets_resize)
    val removeLabel = stringResource(Res.string.home_widgets_remove)
    val editLabel = stringResource(Res.string.home_widgets_edit)
    val ownItems = widget.menuItems(state)
    PopupMenu(
        onDismissRequest = {
            onDismiss()
            true
        },
        popupPositionProvider = rememberCursorPositionProvider(),
    ) {
        // Its own items first, as macOS's "Edit widget", then what every widget offers
        if (ownItems.isNotEmpty()) {
            widgetItems(ownItems, onDismiss)
            separator()
        }
        if (movable && widget.minSpan != widget.maxSpan) {
            selectableItem(
                selected = false,
                iconKey = AllIconsKeys.General.FitContent,
                onClick = {
                    onDismiss()
                    // As on Android, its resize frame
                    state.editingWidgets = true
                    state.selectedWidget = widget.id
                },
            ) { Text(resizeLabel) }
        }
        selectableItem(
            selected = false,
            iconKey = AllIconsKeys.General.Remove,
            onClick = {
                onDismiss()
                state.removeWidget(widget)
            },
        ) { Text(removeLabel) }
        if (!state.editingWidgets) {
            selectableItem(
                selected = false,
                iconKey = AllIconsKeys.Actions.Edit,
                onClick = {
                    onDismiss()
                    state.editingWidgets = true
                },
            ) { Text(editLabel) }
        }
    }
}

private fun MenuScope.widgetItems(
    items: List<WidgetMenuItem>,
    onDismiss: () -> Unit,
) {
    for (item in items) {
        if (item.children.isNotEmpty()) {
            submenu(iconKey = item.icon, submenu = { widgetItems(item.children, onDismiss) }) { Text(item.label) }
        } else {
            selectableItem(
                selected = false,
                iconKey = if (item.checked == true) AllIconsKeys.Actions.Checked else item.icon,
                onClick = {
                    onDismiss()
                    item.onClick()
                },
            ) { Text(item.label) }
        }
    }
}

/**
 * Android's widget resize frame: an outline, and a handle on each side and each corner that drags that side, or both
 * a corner's, cell by cell (the grid works the size out, see [HomeWidgetsGrid]).
 */
@Composable
private fun BoxScope.ResizeFrame(
    id: String,
    drag: WidgetDrag,
    shape: RoundedCornerShape,
) {
    val accent = JewelTheme.globalColors.outlines.focused
    Box(Modifier.matchParentSize().border(2.dp, accent, shape))
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // A corner's diagonal, as it is on screen: the top start corner is the top left one left to right, top right right
    // to left
    val falling = PointerIcon(java.awt.Cursor(java.awt.Cursor.NW_RESIZE_CURSOR))
    val rising = PointerIcon(java.awt.Cursor(java.awt.Cursor.NE_RESIZE_CURSOR))
    val handles =
        listOf(
            ResizeHandle(listOf(ResizeEdge.START), Alignment.CenterStart, TaoPointerIcons.ResizeEastWest),
            ResizeHandle(listOf(ResizeEdge.END), Alignment.CenterEnd, TaoPointerIcons.ResizeEastWest),
            ResizeHandle(listOf(ResizeEdge.TOP), Alignment.TopCenter, TaoPointerIcons.ResizeNorthSouth),
            ResizeHandle(listOf(ResizeEdge.BOTTOM), Alignment.BottomCenter, TaoPointerIcons.ResizeNorthSouth),
            ResizeHandle(listOf(ResizeEdge.START, ResizeEdge.TOP), Alignment.TopStart, if (rtl) rising else falling),
            ResizeHandle(listOf(ResizeEdge.END, ResizeEdge.TOP), Alignment.TopEnd, if (rtl) falling else rising),
            ResizeHandle(listOf(ResizeEdge.START, ResizeEdge.BOTTOM), Alignment.BottomStart, if (rtl) falling else rising),
            ResizeHandle(listOf(ResizeEdge.END, ResizeEdge.BOTTOM), Alignment.BottomEnd, if (rtl) rising else falling),
        )
    for (handle in handles) {
        // Half out of the widget, on its side or corner
        val outward =
            DpOffset(
                if (ResizeEdge.START in handle.edges) {
                    (-9).dp
                } else if (ResizeEdge.END in handle.edges) {
                    9.dp
                } else {
                    0.dp
                },
                if (ResizeEdge.TOP in handle.edges) {
                    (-9).dp
                } else if (ResizeEdge.BOTTOM in handle.edges) {
                    9.dp
                } else {
                    0.dp
                },
            )
        Box(
            Modifier
                .align(handle.alignment)
                .offset(outward.x, outward.y)
                .testTag("widget-resize-$id-${handle.edges.joinToString("-") { it.name.lowercase() }}")
                .size(18.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape)
                .border(2.dp, accent, CircleShape)
                .pointerHoverIcon(handle.cursor)
                .trackedDrag(
                    onStart = { drag.startResize(id, handle.edges, it) },
                    onMove = { drag.pointer = it },
                    onEnd = {
                        if (drag.resizing != null) drag.drop()
                        drag.end()
                    },
                    onCancel = drag::end,
                ),
        )
    }
}

/** A resize frame's handle: the sides it moves, where it sits on the frame, its cursor. */
private class ResizeHandle(
    val edges: List<ResizeEdge>,
    val alignment: Alignment,
    val cursor: PointerIcon,
)

/** A drag on this element, reported in root coordinates (where the grid and the trash are). */
@Composable
internal fun Modifier.trackedDrag(
    onStart: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
): Modifier {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun root(local: Offset) = coordinates?.takeIf { it.isAttached }?.localToRoot(local)
    return onGloballyPositioned { coordinates = it }
        .pointerInput(Unit) { cancellableDrag(::root, onStart, onMove, onEnd, onCancel) }
}

/**
 * detectDragGestures, [onCancel] included when the element goes away mid-drag (edit mode closed with Escape, the
 * window narrowed): detectDragGestures then just stops, calling neither its end nor its cancel, and the drag would
 * stay on, the widget held where the pointer left it.
 */
internal suspend fun androidx.compose.ui.input.pointer.PointerInputScope.cancellableDrag(
    root: (Offset) -> Offset?,
    onStart: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
) {
    var dragging = false
    try {
        detectDragGestures(
            onDragStart = { start ->
                root(start)?.let {
                    dragging = true
                    onStart(it)
                }
            },
            onDrag = { change, _ ->
                change.consume()
                root(change.position)?.let(onMove)
            },
            onDragEnd = {
                dragging = false
                onEnd()
            },
            onDragCancel = {
                dragging = false
                onCancel()
            },
        )
    } finally {
        if (dragging) onCancel()
    }
}

/** A slightly bouncy spring, as iOS's widgets popping in. */
internal val PopInSpec = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)

/** The scale of something popping in at [progress] (0 to 1, past 1 as the spring bounces). */
internal fun popIn(progress: Float) = 0.6f + 0.4f * progress
