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
        derivedStateOf {
            buildCumulativePixels(
                size = allCharCounts.size,
                capacity = capacity,
                lineHeightPx = lineHeightPx,
                paddingPerItemPx = paddingPerItemPx,
                charCountAt = { allCharCounts[it] },
            )
        }
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

    // Thumb **position** in pixel-space: `cumPx[firstIdx] + innerFraction × itemModelHeight`
    // divided by `totalContentPx − viewport`. Uses cumPx (exact modelled weight of each
    // preceding item) plus a real-pixel fraction remapped to cumPx units, so the thumb
    // advances proportionally to the modelled weight of the current item.
    val position =
        computeScrollPosition(listState, cumPx, itemCount, totalContentPx).coerceIn(0f, 1f)

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
                    val targetIdx = findItemIndexForPixel(cum, total, targetPx).coerceIn(0, loadedCount - 1)
                    // Offset **inside** the item so the viewport top lands exactly on
                    // `targetPx`. Without it, `requestScrollToItem(idx, 0)` pins the
                    // item to the viewport top and a drag to `thumbRatio = 1.0` stops
                    // short of the real end by `targetPx − cumPx[targetIdx]` pixels.
                    val offsetWithinItemPx =
                        (targetPx - cum[targetIdx].toDouble()).coerceAtLeast(0.0).toInt()
                    ls.requestScrollToItem(targetIdx, offsetWithinItemPx)
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
 * Pixel-space thumb position in `[0, 1]`.
 *
 * Formula: `scrolledPx = cumPx[firstIdx] + innerFraction × itemModelHeight`, divided
 * by `totalContentPx − viewport`. `cumPx[firstIdx]` exactly sums the modelled weight
 * of every item before the first visible one. `innerFraction = -offset / size` is the
 * proportion scrolled **through** the current item on real rendered pixels; multiplied
 * by `cumPx[firstIdx + 1] − cumPx[firstIdx]` it converts back into cumPx units so a
 * tall commentary moves the thumb more than a short one while scrolling through it.
 */
private fun computeScrollPosition(
    listState: LazyListState,
    cumPx: LongArray,
    itemCount: Int,
    totalContentPx: Float,
): Float {
    if (itemCount == 0 || cumPx.size < itemCount + 1) return 0f
    val info = listState.layoutInfo
    // Guard against transient states where `visibleItemsInfo` contains indices outside
    // our domain. Single-pass scan avoids a List allocation per scroll frame (60 Hz).
    var firstInfo: androidx.compose.foundation.lazy.LazyListItemInfo? = null
    val visibleList = info.visibleItemsInfo
    for (i in visibleList.indices) {
        val item = visibleList[i]
        if (item.index in 0 until itemCount) {
            firstInfo = item
            break
        }
    }
    if (firstInfo == null) return 0f
    val firstIdx = firstInfo.index.coerceIn(0, itemCount - 1)
    val firstSize = firstInfo.size.coerceAtLeast(1)
    val innerFraction = ((-firstInfo.offset).toFloat() / firstSize).coerceIn(0f, 1f)
    val itemModelHeight = (cumPx[firstIdx + 1] - cumPx[firstIdx]).toFloat().coerceAtLeast(0f)
    val scrolledPx = cumPx[firstIdx].toFloat() + innerFraction * itemModelHeight
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    val maxScroll = (totalContentPx - viewport).coerceAtLeast(1f)
    return scrolledPx / maxScroll
}
