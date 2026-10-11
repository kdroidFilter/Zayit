package io.github.kdroidfilter.seforimapp.features.home.widgets

import kotlin.math.abs
import kotlin.random.Random

// The Home's "arrange": the widgets laid out again, resized within their limits, so the grid has as few holes as can
// be and is as short as can be. The layout is rows across the whole grid; a row is columns side by side, all of the
// row's height, a column one widget or a few stacked at the same width. Over every way of dealing the widgets into
// rows and columns (a search over subsets: the widgets are at most a dozen or so), the one with the fewest empty cells,
// then the fewest rows of the grid, wins; each widget then takes the size of those allowed nearest the one it had.
// Many layouts are often as tight: pressed again, the button deals the widgets in another order, for another of them.

/** Widgets stacked in one column, at most: three of the 2-row ones make one 6 rows tall. */
private const val MAX_STACK = 3

/** Orders tried for a layout other than the one shown, before deciding there's no other. */
private const val MAX_TRIES = 8

/** An empty cell weighs more than any number of rows: a hole is what arranging is for. */
private const val HOLE_COST = 1000

private const val ALL_WIDTHS = (1 shl (HOME_GRID_COLUMNS + 1)) - 1

private fun widthsOf(bits: Int) = (0..HOME_GRID_COLUMNS).filter { bits shr it and 1 == 1 }

/**
 * The [attempt]th layout as tight as can be, other than this one; this one when there's none, or when it's tighter
 * than any of rows and columns. Slow-ish (some 100 ms for every widget): off the main thread.
 */
internal fun List<WidgetPlacement>.rearranged(
    pitch: CellPitch,
    attempt: Int,
): List<WidgetPlacement> {
    val shown = map { it.widget.id to it.cell }.toSet()
    val score = score()
    return (attempt until attempt + MAX_TRIES)
        .asSequence()
        .map { seed -> arranged(pitch, if (seed == 0) null else Random(seed)) }
        .firstOrNull { it.score() <= score && it.map { p -> p.widget.id to p.cell }.toSet() != shown } ?: this
}

/** The tightest layout of rows and columns, dealing the widgets in reading order, or shuffled by [random]. */
internal fun List<WidgetPlacement>.arranged(
    pitch: CellPitch,
    random: Random? = null,
): List<WidgetPlacement> {
    if (isEmpty()) return this
    // The order rows and columns keep where the cost ties
    val reading = sortedWith(compareBy({ it.cell.y }, { it.cell.x }))
    val widgets = random?.let(reading::shuffled) ?: reading
    val n = widgets.size
    val full = (1 shl n) - 1

    // Each widget's allowed sizes: for a height, the widths it can have at it, as bits
    val maxH = widgets.maxOf { p -> p.widget.heights(pitch).maxOf { it.key } }
    val fits = Array(n) { i -> IntArray(maxH + 1).also { a -> widgets[i].widget.heights(pitch).forEach { (h, w) -> a[h] = w } } }

    // column[mask][h]: the widths at which the widgets of mask, stacked, are exactly h rows tall
    val column = Array(1 shl n) { IntArray(maxH + 1) }
    for (mask in 1..full) {
        if (Integer.bitCount(mask) > MAX_STACK) continue
        val top = Integer.numberOfTrailingZeros(mask)
        val rest = mask and (mask - 1)
        for (h in 1..maxH) {
            column[mask][h] =
                if (rest == 0) {
                    fits[top][h]
                } else {
                    (1 until h).fold(0) { bits, topH -> bits or (fits[top][topH] and column[rest][h - topH]) }
                }
        }
    }

    // row[mask][h]: the total widths at which the widgets of mask, in columns h rows tall, can be side by side
    val row = Array(1 shl n) { IntArray(maxH + 1) }
    for (mask in 1..full) {
        val low = mask and -mask
        for (h in 1..maxH) {
            var bits = 0
            forEachSubmask(mask xor low) { s ->
                val c = s or low
                val cols = column[c][h]
                if (cols == 0) return@forEachSubmask
                val others = if (c == mask) 1 else row[mask xor c][h]
                if (others == 0) return@forEachSubmask
                for (w in widthsOf(cols)) bits = bits or (others shl w)
            }
            row[mask][h] = bits and ALL_WIDTHS
        }
    }

    // A row's best: its height and width, at the least cost
    data class Row(
        val cost: Int,
        val h: Int,
        val w: Int,
    )
    val bestRow =
        Array(1 shl n) { mask ->
            if (mask == 0) return@Array null
            (1..maxH)
                .mapNotNull { h ->
                    val w = widthsOf(row[mask][h]).lastOrNull() ?: return@mapNotNull null
                    Row((HOME_GRID_COLUMNS - w) * h * HOLE_COST + h, h, w)
                }.minByOrNull { it.cost }
        }

    // layout[mask]: the cheapest way of dealing the widgets of mask into rows, its first row
    val cost = IntArray(1 shl n) { Int.MAX_VALUE }
    val firstRow = IntArray(1 shl n)
    cost[0] = 0
    for (mask in 1..full) {
        val low = mask and -mask
        forEachSubmask(mask xor low) { s ->
            val r = s or low
            val rowCost = bestRow[r]?.cost ?: return@forEachSubmask
            val rest = cost[mask xor r]
            if (rest != Int.MAX_VALUE && rowCost + rest < cost[mask]) {
                cost[mask] = rowCost + rest
                firstRow[mask] = r
            }
        }
    }

    // The rows back, in the order of their first widget, any with a hole last (a hole mid-page splits it), each widget
    // sized nearest what it had
    val rows = generateSequence(full) { it xor firstRow[it] }.takeWhile { it != 0 }.map { firstRow[it] }.toList()
    var y = 0
    val placed = mutableListOf<WidgetPlacement>()
    for (r in rows.sortedWith(compareBy({ bestRow[it]!!.w < HOME_GRID_COLUMNS }, { Integer.numberOfTrailingZeros(it) }))) {
        val best = checkNotNull(bestRow[r])
        var x = 0
        for ((c, w) in splitRow(r, best.h, best.w, row, column, widgets)) {
            var top = y
            for ((i, h) in splitColumn(c, best.h, w, column, fits, widgets)) {
                placed += widgets[i].copy(cell = CellRect(x, top, w, h))
                top += h
            }
            x += w
        }
        y += best.h
    }
    val order = map { it.widget.id }
    return placed.sortedBy { order.indexOf(it.widget.id) }
}

/** How much room a layout loses: its empty cells, then its rows, as the search weighs them. */
private fun List<WidgetPlacement>.score() = (bottom() * HOME_GRID_COLUMNS - sumOf { it.cell.w * it.cell.h }) * HOLE_COST + bottom()

/** For each height a widget can have, the widths it can have at it, as bits. */
private fun HomeWidget.heights(pitch: CellPitch): Map<Int, Int> {
    val sizes = mutableMapOf<Int, Int>()
    for (w in minSpan.w..minOf(maxSpan.w, HOME_GRID_COLUMNS)) {
        val minH = minRows(w, pitch)
        for (h in minH..maxOf(maxSpan.h, minH)) sizes[h] = (sizes[h] ?: 0) or (1 shl w)
    }
    return sizes
}

private inline fun forEachSubmask(
    mask: Int,
    action: (Int) -> Unit,
) {
    var s = mask
    while (true) {
        action(s)
        if (s == 0) return
        s = (s - 1) and mask
    }
}

/** Row [r], [h] tall and [width] wide, as its columns and their widths, the widths nearest the widgets' own. */
private fun splitRow(
    r: Int,
    h: Int,
    width: Int,
    row: Array<IntArray>,
    column: Array<IntArray>,
    widgets: List<WidgetPlacement>,
): List<Pair<Int, Int>> {
    if (r == 0) return emptyList()
    val low = r and -r
    var best: Triple<Int, Int, Int>? = null // column, width, distance from the widgets' own
    forEachSubmask(r xor low) { s ->
        val c = s or low
        for (w in widthsOf(column[c][h])) {
            val left = width - w
            val fits = if (c == r) left == 0 else left > 0 && row[r xor c][h] shr left and 1 == 1
            if (!fits) continue
            val distance = indices(c).sumOf { abs(widgets[it].cell.w - w) }
            if (best == null || distance < best!!.third) best = Triple(c, w, distance)
        }
    }
    val (c, w) = checkNotNull(best)
    return listOf(c to w) + splitRow(r xor c, h, width - w, row, column, widgets)
}

/** Column [c], [h] tall at width [w], as its widgets and their heights, top first, nearest the widgets' own. */
private fun splitColumn(
    c: Int,
    h: Int,
    w: Int,
    column: Array<IntArray>,
    fits: Array<IntArray>,
    widgets: List<WidgetPlacement>,
): List<Pair<Int, Int>> {
    val top = Integer.numberOfTrailingZeros(c)
    val rest = c and (c - 1)
    if (rest == 0) return listOf(top to h)
    val topH =
        (1 until h)
            .filter { fits[top][it] shr w and 1 == 1 && column[rest][h - it] shr w and 1 == 1 }
            .minBy { abs(widgets[top].cell.h - it) }
    return listOf(top to topH) + splitColumn(rest, h - topH, w, column, fits, widgets)
}

private fun indices(mask: Int) = (0 until Int.SIZE_BITS).filter { mask shr it and 1 == 1 }
