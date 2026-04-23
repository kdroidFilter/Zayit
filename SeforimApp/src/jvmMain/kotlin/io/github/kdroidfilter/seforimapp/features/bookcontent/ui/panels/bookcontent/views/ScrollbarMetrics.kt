package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import kotlin.math.ceil
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

/**
 * Realistic Hebrew prose used by `TextMeasurer` to compute chars-per-visual-line.
 *
 * A continuous `"א"×N` reference would pack char-by-char with no word-boundary waste,
 * which over-estimates capacity versus real content: Compose word-wraps real text at
 * spaces, leaving a few unused pixels at the end of each line. Using text with natural
 * Hebrew word lengths and spaces yields a capacity that matches what Compose will
 * actually wrap in the rendered items.
 *
 * Shared between [BookContentView]'s main scrollbar metrics and [LineCommentsView]'s
 * commentary scrollbar metrics — both pieces measure prose-rendered Hebrew text.
 */
internal val CAPACITY_REFERENCE =
    ("ועל כן ראוי לנו לומר בדבר הזה ולהבין על מה כוונת המחבר בהזכירו דברים אלו ").repeat(200)

/**
 * Re-latch the thumb size when the viewport drifts by at least this fraction from
 * the previously latched value. Catches resize/SplitPane changes while ignoring
 * sub-pixel jitter from layout settle. Shared between the book and commentary
 * scrollbars.
 */
internal const val RELATCH_VIEWPORT_THRESHOLD = 0.10f

/**
 * Safety net: if the live scroll position never converges to within 0.005 of a dragged
 * target (e.g. the target rounds to a different last-visible index, or the list shrinks
 * under us), unpin the thumb after this timeout so it stops floating.
 */
internal val PENDING_JUMP_TIMEOUT = 1500.milliseconds

/**
 * Visual lines a single item occupies in the rendered text area: `ceil(charCount / capacity)`,
 * floored at 1 so even an empty line still consumes a slot. Shared by both scrollbars.
 */
internal fun visualLinesOf(
    charCount: Int,
    capacity: Int,
): Int {
    if (capacity <= 0) return 1
    if (charCount <= 0) return 1
    return max(1, ceil(charCount.toDouble() / capacity).toInt())
}

/**
 * Pixel-space prefix sum: `cumPx[i]` is the total content height of items `[0, i)`,
 * an exact match for what Compose lays out. The last entry equals total content height.
 * Used both for thumb **size** and for converting a thumb ratio → target item index in
 * O(log N) on drag/jump (see [findLineIndexForPixel]). Accumulates in Double to avoid
 * drift over large books, then snapshots to Long.
 */
internal fun buildCumulativePixels(
    charCounts: IntArray,
    capacity: Int,
    lineHeightPx: Float,
    paddingPerItemPx: Float,
): LongArray {
    val n = charCounts.size
    val arr = LongArray(n + 1)
    var acc = 0.0
    for (i in 0 until n) {
        arr[i] = acc.toLong()
        acc += visualLinesOf(charCounts[i], capacity) * lineHeightPx.toDouble() + paddingPerItemPx
    }
    arr[n] = acc.toLong()
    return arr
}

/** [List] overload of [buildCumulativePixels] for callers backed by `List<Int>`. */
internal fun buildCumulativePixels(
    charCounts: List<Int>,
    capacity: Int,
    lineHeightPx: Float,
    paddingPerItemPx: Float,
): LongArray {
    val n = charCounts.size
    val arr = LongArray(n + 1)
    var acc = 0.0
    for (i in 0 until n) {
        arr[i] = acc.toLong()
        acc += visualLinesOf(charCounts[i], capacity) * lineHeightPx.toDouble() + paddingPerItemPx
    }
    arr[n] = acc.toLong()
    return arr
}

/**
 * Largest item index `i ∈ [0, total)` such that `cumPx[i] ≤ targetPx`. Pure binary
 * search, no allocation. `cumPx` is monotonically non-decreasing with `cumPx[0] = 0`
 * and `cumPx[total]` = total content height.
 */
internal fun findLineIndexForPixel(
    cumPx: LongArray,
    total: Int,
    targetPx: Double,
): Int {
    if (total <= 0) return 0
    val target = targetPx.toLong()
    var lo = 0
    var hi = total - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) ushr 1
        if (cumPx[mid] <= target) lo = mid else hi = mid - 1
    }
    return lo
}
