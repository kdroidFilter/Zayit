package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widgets_edit
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
    val save = { HomeWidgetsLayout.save(live + widgets.filterNot { it.widget.isSupported }) }

    BoxWithConstraints(modifier) {
        val side = ((maxWidth - MAX_GRID_WIDTH) / 2).coerceAtLeast(0.dp)
        val cells = gridCells(live, maxWidth - side * 2)
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
                items(cells, key = { it.placement.widget.id }, span = { GridItemSpan(it.span) }) { cell ->
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
                fullWidthItem {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (state.editingWidgets) {
                            // Lets the last widgets scroll above the gallery panel
                            Spacer(Modifier.height(WIDGET_GALLERY_HEIGHT))
                        } else {
                            OutlinedButton(onClick = { state.editingWidgets = true }) {
                                Text(stringResource(Res.string.home_widgets_edit))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** An item across the whole grid, as the Home's search sections are. */
fun LazyGridScope.fullWidthItem(content: @Composable () -> Unit) = item(span = { GridItemSpan(maxLineSpan) }) { content() }

/**
 * A widget dragged from the gallery ([newWidget] at [pointer]) and where the placed widgets sit, in root coordinates,
 * to find the one it's dropped on.
 */
internal class WidgetDrag {
    var targetId by mutableStateOf<String?>(null)
    var newWidget by mutableStateOf<WidgetPlacement?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    val bounds = mutableMapOf<String, Rect>()
    var galleryBounds = Rect.Zero

    fun widgetAt(point: Offset): String? = bounds.entries.firstOrNull { (_, rect) -> rect.contains(point) }?.key
}
