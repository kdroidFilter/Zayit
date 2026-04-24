package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.paging.compose.LazyPagingItems
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import kotlin.time.Duration.Companion.milliseconds

/**
 * Content-aware vertical scrollbar for the book content pane.
 *
 * Works in pixel-space, using three exact inputs supplied by the caller:
 *  - [capacity] — chars per visual line, measured once by [androidx.compose.ui.text.TextMeasurer]
 *    against the current text area width, font family and font size. Deterministic: does not
 *    move during scroll, updates naturally on resize / font change.
 *  - [lineHeightPx] — exact line-height in px, derived from the font settings.
 *  - [paddingPerItemPx] — total vertical padding applied to every line's slot.
 *
 * The total content height becomes
 * `totalContentPx = totalVisualLines × lineHeightPx + N × paddingPerItemPx`, which matches
 * what Compose actually lays out. Thumb size = `viewport / totalContentPx`, thumb position
 * = `scrollPx / (totalContentPx − viewport)`. No per-frame sampling, no latch heuristics.
 *
 * [bookCharCounts] is the per-line raw char-count vector for the currently loaded book in
 * `lineIndex` order (the VM prefetches it once per book). The composable prefix-sums the
 * `max(1, ceil(charCount[i] / capacity))` mapping, binary-searches it on drag/jump, and
 * defers to [onScrollToLineIndex] when the target line lies outside the loaded pager window.
 *
 * Visual styling comes from [JewelTheme.scrollbarStyle] so the scrollbar blends in with
 * every other Jewel scrollbar in the app.
 */
@Composable
fun ContentScrollbar(
    listState: LazyListState,
    lazyPagingItems: LazyPagingItems<Line>,
    bookCharCounts: IntArray?,
    capacity: Int,
    lineHeightPx: Float,
    paddingPerItemPx: Float,
    onScrollToLineIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val counts = bookCharCounts
    if (counts == null || counts.isEmpty() || lazyPagingItems.itemCount == 0) return
    if (capacity <= 0 || lineHeightPx <= 0f) return

    val cumPx by remember(counts, capacity, lineHeightPx, paddingPerItemPx) {
        derivedStateOf {
            buildCumulativePixels(
                size = counts.size,
                capacity = capacity,
                lineHeightPx = lineHeightPx,
                paddingPerItemPx = paddingPerItemPx,
                charCountAt = { counts[it] },
            )
        }
    }
    val itemCount = counts.size
    val totalContentPx = cumPx[itemCount].toFloat()

    val latched =
        rememberLatchedThumbSize(
            latchKey = counts,
            capacity = capacity,
            lineHeightPx = lineHeightPx,
            totalContentPx = totalContentPx,
            listState = listState,
        ) ?: return
    if (latched.hidden) return

    // Thumb **position** uses the book-wide line-index geometry (not the paged-window
    // line index): `position = (firstLineIdx + firstInnerOffset) / (N − visibleLines)`.
    // At scroll start `firstLineIdx = 0` → 0. At scroll end `firstLineIdx = N −
    // visibleLines` → 1. Numerator and denominator scale with the same `avgItemSize`
    // so the ratio reaches the boundaries exactly, no pinning required, no flicker.
    val position = computeBookPosition(listState, lazyPagingItems, counts.size).coerceIn(0f, 1f)

    val listStateRef = rememberUpdatedState(listState)
    val pagingRef = rememberUpdatedState(lazyPagingItems)
    val bookLineCountRef = rememberUpdatedState(counts.size)
    val cumPxRef = rememberUpdatedState(cumPx)
    val totalContentPxRef = rememberUpdatedState(totalContentPx)
    val onScrollToLineIndexState = rememberUpdatedState(onScrollToLineIndex)

    // Far-drag pager rebuild emissions. During a drag, every delta that lands outside
    // the loaded pager window emits a target into this flow; the collector throttles
    // emissions to one rebuild every [FAR_DRAG_THROTTLE], with `DROP_OLDEST` so only
    // the latest target survives. This keeps the displayed text catching up to fast
    // drags (~5 Hz) without spamming `buildLinesPager` on every frame. The latest
    // target is also stashed in [pendingFarDragTarget] so we can flush it once more
    // on `onDragStopped` — guaranteeing the final landing spot is exact even if the
    // last throttled emission was dropped.
    val pendingFarDragTarget = remember { mutableStateOf<Int?>(null) }
    val farDragFlow =
        remember {
            MutableSharedFlow<Int>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        }
    LaunchedEffect(farDragFlow) {
        farDragFlow.collect { lineIndex ->
            onScrollToLineIndexState.value(lineIndex)
            delay(FAR_DRAG_THROTTLE)
        }
    }

    // Convert a thumb ratio → target book line index via the **pixel-space** prefix
    // sum: `targetPx = ratio × (totalContentPx − viewport)`, then binary-search
    // `cumPx` for the line whose top is closest to `targetPx`. Snap locally when the
    // target line is loaded in the pager; otherwise route through the throttled
    // far-drag flow (drag) or call back immediately (tap-jump).
    val applyTarget =
        remember {
            { thumbRatio: Float, viaDrag: Boolean ->
                val ls = listStateRef.value
                val total = bookLineCountRef.value
                val cum = cumPxRef.value
                if (total > 0 && cum.size >= total + 1) {
                    val info = ls.layoutInfo
                    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                    val totalPx = totalContentPxRef.value
                    val maxScrollPx = (totalPx - viewport).coerceAtLeast(0f).toDouble()
                    val targetPx = (thumbRatio.toDouble() * maxScrollPx).coerceIn(0.0, totalPx.toDouble())
                    val targetLineIndex = findItemIndexForPixel(cum, total, targetPx)
                    // Pager snapshot is sorted ascending by `lineIndex`, so a binary
                    // search converts paged-window position lookups from O(N) to
                    // O(log N) on every drag delta.
                    val snapshot = pagingRef.value.itemSnapshotList.items
                    val hit = snapshot.binarySearchBy(targetLineIndex) { it.lineIndex }
                    val localIndex = if (hit >= 0) hit else -1
                    when {
                        localIndex >= 0 -> {
                            ls.requestScrollToItem(localIndex, 0)
                            pendingFarDragTarget.value = null
                        }
                        viaDrag -> {
                            pendingFarDragTarget.value = targetLineIndex
                            farDragFlow.tryEmit(targetLineIndex)
                        }
                        else -> onScrollToLineIndexState.value(targetLineIndex)
                    }
                }
                Unit
            }
        }

    ContentAwareScrollbarShell(
        listState = listState,
        position = position,
        thumbSize = latched.size,
        visualsLabel = "book_scrollbar",
        onApplyTarget = applyTarget,
        modifier = modifier,
        onDragStart = { pendingFarDragTarget.value = null },
        onDragStop = {
            pendingFarDragTarget.value?.let { idx ->
                pendingFarDragTarget.value = null
                onScrollToLineIndexState.value(idx)
            }
        },
    )
}

// Minimum gap between two pager-rebuild emissions during a drag. Keeps the visible
// text catching up to fast drags (~5 Hz) without spamming `buildLinesPager` on every
// frame. Final exact target is flushed on `onDragStopped` regardless.
private val FAR_DRAG_THROTTLE = 200.milliseconds

/**
 * Book-wide thumb position in `[0, 1]`, using the same avg-item geometry as the standard
 * Compose scrollbar adapter — but indexed on **book line number** rather than paging-
 * window index, so it reflects progress through the whole book, not just the loaded
 * page. Numerator and denominator both scale with `avgItemSize` so the ratio reaches 0
 * and 1 exactly at the book's scroll boundaries. No boundary pinning, no flicker.
 */
private fun computeBookPosition(
    listState: LazyListState,
    lazyPagingItems: LazyPagingItems<Line>,
    bookLineCount: Int,
): Float {
    if (bookLineCount == 0) return 0f
    val itemCount = lazyPagingItems.itemCount
    if (itemCount == 0) return 0f
    val info = listState.layoutInfo
    // Guard against paging prepends/appends where `visibleItemsInfo` briefly contains
    // indices beyond the snapshot size — `peek()` would throw `IndexOutOfBoundsException`.
    val visible = info.visibleItemsInfo.filter { it.index in 0 until itemCount }
    if (visible.isEmpty()) return 0f
    val firstInfo = visible.first()
    val firstLine = lazyPagingItems.peek(firstInfo.index) ?: return 0f
    val firstLineIdx = firstLine.lineIndex.coerceAtLeast(0)
    val firstSize = firstInfo.size.coerceAtLeast(1)
    val firstInnerOffset = ((-firstInfo.offset).toFloat() / firstSize).coerceIn(0f, 1f)
    val avgItemSize = visible.sumOf { it.size }.toFloat() / visible.size
    if (avgItemSize <= 0f) return 0f
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    val visibleCount = (viewport / avgItemSize).coerceAtLeast(1f)
    val maxProgress = (bookLineCount - visibleCount).coerceAtLeast(1f)
    return ((firstLineIdx + firstInnerOffset) / maxProgress)
}
