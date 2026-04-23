package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.paging.compose.LazyPagingItems
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.theme.scrollbarStyle

/**
 * Content-aware vertical scrollbar for a paginated list of [CommentaryWithText].
 *
 * Works in pixel-space, using three exact inputs supplied by the caller:
 *  - [capacity] — chars per visual line, measured once by [androidx.compose.ui.text.TextMeasurer]
 *    against the current text area width, font family and font size. Deterministic: does not
 *    move during scroll, updates naturally on resize / font change.
 *  - [lineHeightPx] — exact line-height in px, derived from the font settings (`fontSize ×
 *    lineHeight multiplier`). The per-visual-line pixel cost in Compose's layout.
 *  - [paddingPerItemPx] — total vertical padding applied to every item by its container
 *    (e.g. `padding(vertical = 8.dp)` on the Column in each item → 16 dp total).
 *
 * The total content height becomes
 * `totalContentPx = totalVisualLines × lineHeightPx + N × paddingPerItemPx`, which matches
 * what Compose actually lays out. Thumb size = `viewport / totalContentPx`, thumb position
 * = `scrollPx / (totalContentPx − viewport)`. No per-frame sampling, no latch heuristics.
 *
 * Drag is pixel-aware: a thumb ratio is converted to the target item by binary-searching
 * the `cumPx` prefix sum, so a 50 % drag on a list with one giant commentary and many
 * short ones lands at the visual midpoint, not the index midpoint.
 *
 * Visual styling — colors, thumb corner radius, track thickness, hover expand, fade
 * durations — is read from [JewelTheme.scrollbarStyle] so the scrollbar stays identical
 * to every other Jewel scrollbar in the app.
 */
@Composable
fun CommentariesScrollbar(
    listState: LazyListState,
    lazyPagingItems: LazyPagingItems<CommentaryWithText>,
    allCharCounts: List<Int>,
    capacity: Int,
    lineHeightPx: Float,
    paddingPerItemPx: Float,
    modifier: Modifier = Modifier,
) {
    if (lazyPagingItems.itemCount == 0 || allCharCounts.isEmpty()) return
    if (capacity <= 0 || lineHeightPx <= 0f) return

    val cumPx by remember(allCharCounts, capacity, lineHeightPx, paddingPerItemPx) {
        derivedStateOf { buildCumulativePixels(allCharCounts, capacity, lineHeightPx, paddingPerItemPx) }
    }
    val itemCount = allCharCounts.size
    val totalContentPx = cumPx[itemCount].toFloat()

    val latched =
        rememberLatchedThumbSize(
            latchKey = allCharCounts,
            capacity = capacity,
            lineHeightPx = lineHeightPx,
            totalContentPx = totalContentPx,
            listState = listState,
        ) ?: return
    if (latched.hidden) return

    // Thumb **position** uses Compose's native avg-item scroll geometry, not the
    // pixel-space estimate. Internally consistent: both `scrollOffsetAvg` and
    // `contentSizeAvg` scale with the same `avgItemSize`, so `position` reaches 0 at
    // scroll-start and 1 at scroll-end — no boundary pinning required, no flicker.
    val position = computeAvgScrollPosition(listState).coerceIn(0f, 1f)

    val listStateRef = rememberUpdatedState(listState)
    val cumPxRef = rememberUpdatedState(cumPx)
    val totalContentPxRef = rememberUpdatedState(totalContentPx)
    val itemCountRef = rememberUpdatedState(itemCount)

    // Convert a thumb ratio → target item index via the **pixel-space** prefix sum,
    // matching the geometry the thumb size is built from. Same shape as the book
    // scrollbar; no far-drag flow because all commentaries for a line load eagerly.
    val applyTarget =
        remember {
            { thumbRatio: Float, _: Boolean ->
                val ls = listStateRef.value
                val total = itemCountRef.value
                val cum = cumPxRef.value
                val info = ls.layoutInfo
                val loadedCount = info.totalItemsCount
                if (total > 0 && loadedCount > 0 && cum.size >= total + 1) {
                    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                    val totalPx = totalContentPxRef.value
                    val maxScrollPx = (totalPx - viewport).coerceAtLeast(0f).toDouble()
                    val targetPx = (thumbRatio.toDouble() * maxScrollPx).coerceIn(0.0, totalPx.toDouble())
                    val targetIdx = findLineIndexForPixel(cum, total, targetPx).coerceIn(0, loadedCount - 1)
                    ls.requestScrollToItem(targetIdx, 0)
                }
                Unit
            }
        }

    ContentAwareScrollbarShell(
        listState = listState,
        position = position,
        thumbSize = latched.size,
        visualsLabel = "commentaries_scrollbar",
        onApplyTarget = applyTarget,
        modifier = modifier,
    )
}

/**
 * Thumb position in `[0, 1]`, computed from `LazyListState` the same way the standard
 * Compose scrollbar adapter does: `(firstIdx × avgItemSize + firstOffset) / (totalCount
 * × avgItemSize − viewport)`. Reaches 0 exactly at scroll-start (firstIdx and offset
 * both 0); reaches 1 approximately at scroll-end as long as `avgItemSize` (sampled
 * over the currently visible items) stays representative of the items at the tail.
 * Approximate between boundaries — acceptable for a progress indicator. No boundary
 * pinning, no flicker.
 */
private fun computeAvgScrollPosition(listState: LazyListState): Float {
    val info = listState.layoutInfo
    val total = info.totalItemsCount
    if (total == 0) return 0f
    // Guard against paging prepends/appends where `visibleItemsInfo` briefly contains
    // indices beyond the paging snapshot size.
    val visible = info.visibleItemsInfo.filter { it.index in 0 until total }
    if (visible.isEmpty()) return 0f
    val avgItemSize = visible.sumOf { it.size }.toFloat() / visible.size
    if (avgItemSize <= 0f) return 0f
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    val contentSize = total * avgItemSize
    val maxScroll = (contentSize - viewport).coerceAtLeast(1f)
    val scrollOffset =
        listState.firstVisibleItemIndex * avgItemSize +
            listState.firstVisibleItemScrollOffset
    return scrollOffset / maxScroll
}
