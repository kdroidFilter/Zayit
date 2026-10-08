package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
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
 * O(log N) on drag/jump (see [findItemIndexForPixel]). Accumulates in Double to avoid
 * drift over large books, then snapshots to Long.
 *
 * The [charCountAt] selector lets callers back this with either an `IntArray` (book
 * scrollbar) or a `List<Int>` (commentaries scrollbar) without an extra copy.
 */
internal fun buildCumulativePixels(
    size: Int,
    capacity: Int,
    lineHeightPx: Float,
    paddingPerItemPx: Float,
    charCountAt: (Int) -> Int,
): LongArray {
    val arr = LongArray(size + 1)
    var acc = 0.0
    for (i in 0 until size) {
        arr[i] = acc.toLong()
        acc += visualLinesOf(charCountAt(i), capacity) * lineHeightPx.toDouble() + paddingPerItemPx
    }
    arr[size] = acc.toLong()
    return arr
}

/**
 * Largest item index `i ∈ [0, total)` such that `cumPx[i] ≤ targetPx`. Pure binary
 * search, no allocation. `cumPx` is monotonically non-decreasing with `cumPx[0] = 0`
 * and `cumPx[total]` = total content height.
 */
internal fun findItemIndexForPixel(
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

/**
 * Thumb position in `[0, 1]`, as `above / (above + below)` in cumPx units.
 *
 * `above` is the modelled height scrolled past the viewport top: `cumPx[first]` plus the
 * real fraction already scrolled through the first visible item, remapped to its
 * modelled height. `below` mirrors it from the viewport bottom: the real fraction of the
 * last visible item still hidden, remapped the same way, plus `cumPx` of every item
 * after it. Both ends are therefore exact on real layout — `above = 0` at the top and
 * `below = 0` at the bottom — even when the model over- or under-estimates heights.
 * Dividing by `total − viewport` instead left the thumb short of the end whenever the
 * last screen's modelled height exceeded the viewport, a gap that is visible on short
 * books where `total − viewport` is small.
 *
 * [modelIndexAt] maps a lazy-list index to its index in [cumPx], or `-1` for items
 * outside the modelled domain (loaders, transient paging indices).
 */
internal inline fun computeModelScrollRatio(
    info: LazyListLayoutInfo,
    cumPx: LongArray,
    total: Int,
    modelIndexAt: (lazyIndex: Int) -> Int,
): Float {
    if (total <= 0 || cumPx.size < total + 1) return 0f
    // Indexed scans: no iterator nor filtered list allocated on each scroll frame.
    val visible = info.visibleItemsInfo
    var firstInfo: LazyListItemInfo? = null
    var firstIdx = -1
    for (i in visible.indices) {
        val idx = modelIndexAt(visible[i].index)
        if (idx >= 0) {
            firstInfo = visible[i]
            firstIdx = idx.coerceAtMost(total - 1)
            break
        }
    }
    if (firstInfo == null) return 0f
    var lastInfo: LazyListItemInfo = firstInfo
    var lastIdx = firstIdx
    for (i in visible.lastIndex downTo 0) {
        val idx = modelIndexAt(visible[i].index)
        if (idx >= 0) {
            lastInfo = visible[i]
            lastIdx = idx.coerceAtMost(total - 1)
            break
        }
    }

    val hiddenAbove = (info.viewportStartOffset - firstInfo.offset).toFloat()
    val firstFraction = (hiddenAbove / firstInfo.size.coerceAtLeast(1)).coerceIn(0f, 1f)
    val above = cumPx[firstIdx] + firstFraction * (cumPx[firstIdx + 1] - cumPx[firstIdx])

    val hiddenBelow = (lastInfo.offset + lastInfo.size - info.viewportEndOffset).toFloat()
    val lastFraction = (hiddenBelow / lastInfo.size.coerceAtLeast(1)).coerceIn(0f, 1f)
    val below = (cumPx[total] - cumPx[lastIdx + 1]) + lastFraction * (cumPx[lastIdx + 1] - cumPx[lastIdx])

    val sum = above + below
    return if (sum <= 0f) 0f else above / sum
}
