package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

// The Home grid, as Android's home screen (Launcher3's CellLayout): cells of a fixed grid, each widget on an area of
// them that it keeps, holes and all; nothing ever resizes or moves on its own. The grid has HOME_GRID_COLUMNS columns
// and as many rows as its widgets need.

const val HOME_GRID_COLUMNS = 20

/** One row of cells: a widget [CellSpan.h] rows tall is that many of these and the gaps between them (4 rows: the zmanim's 288 dp). */
val HOME_GRID_ROW_HEIGHT = 63.dp
internal val GRID_GAP = 12.dp
internal val MAX_GRID_WIDTH = 1000.dp

/** Below this width the grid is too narrow to place widgets: they're shown one per row, in reading order. */
internal val COMPACT_GRID_WIDTH = 670.dp

/** A size in cells: [w] columns by [h] rows. */
@Immutable
data class CellSpan(
    val w: Int,
    val h: Int,
)

/** An area of the grid: [w] by [h] cells from column [x] (from the start edge) and row [y] (from the top). */
@Immutable
data class CellRect(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
) {
    val right get() = x + w
    val bottom get() = y + h
    val span get() = CellSpan(w, h)

    fun overlaps(other: CellRect) = x < other.right && other.x < right && y < other.bottom && other.y < bottom

    fun at(
        x: Int,
        y: Int,
    ) = copy(x = x, y = y)
}

/** A grid [gridWidth] wide, in dp: how far apart its columns and rows are, a cell plus a gap. */
@Immutable
internal data class CellPitch(
    val x: Float,
    val y: Float,
) {
    constructor(gridWidth: Dp) : this((gridWidth.value + GRID_GAP.value) / HOME_GRID_COLUMNS, HOME_GRID_ROW_HEIGHT.value + GRID_GAP.value)

    fun width(w: Int) = (w * x - GRID_GAP.value).dp

    fun height(h: Int) = (h * y - GRID_GAP.value).dp

    /** The centre of [rect], in dp from the grid's top-start corner. */
    fun centre(rect: CellRect) = Offset((rect.x + rect.w / 2f) * x - GRID_GAP.value / 2, (rect.y + rect.h / 2f) * y - GRID_GAP.value / 2)
}

/** The rows a widget [w] columns wide needs at least: its [HomeWidget.minSpan], or more when its content asks. */
internal fun HomeWidget.minRows(
    w: Int,
    pitch: CellPitch,
): Int {
    val content = heightAt(pitch.width(w)) ?: return minSpan.h
    // A hair of slack: 282 dp is 4 rows exactly, not 4.0000001
    return maxOf(minSpan.h, ceil((content.value + GRID_GAP.value) / pitch.y - 0.01f).toInt())
}

/** [rect] within the widget's limits and the grid's columns, its start kept. */
internal fun HomeWidget.clamp(
    rect: CellRect,
    pitch: CellPitch,
): CellRect {
    val w = rect.w.coerceIn(minSpan.w, minOf(maxSpan.w, HOME_GRID_COLUMNS))
    val h = rect.h.coerceIn(minRows(w, pitch), maxOf(maxSpan.h, minRows(w, pitch)))
    return CellRect(rect.x.coerceIn(0, HOME_GRID_COLUMNS - w), rect.y.coerceAtLeast(0), w, h)
}

/** Whether [rect] is on the grid and free of every widget but [except]. */
internal fun List<WidgetPlacement>.isVacant(
    rect: CellRect,
    except: String? = null,
) = rect.x >= 0 &&
    rect.y >= 0 &&
    rect.right <= HOME_GRID_COLUMNS &&
    none { it.widget.id != except && it.cell.overlaps(rect) }

/** The first row below every widget. */
internal fun List<WidgetPlacement>.bottom() = maxOfOrNull { it.cell.bottom } ?: 0

/**
 * Launcher3's CellLayout.findNearestArea: the area of [span] whose centre is nearest [centre] (dp from the grid's
 * top-start corner), vacant (of every widget but [except]) unless [ignoreOccupied]. The grid grows down as needed,
 * one widget below the others at most, so there is always one.
 */
internal fun List<WidgetPlacement>.nearestArea(
    centre: Offset,
    span: CellSpan,
    pitch: CellPitch,
    except: String? = null,
    ignoreOccupied: Boolean = false,
): CellRect {
    var best: CellRect? = null
    var bestDistance = Float.MAX_VALUE
    // Down to just below every widget, where a vacant area always is, and no further: dragged to the page's bottom,
    // a widget can't make it ever taller as it scrolls
    val lastRow = bottom()
    for (y in 0..lastRow) {
        for (x in 0..HOME_GRID_COLUMNS - span.w) {
            val rect = CellRect(x, y, span.w, span.h)
            if (!ignoreOccupied && !isVacant(rect, except)) continue
            val distance = (pitch.centre(rect) - centre).getDistance()
            if (distance < bestDistance) {
                best = rect
                bestDistance = distance
            }
        }
    }
    return checkNotNull(best) { "a ${span.w}-column widget can't fit $HOME_GRID_COLUMNS columns" }
}

/** Launcher3's CellLayout.findCellForSpan: the first vacant area of [span], row after row, as a widget added. */
internal fun List<WidgetPlacement>.firstVacant(span: CellSpan): CellRect {
    for (y in 0..bottom()) {
        for (x in 0..HOME_GRID_COLUMNS - span.w) {
            val rect = CellRect(x, y, span.w, span.h)
            if (isVacant(rect)) return rect
        }
    }
    return CellRect(0, bottom(), span.w, span.h)
}

/**
 * Launcher3's CellLayout.performReorder, from the layout as it was ([this], never a previous reorder's): [held] at its
 * [WidgetPlacement.cell], each widget it now overlaps moved to the vacant area nearest where it was, nearest first.
 * The grid grows down as needed, so there is always a solution; every other widget stays where it is.
 */
internal fun List<WidgetPlacement>.reorder(
    held: WidgetPlacement,
    pitch: CellPitch,
): List<WidgetPlacement> {
    val target = held.cell
    val (pushed, staying) = filter { it.widget.id != held.widget.id }.partition { it.cell.overlaps(target) }
    val placed = (staying + held).toMutableList()
    val heldCentre = pitch.centre(target)
    for (widget in pushed.sortedBy { (pitch.centre(it.cell) - heldCentre).getDistance() }) {
        placed += widget.copy(cell = placed.nearestArea(pitch.centre(widget.cell), widget.cell.span, pitch))
    }
    // In the order they were, the held one where it was or at the end
    val order = map { it.widget.id }
    return placed.sortedBy { order.indexOf(it.widget.id).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
}

/** A side of a widget a resize frame's handle drags. */
internal enum class ResizeEdge { START, END, TOP, BOTTOM }

/**
 * Launcher3's AppWidgetResizeFrame: [start] with its [edge] moved by [cells] cells (towards the end or the bottom when
 * positive), the opposite side kept, within the widget's limits and the grid.
 */
internal fun HomeWidget.resized(
    start: CellRect,
    edge: ResizeEdge,
    cells: Int,
    pitch: CellPitch,
): CellRect {
    val maxW = minOf(maxSpan.w, HOME_GRID_COLUMNS)
    return when (edge) {
        ResizeEdge.END -> {
            val w = (start.w + cells).coerceIn(minSpan.w, minOf(maxW, HOME_GRID_COLUMNS - start.x))
            val h = start.h.coerceAtLeast(minRows(w, pitch))
            start.copy(w = w, h = h)
        }
        ResizeEdge.START -> {
            val w = (start.w - cells).coerceIn(minSpan.w, minOf(maxW, start.right))
            val h = start.h.coerceAtLeast(minRows(w, pitch))
            CellRect(start.right - w, start.y, w, h)
        }
        ResizeEdge.BOTTOM -> {
            val h = (start.h + cells).coerceIn(minRows(start.w, pitch), maxOf(maxSpan.h, minRows(start.w, pitch)))
            start.copy(h = h)
        }
        ResizeEdge.TOP -> {
            // Its bottom kept, unless its content needs more rows than there are above it: then it grows down
            val minH = minRows(start.w, pitch)
            val h = (start.h - cells).coerceIn(minH, maxOf(minH, minOf(maxSpan.h, start.bottom)))
            CellRect(start.x, (start.bottom - h).coerceAtLeast(0), start.w, h)
        }
    }
}
