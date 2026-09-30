package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val HOME_GRID_COLUMNS = 20
val HOME_GRID_CELL_HEIGHT = 135.dp
private val GRID_GAP = 12.dp
private val MAX_GRID_WIDTH = 1000.dp

/** Below this width every widget takes a whole row. */
private val COMPACT_GRID_WIDTH = 670.dp

private fun GridSize.height(): Dp = HOME_GRID_CELL_HEIGHT * rows + GRID_GAP * (rows - 1).coerceAtLeast(0f)

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
 * Splits [width] px (gaps already taken out) between widgets of these [columns], in proportion: a row that isn't
 * full stretches its widgets, so the grid never shows a hole whatever the user adds or removes.
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

@Composable
fun HomeWidgetsHost(
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
    widgets: List<WidgetPlacement> = homeWidgets,
) {
    BoxWithConstraints(modifier.widthIn(max = MAX_GRID_WIDTH).fillMaxWidth()) {
        val compact = maxWidth < COMPACT_GRID_WIDTH
        val rows = packRows(widgets.filter { it.widget.isSupported }, compact)
        Column(verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
            rows.forEach { row -> key(row.first().widget.id) { WidgetRow(row, state) } }
        }
    }
}

/**
 * One grid row: widgets share its width in proportion to their columns, the row is as tall as its tallest widget (a
 * [HomeWidget.wrapContentHeight] one may grow), and every other widget stretches to that height.
 */
@Composable
private fun WidgetRow(
    row: List<WidgetPlacement>,
    state: HomeWidgetsState,
) {
    Layout(
        content = { row.forEach { key(it.widget.id) { it.widget.Content(state, Modifier) } } },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val gap = GRID_GAP.roundToPx()
        val widths = rowWidths(row.map { it.grid.columns }, constraints.maxWidth - gap * (row.size - 1))
        val baseHeight = row.maxOf { it.grid.height().roundToPx() }
        val wrapped =
            row.indices.filter { row[it].widget.wrapContentHeight }.associateWith {
                measurables[it].measure(Constraints(widths[it], widths[it], baseHeight, Constraints.Infinity))
            }
        val height = maxOf(baseHeight, wrapped.values.maxOfOrNull { it.height } ?: 0)
        val placeables =
            row.indices.map { wrapped[it] ?: measurables[it].measure(Constraints.fixed(widths[it], height)) }
        layout(constraints.maxWidth, height) {
            var x = 0
            placeables.forEachIndexed { i, placeable ->
                placeable.placeRelative(x, 0)
                x += widths[i] + gap
            }
        }
    }
}
