package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier

/**
 * Content-aware vertical scrollbar for a multi-section link/targum list.
 *
 * The list is a single [androidx.compose.foundation.lazy.LazyColumn] concatenating
 * N sections, each laid out as `[section header] + [paged link items]`. The scrollbar
 * receives [allCharCounts] already flattened in that same display order — the caller
 * inserts a zero char-count entry per header so cumulative pixels line up one-to-one
 * with the LazyColumn's item indices.
 *
 * Thumb size follows the same pixel-space model as [CommentariesScrollbar]: each
 * item contributes `ceil(charCount / capacity) × lineHeightPx + paddingPerItemPx`
 * to the total content height, so a short header and a 2000-char targum entry scale
 * proportionally. Thumb position uses Compose's native avg-item geometry (same as
 * the commentaries scrollbar) — stable at boundaries, approximate in between.
 *
 * Visual styling comes from [org.jetbrains.jewel.ui.theme.scrollbarStyle] via
 * [ContentAwareScrollbarShell], keeping the track / thumb / hover-expand identical
 * to every other scrollbar in the content pane.
 */
@Composable
fun TargumScrollbar(
    listState: LazyListState,
    allCharCounts: List<Int>,
    capacity: Int,
    lineHeightPx: Float,
    paddingPerItemPx: Float,
    modifier: Modifier = Modifier,
) {
    if (allCharCounts.isEmpty()) return
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

    val position = computeAvgScrollPosition(listState).coerceIn(0f, 1f)

    val listStateRef = rememberUpdatedState(listState)
    val cumPxRef = rememberUpdatedState(cumPx)
    val totalContentPxRef = rememberUpdatedState(totalContentPx)
    val itemCountRef = rememberUpdatedState(itemCount)

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
                    ls.requestScrollToItem(targetIdx, 0)
                }
                Unit
            }
        }

    ContentAwareScrollbarShell(
        listState = listState,
        position = position,
        thumbSize = latched.size,
        visualsLabel = "targum_scrollbar",
        onApplyTarget = applyTarget,
        modifier = modifier,
    )
}

/**
 * Thumb position in `[0, 1]` from [LazyListState] using the same avg-item geometry
 * as the standard Compose scrollbar adapter. Duplicated locally to avoid coupling
 * to [CommentariesScrollbar]'s private helper.
 */
private fun computeAvgScrollPosition(listState: LazyListState): Float {
    val info = listState.layoutInfo
    val total = info.totalItemsCount
    if (total == 0) return 0f
    val visible = info.visibleItemsInfo.filter { it.index in 0 until total }
    if (visible.isEmpty()) return 0f
    val avgItemSize = visible.sumOf { it.size }.toFloat() / visible.size
    if (avgItemSize <= 0f) return 0f
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    val contentSize = total * avgItemSize
    val maxScroll = (contentSize - viewport).coerceAtLeast(1f)
    val scrollOffset =
        listState.firstVisibleItemIndex * avgItemSize + listState.firstVisibleItemScrollOffset
    return scrollOffset / maxScroll
}
