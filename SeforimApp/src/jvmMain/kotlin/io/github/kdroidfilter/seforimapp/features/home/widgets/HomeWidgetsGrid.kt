package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

const val HOME_GRID_COLUMNS = 20
val HOME_GRID_CELL_HEIGHT = 135.dp
internal val GRID_GAP = 12.dp
internal val MAX_GRID_WIDTH = 1000.dp

/** Below this width every widget takes a whole row. */
private val COMPACT_GRID_WIDTH = 670.dp

internal fun GridSize.height(): Dp = HOME_GRID_CELL_HEIGHT * rows + GRID_GAP * (rows - 1).coerceAtLeast(0f)

/** Its width on a full-width grid, as the gallery previews it. */
internal fun GridSize.width(): Dp = (MAX_GRID_WIDTH + GRID_GAP) * columns / HOME_GRID_COLUMNS - GRID_GAP

/** Fills rows of [HOME_GRID_COLUMNS] columns with [widgets] in order, a widget that doesn't fit starting a new row. */
internal fun packRows(
    widgets: List<WidgetPlacement>,
    compact: Boolean,
): List<List<WidgetPlacement>> {
    if (compact) return widgets.map { listOf(it) }
    val rows = mutableListOf<MutableList<WidgetPlacement>>()
    for (widget in widgets) {
        val last = rows.lastOrNull()
        if (last != null && last.sumOf { it.grid.columns } + widget.grid.columns <= HOME_GRID_COLUMNS) {
            last += widget
        } else {
            rows += mutableListOf(widget)
        }
    }
    return rows
}

/**
 * Splits [width] (px, or grid columns) between widgets of these [columns], in proportion: a row that isn't full
 * stretches its widgets, so the grid never shows a hole whatever the user adds or removes.
 */
internal fun rowWidths(
    columns: List<Int>,
    width: Int,
): List<Int> {
    val total = columns.sum()
    var start = 0
    var used = 0
    return columns.map { c ->
        used += c
        // Cumulative rounding: the widths always add up to [width] exactly
        val end = width * used / total
        (end - start).also { start = end }
    }
}

/** A widget as the grid lays it out: its span (a whole row always adds up to [HOME_GRID_COLUMNS]) and height. */
@Immutable
internal data class GridCell(
    val placement: WidgetPlacement,
    val span: Int,
    val height: Dp,
)

/** Lays [widgets] out on a grid [gridWidth] wide: rows packed, stretched to be full, as tall as their tallest widget. */
internal fun gridCells(
    widgets: List<WidgetPlacement>,
    gridWidth: Dp,
): List<GridCell> {
    val compact = gridWidth < COMPACT_GRID_WIDTH
    val cellWidth = (gridWidth - GRID_GAP * (HOME_GRID_COLUMNS - 1)) / HOME_GRID_COLUMNS
    return packRows(widgets, compact).flatMap { row ->
        val spans = rowWidths(row.map { it.grid.columns }, HOME_GRID_COLUMNS)
        val height =
            row.indices.maxOf { i ->
                val width = cellWidth * spans[i] + GRID_GAP * (spans[i] - 1)
                maxOf(row[i].grid.height(), row[i].widget.heightAt(width) ?: 0.dp)
            }
        row.indices.map { i -> GridCell(row[i], spans[i], height) }
    }
}

/**
 * The Home page: [header] (the search) then the widgets, all one lazy grid of [HOME_GRID_COLUMNS] columns. Widgets
 * are moved with Reorderable, the others making room as one is dragged; the layout is saved when it's dropped.
 */
@Composable
fun HomeWidgetsGrid(
    state: HomeWidgetsState,
    widgets: List<WidgetPlacement>,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    showWidgets: Boolean = true,
    header: LazyGridScope.() -> Unit = {},
) {
    // The order while a widget is dragged, saved on drop; a new saved layout replaces it
    var live by remember(widgets) { mutableStateOf(widgets.filter { it.widget.isSupported }) }
    val reorderState =
        rememberReorderableLazyGridState(gridState) { from, to ->
            val fromIndex = live.indexOfFirst { it.widget.id == from.key }
            val toIndex = live.indexOfFirst { it.widget.id == to.key }
            if (fromIndex >= 0 && toIndex >= 0) {
                live = live.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            }
        }
    // Widgets this platform can't show keep their place in the save
    val save = { state.layout.save(live + widgets.filterNot { it.widget.isSupported }) }

    // A widget dragged in from the gallery gets its place in the grid while it's held there, the others making room
    val incoming = state.drag.newWidget?.takeIf { state.drag.overGrid }
    val shown =
        if (incoming == null) {
            live
        } else {
            val at = live.indexOfFirst { it.widget.id == state.drag.targetId }.takeIf { it >= 0 } ?: live.size
            live.toMutableList().apply { add(at, incoming) }
        }

    BoxWithConstraints(modifier) {
        val side = ((maxWidth - MAX_GRID_WIDTH) / 2).coerceAtLeast(0.dp)
        val cells = gridCells(shown, maxWidth - side * 2)
        LazyVerticalGrid(
            columns = GridCells.Fixed(HOME_GRID_COLUMNS),
            state = gridState,
            // Top: room for the "−" badges, which stick out of the first row
            contentPadding = PaddingValues(start = side, top = 8.dp, end = side, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(GRID_GAP),
            horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
        ) {
            header()
            if (showWidgets) {
                // The search sections were 16 dp apart and 32 dp above the widgets: keep that rhythm on the grid's gaps
                fullWidthItem { Spacer(Modifier.height(4.dp)) }
                items(cells, key = { it.placement.widget.id }, span = { GridItemSpan(it.span) }) { cell ->
                    if (cell.placement == incoming) {
                        DropPlaceholder(
                            Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .height(cell.height)
                                .onGloballyPositioned { state.drag.placeholderBounds = it.boundsInRoot() },
                        )
                        return@items
                    }
                    ReorderableItem(reorderState, key = cell.placement.widget.id) { dragging ->
                        WidgetFrame(
                            placement = cell.placement,
                            state = state,
                            dragging = dragging,
                            onDrop = save,
                            modifier = Modifier.fillMaxWidth().height(cell.height),
                        )
                    }
                }
                // Lets the last widgets scroll above the gallery panel
                if (state.editingWidgets) fullWidthItem { Spacer(Modifier.height(WIDGET_GALLERY_HEIGHT)) }
            }
        }
    }
}

/** An item across the whole grid, as the Home's search sections are ([gapAfter] adds to the grid's own gap). */
fun LazyGridScope.fullWidthItem(
    gapAfter: Dp = 0.dp,
    content: @Composable () -> Unit,
) = item(span = { GridItemSpan(maxLineSpan) }) {
    Box(Modifier.padding(bottom = gapAfter)) { content() }
}

/** Where a widget dragged in from the gallery will land: a dashed, empty card of its size. */
@Composable
private fun DropPlaceholder(modifier: Modifier = Modifier) {
    val color = JewelTheme.globalColors.borders.focused
    Box(
        modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .drawBehind {
                val radius = CornerRadius(18.dp.toPx())
                drawRoundRect(
                    color = color,
                    cornerRadius = radius,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
                )
            },
    )
}

/**
 * A widget dragged from the gallery ([newWidget] at [pointer]) and where the placed widgets sit, in root coordinates,
 * to find the one it's dropped on.
 */
internal class WidgetDrag {
    var targetId by mutableStateOf<String?>(null)
    var newWidget by mutableStateOf<WidgetPlacement?>(null)
    var pointer by mutableStateOf(Offset.Zero)

    /** Off the gallery: the grid shows where the widget would land. */
    var overGrid by mutableStateOf(false)
    val bounds = mutableMapOf<String, Rect>()
    var galleryBounds = Rect.Zero
    var placeholderBounds = Rect.Zero

    /** A placed widget being moved (by Reorderable), and the pointer moving it: the trash shows, to drop it in. */
    var movingId by mutableStateOf<String?>(null)
    var movePointer by mutableStateOf(Offset.Zero)
    var trashBounds by mutableStateOf(Rect.Zero)
    val overTrash: Boolean get() = movingId != null && trashBounds.contains(movePointer)

    fun widgetAt(point: Offset): String? = bounds.entries.firstOrNull { (_, rect) -> rect.contains(point) }?.key

    /**
     * Follows the pointer of a gallery drag. Over its own placeholder the target holds: the placeholder took that
     * widget's place, and letting go of it would send the placeholder away and back under the pointer.
     */
    fun moveTo(point: Offset) {
        pointer = point
        overGrid = !galleryBounds.contains(point)
        // Off every widget (the search above, a gap): the end of the grid
        if (!placeholderBounds.contains(point)) targetId = widgetAt(point)
    }

    fun end() {
        newWidget = null
        targetId = null
        overGrid = false
        placeholderBounds = Rect.Zero
    }
}
