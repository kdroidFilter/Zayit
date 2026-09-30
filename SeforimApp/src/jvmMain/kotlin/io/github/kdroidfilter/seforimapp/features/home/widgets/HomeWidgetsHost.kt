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

private fun HomeWidget.height(): Dp = HOME_GRID_CELL_HEIGHT * rows + GRID_GAP * (rows - 1).coerceAtLeast(0f)

/** Fills rows of [HOME_GRID_COLUMNS] columns with [widgets] in order, a widget that doesn't fit starting a new row. */
internal fun packRows(
    widgets: List<HomeWidget>,
    compact: Boolean,
): List<List<HomeWidget>> {
    if (compact) return widgets.map { listOf(it) }
    val rows = mutableListOf<MutableList<HomeWidget>>()
    for (widget in widgets) {
        val last = rows.lastOrNull()
        if (last != null && last.sumOf { it.columns } + widget.columns <= HOME_GRID_COLUMNS) {
            last += widget
        } else {
            rows += mutableListOf(widget)
        }
    }
    return rows
}

@Composable
fun HomeWidgetsHost(
    state: HomeWidgetsState,
    modifier: Modifier = Modifier,
    widgets: List<HomeWidget> = homeWidgets,
) {
    BoxWithConstraints(modifier.widthIn(max = MAX_GRID_WIDTH).fillMaxWidth()) {
        val compact = maxWidth < COMPACT_GRID_WIDTH
        val rows = packRows(widgets.filter { it.isSupported }, compact)
        Column(verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
            rows.forEach { row -> key(row.first().id) { WidgetRow(row, state, compact) } }
        }
    }
}

/**
 * One grid row: widgets are as wide as their columns, the row as tall as its tallest widget (a
 * [HomeWidget.wrapContentHeight] one may grow), and every other widget stretches to that height.
 */
@Composable
private fun WidgetRow(
    row: List<HomeWidget>,
    state: HomeWidgetsState,
    compact: Boolean,
) {
    Layout(
        content = { row.forEach { key(it.id) { it.Content(state, Modifier) } } },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val gap = GRID_GAP.roundToPx()
        val pitch = (constraints.maxWidth + gap) / HOME_GRID_COLUMNS.toFloat()
        val widths = row.map { if (compact) constraints.maxWidth else (it.columns * pitch - gap).toInt() }
        val baseHeight = row.maxOf { it.height().roundToPx() }
        val wrapped =
            row.indices.filter { row[it].wrapContentHeight }.associateWith {
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
