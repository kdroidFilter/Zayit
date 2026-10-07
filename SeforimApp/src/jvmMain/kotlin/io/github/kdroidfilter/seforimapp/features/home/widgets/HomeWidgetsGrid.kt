package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.Ref
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.jewel.foundation.theme.JewelTheme
import kotlin.math.roundToInt

/**
 * How long a widget rests over an occupied area before the others make room: Launcher3's REORDER_TIMEOUT is 650 ms,
 * slow under a mouse, which aims and rests far quicker than a finger.
 */
private const val REORDER_TIMEOUT_MS = 200L

/**
 * The Home page: [header] (the search) then the widgets, on a grid of cells as on Android's home screen. A widget keeps
 * the area the user gave it: dragged, it goes to the area nearest where it's let go, the widgets in the way making
 * room; its resize frame (edit mode) resizes it cell by cell. Nothing moves or resizes on its own.
 */
@Composable
fun HomeWidgetsGrid(
    state: HomeWidgetsState,
    widgets: List<WidgetPlacement>,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.() -> Unit = {},
) {
    AutoScroll(state, scrollState)
    BoxWithConstraints(modifier) {
        val side = ((maxWidth - MAX_GRID_WIDTH) / 2).coerceAtLeast(0.dp)
        Column(
            modifier =
                Modifier
                    .onGloballyPositioned { state.drag.viewport = it.boundsInRoot() }
                    .verticalScroll(scrollState)
                    // Top: room for the "−" badges, which stick out of the first row
                    .padding(start = side, top = 8.dp, end = side, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(GRID_GAP),
        ) {
            header()
            // The search sections were 16 dp apart and 32 dp above the widgets: keep that rhythm
            Spacer(Modifier.height(4.dp))
            WidgetCells(state, widgets)
            // Lets the last widgets scroll above the gallery panel
            if (state.editingWidgets) Spacer(Modifier.height(WIDGET_GALLERY_HEIGHT))
        }
    }
}

/** How close to the page's top or bottom a drag scrolls it, and how fast at most (dp a frame) right at the edge. */
private val AUTO_SCROLL_BAND = 64.dp
private val AUTO_SCROLL_SPEED = 14.dp

/**
 * Reorderable's auto-scroll: while a widget is dragged near the top or the bottom of the page (in edit mode, of what
 * the gallery leaves of it), the page scrolls, the faster the nearer the edge.
 */
@Composable
private fun AutoScroll(
    state: HomeWidgetsState,
    scrollState: ScrollState,
) {
    val drag = state.drag
    val density = LocalDensity.current
    val dragging = drag.movingId != null || drag.resizing != null || (drag.newWidget != null && drag.overGrid)
    LaunchedEffect(dragging, state.editingWidgets) {
        if (!dragging) return@LaunchedEffect
        val band = with(density) { AUTO_SCROLL_BAND.toPx() }
        val speed = with(density) { AUTO_SCROLL_SPEED.toPx() }
        val covered = if (state.editingWidgets) with(density) { WIDGET_GALLERY_HEIGHT.toPx() } else 0f

        fun towards(): Float {
            val view = drag.viewport
            val y = drag.pointer.y
            val bottom = view.bottom - covered
            return when {
                y < view.top + band -> -(view.top + band - y) / band
                y > bottom - band -> (y - (bottom - band)) / band
                else -> 0f
            }.coerceIn(-1f, 1f)
        }
        // Frames only while it scrolls: out of the bands, or the page at its end, nothing keeps the clock ticking
        snapshotFlow { drag.pointer }.collectLatest {
            while (true) {
                val direction = towards()
                if (direction == 0f) break
                var scrolled = 0f
                withFrameNanos { scrolled = scrollState.dispatchRawDelta(direction * speed) }
                if (scrolled == 0f) break
            }
        }
    }
}

/** A section across the whole page, as the Home's search sections are ([gapAfter] adds to the page's own gap). */
@Composable
fun FullWidthSection(
    gapAfter: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(bottom = gapAfter)) { content() }
}

@Composable
private fun WidgetCells(
    state: HomeWidgetsState,
    widgets: List<WidgetPlacement>,
) {
    val revealed = rememberRevealedWidgets(widgets)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < COMPACT_GRID_WIDTH) {
            CompactWidgets(state, widgets, maxWidth, revealed)
        } else {
            CellArea(state, widgets, maxWidth, revealed)
        }
    }
}

/**
 * The widgets of a Home composed from scratch (a new tab) come in after the tab's opening animation, one per frame, in
 * reading order: composing and first drawing them all at once (their 3D views, calendars, pictures) took some 150 ms of
 * the animation's frames. Until then each keeps its place, empty. Null once all are in: every widget shows.
 */
@Composable
private fun rememberRevealedWidgets(widgets: List<WidgetPlacement>): Set<String>? {
    val order = widgets.sortedWith(compareBy({ it.cell.y }, { it.cell.x })).map { it.widget.id }
    val currentOrder by rememberUpdatedState(order)
    var count by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(WIDGET_REVEAL_DELAY_MS)
        while (count < currentOrder.size) {
            withFrameNanos { }
            count++
        }
        done = true
    }
    return if (done) null else order.take(count).toSet()
}

/** The tab strip's opening animation (TabsView), which the widgets wait out. */
private const val WIDGET_REVEAL_DELAY_MS = 200L

/** Too narrow a window for the grid: the widgets one per row, in reading order, as they are (no moving them). */
@Composable
private fun CompactWidgets(
    state: HomeWidgetsState,
    widgets: List<WidgetPlacement>,
    width: Dp,
    revealed: Set<String>?,
) {
    val pitch = CellPitch(width)
    // No grid to place it on: a widget let go from the gallery is added as a click would
    SideEffect { state.drag.drop = { state.drag.newWidget?.let(state.layout::add) } }
    Column(verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
        widgets
            .filter { it.widget.isSupported }
            .sortedWith(compareBy({ it.cell.y }, { it.cell.x }))
            .forEach { placement ->
                val height = maxOf(pitch.height(placement.cell.h), placement.widget.heightAt(width) ?: 0.dp)
                WidgetFrame(
                    placement,
                    state,
                    movable = false,
                    revealed = revealed == null || placement.widget.id in revealed,
                    modifier = Modifier.fillMaxWidth().height(height),
                )
            }
    }
}

/**
 * The grid of cells, and Launcher3's Workspace.onDragOver for a widget moved or one from the gallery alike:
 *  - the drop area is the nearest to the held widget's visual centre, whatever is there;
 *  - its outline shows there at once; occupied, an alarm is set (a new area resets it), and when it goes off the
 *    widgets in the way make room (CellLayout.performReorder);
 *  - let go, the widget goes to the area nearest then, the widgets in the way moved.
 * Every reorder is worked out from the layout as it was, never from the previous one. A resize frame's handle resizes
 * the widget cell by cell, the widgets in the way making room the same way.
 */
@Composable
private fun CellArea(
    state: HomeWidgetsState,
    widgets: List<WidgetPlacement>,
    gridWidth: Dp,
    revealed: Set<String>?,
) {
    val drag = state.drag
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pitch = CellPitch(gridWidth)
    // The widgets shown, and so the cells taken: one this platform can't show leaves its area free, kept in the save
    val hidden = widgets.filterNot { it.widget.isSupported }
    // The layout saved on a drop, shown until the saved one comes back
    var layout by remember(widgets) { mutableStateOf(widgets - hidden.toSet()) }

    /** A point in root pixels, in dp from the grid's top-start corner. */
    fun toGrid(point: Offset): Offset {
        val local = with(density) { (point - drag.gridOrigin).let { Offset(it.x.toDp().value, it.y.toDp().value) } }
        return if (rtl) local.copy(x = gridWidth.value - local.x) else local
    }

    /** The widget held (moved, or from the gallery while over the grid), and its visual centre. */
    fun held(): Pair<WidgetPlacement, Offset>? {
        layout.firstOrNull { it.widget.id == drag.movingId }?.let { moved ->
            val size = with(density) { Offset(pitch.width(moved.cell.w).toPx(), pitch.height(moved.cell.h).toPx()) }
            return moved to toGrid(drag.pointer - drag.grab + size / 2f)
        }
        val added = drag.newWidget?.takeIf { drag.overGrid } ?: return null
        val span = added.defaultSpan
        return WidgetPlacement(added, CellRect(0, 0, span.w, span.h)) to toGrid(drag.pointer)
    }

    fun dropArea(
        held: WidgetPlacement,
        centre: Offset,
    ) = layout.nearestArea(centre, held.cell.span, pitch, except = held.widget.id, ignoreOccupied = true)

    // Launcher3's mReorderAlarm: each setting is a new one, null cancels it
    var alarm by remember { mutableStateOf<Any?>(null) }

    // When it goes off: the widgets in the way make room at the area nearest then
    fun onAlarm() {
        val (held, centre) = held() ?: return
        val area = dropArea(held, centre)
        drag.preview = layout.reorder(held.copy(cell = area), pitch)
        drag.outline = area
    }

    fun onDragOver() {
        drag.resizing?.let { resize ->
            resizeTo(resize, layout, pitch, density, rtl)
            return
        }
        val (held, centre) =
            held() ?: run {
                alarm = null
                drag.lastReorder = null
                drag.preview = null
                drag.outline = null
                return
            }
        val area = dropArea(held, centre)
        // Where it will land, at once, whatever is there: the others make room once it rests (the alarm)
        drag.outline = area
        if (layout.isVacant(area, except = held.widget.id)) {
            // Room there: the others back where they were; an occupied area comes back to its alarm
            drag.preview = null
            drag.lastReorder = null
        } else if (area != drag.lastReorder) {
            // The outline is on it, so the others make room for it once it rests, however far the centre is
            drag.lastReorder = area
            alarm = Any()
        }
    }
    // Started again for a new layout (removed, added, saved), grid width or direction, so they never work from a
    // previous one (a function reference kept with rememberUpdatedState isn't enough: the compiler memoizes it)
    LaunchedEffect(alarm, layout, pitch, rtl) {
        if (alarm == null) return@LaunchedEffect
        delay(REORDER_TIMEOUT_MS)
        onAlarm()
    }
    LaunchedEffect(drag, layout, pitch, rtl) {
        snapshotFlow { listOf(drag.pointer, drag.gridOrigin, drag.movingId, drag.newWidget, drag.overGrid, drag.resizing) }
            .collect { onDragOver() }
    }
    // Gone (the window too narrow, the widgets hidden), its drop must not act on a layout it no longer shows
    DisposableEffect(drag) { onDispose { drag.drop = {} } }
    SideEffect {
        drag.drop = drop@{
            val final =
                if (drag.resizing != null) {
                    drag.preview
                } else {
                    held()?.let { (held, centre) -> layout.reorder(held.copy(cell = dropArea(held, centre)), pitch) }
                }
            drag.droppedId = drag.newWidget?.id
            alarm = null
            drag.preview = null
            drag.outline = null
            drag.lastReorder = null
            if (final == null) return@drop
            layout = final
            state.layout.save(final + hidden)
        }
    }

    // Each widget's live picture, kept here rather than by the widget: one removed is still seen leaving from it
    val graphics = LocalGraphicsContext.current
    val pictures = remember { mutableMapOf<String, GraphicsLayer>() }
    DisposableEffect(graphics) {
        onDispose {
            pictures.values.forEach(graphics::releaseGraphicsLayer)
            pictures.clear()
        }
    }
    // As on iOS: a widget removed shrinks away where it was, one added (a click, an undo) pops in; not one put down
    // from the gallery, which lands from where it was let go
    val leaving = remember { mutableStateListOf<WidgetPlacement>() }
    val appearing = remember { mutableStateSetOf<String>() }
    val before = remember { Ref<List<WidgetPlacement>>() }
    remember(layout) {
        before.value?.let { old ->
            val now = layout.map { it.widget.id }.toSet()
            leaving += old.filter { it.widget.id !in now && it.widget.id in pictures }
            leaving.removeAll { it.widget.id in now }
            for (added in layout) {
                if (old.none { it.widget.id == added.widget.id } && added.widget.id != drag.droppedId) appearing += added.widget.id
            }
        }
        before.value = layout
    }

    val shown = drag.preview ?: layout
    val outline = drag.outline?.takeIf { drag.movingId != null || drag.newWidget != null }
    val rows = maxOf(shown.bottom(), outline?.bottom ?: 0)
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (rows == 0) 0.dp else pitch.height(rows))
            .onGloballyPositioned { drag.gridOrigin = it.positionInRoot() }
            .pointerInput(Unit) { detectTapGestures { state.selectedWidget = null } },
    ) {
        outline?.let { DropOutline(Modifier.onCell(it, pitch, gridWidth)) }
        for (gone in leaving.toList()) {
            val id = gone.widget.id
            key("leaving-$id") {
                Leaving(
                    picture = pictures.getValue(id),
                    onLeave = {
                        leaving.remove(gone)
                        // Unless it came back meanwhile (an undo), and draws there again
                        if (layout.none { it.widget.id == id }) pictures.remove(id)?.let(graphics::releaseGraphicsLayer)
                    },
                    modifier = Modifier.onCell(gone.cell, pitch, gridWidth).testTag("widget-leaving-$id"),
                )
            }
        }
        shown.forEach { placement ->
            val id = placement.widget.id
            key(id) {
                WidgetFrame(
                    placement = placement,
                    state = state,
                    revealed = revealed == null || id in revealed,
                    picture = pictures.getOrPut(id) { graphics.createGraphicsLayer() },
                    appearing = id in appearing,
                    onAppear = { appearing -= id },
                    modifier =
                        Modifier.onCell(
                            cell = placement.cell,
                            pitch = pitch,
                            gridWidth = gridWidth,
                            held = drag.movingId == id,
                            resized = drag.resizing?.id == id,
                            onLanding = { landing -> if (landing) drag.landing += id else drag.landing -= id },
                        ) {
                            // Where the pointer holds it, in the grid's own (start-based) coordinates
                            val topLeft = drag.pointer - drag.grab - drag.gridOrigin
                            val width = pitch.width(placement.cell.w).roundToPx()
                            val x = if (rtl) gridWidth.roundToPx() - topLeft.x.roundToInt() - width else topLeft.x.roundToInt()
                            IntOffset(x, topLeft.y.roundToInt())
                        },
                )
            }
        }
    }
}

/** A resize frame's handle at the drag's pointer: the widget's area for it, cell by cell, the others making room. */
private fun resizeTo(
    resize: Resize,
    layout: List<WidgetPlacement>,
    pitch: CellPitch,
    density: Density,
    rtl: Boolean,
) {
    val drag = resize.drag
    val placement = layout.firstOrNull { it.widget.id == resize.id } ?: return
    val moved = with(density) { (drag.pointer - resize.from).let { Offset(it.x.toDp().value, it.y.toDp().value) } }
    // Launcher3's AppWidgetResizeFrame: the handle's travel, in cells, rounded; a corner moves both its sides, the
    // width first, as the rows a widget needs may depend on it
    val across = ((if (rtl) -moved.x else moved.x) / pitch.x).roundToInt()
    val down = (moved.y / pitch.y).roundToInt()
    val area =
        resize.edges.sortedBy { it == ResizeEdge.TOP || it == ResizeEdge.BOTTOM }.fold(placement.cell) { cell, edge ->
            val cells = if (edge == ResizeEdge.START || edge == ResizeEdge.END) across else down
            placement.widget.resized(cell, edge, cells, pitch)
        }
    drag.preview = layout.reorder(placement.copy(cell = area), pitch)
}

private val PlacementSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)

/** A widget let go into its area: brisker than the others making room, about a quarter of a second. */
private val LandingSpec = spring(stiffness = Spring.StiffnessMedium, visibilityThreshold = IntOffset.VisibilityThreshold)

private const val FRAME_NANOS = 16_666_667L

/**
 * Puts a widget on [cell], gliding there when its area changes (the others making room, a drop); while [held], at
 * [heldAt], under the pointer, and from there into its area once let go. A new [gridWidth] (the window resized) puts
 * it there at once, as being [resized] does: its size changes at once, so its start must too, or a side dragged
 * towards the start would first grow it the other way, then glide back.
 */
@Composable
private fun Modifier.onCell(
    cell: CellRect,
    pitch: CellPitch,
    gridWidth: Dp,
    held: Boolean = false,
    resized: Boolean = false,
    onLanding: (landing: Boolean) -> Unit = {},
    heldAt: Density.() -> IntOffset = { IntOffset.Zero },
): Modifier {
    val density = LocalDensity.current
    val target = with(density) { IntOffset((cell.x * pitch.x).dp.roundToPx(), (cell.y * pitch.y).dp.roundToPx()) }
    val position = remember { Animatable(target, IntOffset.VectorConverter) }
    val letGo = remember { Ref<IntOffset>() }
    var placedFor by remember { mutableStateOf(gridWidth) }
    // Let go, gliding into its area: where it shows (above the widgets making room, below one held)
    var landing by remember { mutableStateOf<IntOffset?>(null) }
    val currentOnLanding by rememberUpdatedState(onLanding)
    LaunchedEffect(target, held, gridWidth) {
        if (held) return@LaunchedEffect
        if (gridWidth != placedFor || resized) {
            placedFor = gridWidth
            // Let go as the window was resized: on its area at once, no longer where it was let go
            letGo.value = null
            position.snapTo(target)
            return@LaunchedEffect
        }
        val from = letGo.value
        letGo.value = null
        if (from == null) {
            position.animateTo(target, PlacementSpec)
            return@LaunchedEffect
        }
        // Landing at once: as of the frame it was let go in, not from the next one at rest (an animation's first frame
        // only takes its start time, which held it still two frames), and briskly, as Android's drop
        val animation = TargetBasedAnimation(LandingSpec, IntOffset.VectorConverter, from, target)
        landing = from
        currentOnLanding(true)
        try {
            var start = -1L
            var done = false
            while (!done) {
                withFrameNanos { now ->
                    if (start < 0) start = now - FRAME_NANOS
                    landing = animation.getValueFromNanos(now - start)
                    done = animation.isFinishedFromNanos(now - start)
                }
            }
            position.snapTo(target)
        } finally {
            landing = null
            currentOnLanding(false)
        }
    }
    // Placed this very frame, before the effect above catches up: resized, on its area; just let go, where it was let
    // go (its animation still holds where it started, which would flash for a frame). One above another: the widget
    // held or resized, then the one landing, then the ones making room, as they may cross
    val z =
        when {
            held || resized -> 3f
            landing != null || letGo.value != null -> 2f
            position.isRunning -> 1f
            else -> 0f
        }
    return zIndex(z)
        .offset {
            when {
                held -> heldAt().also { letGo.value = it }
                resized -> target
                else -> letGo.value ?: landing ?: position.value
            }
        }.size(pitch.width(cell.w), pitch.height(cell.h))
}

/** A widget just removed, from its last [picture]: shrinking and fading where it was, as on iOS, then [onLeave]. */
@Composable
private fun Leaving(
    picture: GraphicsLayer,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val left = remember { Animatable(1f) }
    val currentOnLeave by rememberUpdatedState(onLeave)
    LaunchedEffect(Unit) {
        left.animateTo(0f, tween(LEAVE_MS, easing = FastOutLinearInEasing))
        currentOnLeave()
    }
    Box(
        modifier
            .graphicsLayer {
                val scale = 0.4f + 0.6f * left.value
                scaleX = scale
                scaleY = scale
                alpha = left.value
            }.drawBehind { drawLayer(picture) },
    )
}

private const val LEAVE_MS = 240

/** Where a dragged widget will land: a dashed, empty card of its size. */
@Composable
private fun DropOutline(modifier: Modifier = Modifier) {
    val color = JewelTheme.globalColors.borders.focused
    Box(
        modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .drawBehind {
                drawRoundRect(
                    color = color,
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
                )
            },
    )
}

/**
 * A widget's live picture ([layer], recorded as it draws: drawn elsewhere, it shows each new drawing, as a RenderNode)
 * and where it is ([at], in root coordinates, and [size]).
 */
internal class Lifted(
    val id: String,
    val layer: GraphicsLayer,
) {
    var at by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(IntSize.Zero)
}

/** A resize frame's handle being dragged: which widget, which sides (two for a corner), from where. */
internal class Resize(
    val drag: WidgetDrag,
    val id: String,
    val edges: List<ResizeEdge>,
    val from: Offset,
)

/**
 * A widget being dragged: moved ([movingId]), taken from the gallery ([newWidget]) or resized ([resizing]), at
 * [pointer]; and the grid's answer, Launcher3's drag-over state: the reordered layout it shows ([preview]) and where
 * the widget will land ([outline]).
 */
internal class WidgetDrag {
    var newWidget by mutableStateOf<HomeWidget?>(null)

    /** Off the gallery: the grid shows where the widget would land. */
    var overGrid by mutableStateOf(false)
    var movingId by mutableStateOf<String?>(null)
    var resizing by mutableStateOf<Resize?>(null)
    var pointer by mutableStateOf(Offset.Zero)

    /** Where the pointer holds the moved widget, from its top-left corner. */
    var grab = Offset.Zero

    val bounds = mutableMapOf<String, Rect>()
    var galleryBounds = Rect.Zero

    /** The grid's top-left corner, in root coordinates: it moves as the page scrolls, under a still pointer too. */
    var gridOrigin by mutableStateOf(Offset.Zero)

    /** The scrolling page, in root coordinates, for the auto-scroll near its edges. */
    var viewport = Rect.Zero

    /** Widgets let go and gliding into their area: still drawn above the page, as while held. */
    val landing = mutableStateSetOf<String>()

    /**
     * The widget held or landing, drawn above the whole page (Android's DragLayer), not in the scrolling grid: that
     * clips what leaves it, so a widget dragged over the rest of the app would vanish, and reappear at its edge.
     */
    var lifted by mutableStateOf<Lifted?>(null)

    var preview by mutableStateOf<List<WidgetPlacement>?>(null)
    var outline by mutableStateOf<CellRect?>(null)

    /** Launcher3's mLastReorderX/Y: the area last reordered for. */
    var lastReorder: CellRect? = null

    /** Lets the widget go where it shows, and saves the layout (set by the grid). */
    var drop: () -> Unit = {}

    /** The widget last put down from the gallery: it lands, it doesn't pop in as one added by a click. */
    var droppedId: String? = null

    fun startMove(
        id: String,
        pointer: Offset,
    ) {
        grab = pointer - (bounds[id]?.topLeft ?: pointer)
        this.pointer = pointer
        movingId = id
    }

    fun startAdd(
        widget: HomeWidget,
        pointer: Offset,
    ) {
        newWidget = widget
        moveTo(pointer)
    }

    fun startResize(
        id: String,
        edges: List<ResizeEdge>,
        pointer: Offset,
    ) {
        this.pointer = pointer
        resizing = Resize(this, id, edges, pointer)
    }

    /** Follows the pointer of a gallery drag. */
    fun moveTo(point: Offset) {
        pointer = point
        overGrid = !galleryBounds.contains(point)
    }

    fun end() {
        newWidget = null
        overGrid = false
        movingId = null
        resizing = null
    }
}
