package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Narrower than this, a tab no longer shows a readable part of its title: the strip shows as many as fit at this
// width, around the current one, and lists the others under a chip.
internal val MinReadableTabWidth = 112.dp

// The empty strip kept at the trailing edge to move the window by, and how far it shrinks to keep a tab on show.
internal val MaxReservedDragArea = 40.dp
internal val MinReservedDragArea = 12.dp

// A pinned tab: its icon and padding only (Chromium's TabStyle::GetPinnedWidth, without the corners).
internal val PinnedTabWidth = 40.dp

// The chip as its chevron alone: outer padding (2 × 2), inner padding (2 × 10) and chevron (16).
internal val TabOverflowChevronWidth = 40.dp

// The + (40), its divider (1) and the divider's padding (2 × 4).
internal val AddButtonWithDividerWidth = 49.dp

// Around MinReadableTabWidth, so a slow resize across it doesn't flicker.
internal val OverflowHysteresis = 6.dp

/** How the strip lays its tabs out in the room it has; see [computeTabStripLayout]. */
internal data class TabStripLayout(
    // The empty strip kept at the trailing edge to move the window by.
    val reservedDragArea: Dp,
    // Each unpinned tab's share of the room, before any overflow.
    val naturalTabWidth: Dp,
    // Too narrow for every tab's title: fed back as the next call's `wasTooNarrow`.
    val tooNarrow: Boolean,
    // Not even one readable tab fits: the current tab shows as its icon.
    val currentTabAsIcon: Boolean,
    // Some tabs are hidden behind the chip.
    val overflowing: Boolean,
    // The chip says "N more" rather than its chevron alone.
    val chipShowsCount: Boolean,
    // How many unpinned tabs the strip shows (0: the current one as its icon only).
    val visibleCount: Int,
    // The width each shown unpinned tab aims at.
    val tabWidth: Dp,
    // The + has room; without it, its action is in the chip's menu.
    val showAddButton: Boolean,
)

/**
 * The strip's layout in [stripWidth], for [tabCount] tabs of which [pinnedCount] are pinned ([pinnedWidth] in all).
 * [chipWidth] is the overflow chip with its count; [ghostWidth] the slot opened for a tab dragged in from another
 * window. [wasTooNarrow] is the previous result's [TabStripLayout.tooNarrow], for the hysteresis.
 */
internal fun computeTabStripLayout(
    stripWidth: Dp,
    tabCount: Int,
    pinnedCount: Int,
    pinnedWidth: Dp,
    chipWidth: Dp,
    ghostWidth: Dp,
    maxTabWidth: Dp,
    wasTooNarrow: Boolean,
): TabStripLayout {
    val unpinnedCount = (tabCount - pinnedCount).coerceAtLeast(1)
    val beforeDragArea = (stripWidth - AddButtonWithDividerWidth).coerceAtLeast(0.dp)
    // The drag area gives way, down to a sliver, before the last readable tab (and its chip, with others to list)
    // would.
    val chipBesideOneTab = if (unpinnedCount > 1) chipWidth else 0.dp
    val oneTabNeeds = pinnedWidth + MinReadableTabWidth + chipBesideOneTab
    val reservedDragArea = (beforeDragArea - oneTabNeeds).coerceIn(MinReservedDragArea, MaxReservedDragArea)
    val available = (beforeDragArea - reservedDragArea).coerceAtLeast(0.dp)
    val naturalTabWidth = (available - pinnedWidth - ghostWidth).coerceAtLeast(0.dp) / unpinnedCount
    // Decided without the drop ghost, so a tab hovering in from another window never reflows the strip under it,
    // and without the chip's room, so taking it can't drop the chip again.
    val widthWithoutGhost = (available - pinnedWidth).coerceAtLeast(0.dp) / unpinnedCount
    val threshold = if (wasTooNarrow) MinReadableTabWidth + OverflowHysteresis else MinReadableTabWidth
    val tooNarrow = widthWithoutGhost < threshold
    val roomForVisibleTabs = (available - pinnedWidth - ghostWidth - chipWidth).coerceAtLeast(0.dp)
    val currentTabAsIcon = tooNarrow && available - pinnedWidth - chipBesideOneTab < MinReadableTabWidth
    val fitCount = if (currentTabAsIcon) 0 else (roomForVisibleTabs / MinReadableTabWidth).toInt().coerceAtLeast(1)
    // The chip only stands for tabs it actually hides, never for none.
    val overflowing = tooNarrow && fitCount < unpinnedCount
    val visibleCount = if (overflowing) fitCount else unpinnedCount
    val tabWidth =
        when {
            currentTabAsIcon -> PinnedTabWidth
            overflowing -> (roomForVisibleTabs / visibleCount).coerceAtMost(maxTabWidth)
            else -> naturalTabWidth.coerceAtMost(maxTabWidth)
        }
    // Too narrow for the current tab's icon, the chevron and the + together: the + gives way.
    val showAddButton =
        !(currentTabAsIcon && tabCount > 1) ||
            stripWidth - reservedDragArea >= PinnedTabWidth + TabOverflowChevronWidth + AddButtonWithDividerWidth
    return TabStripLayout(
        reservedDragArea = reservedDragArea,
        naturalTabWidth = naturalTabWidth,
        tooNarrow = tooNarrow,
        currentTabAsIcon = currentTabAsIcon,
        overflowing = overflowing,
        // "N more" only beside readable tabs it adds to; beside the current tab's icon, the chevron alone.
        chipShowsCount = !currentTabAsIcon,
        visibleCount = visibleCount,
        tabWidth = tabWidth,
        showAddButton = showAddButton,
    )
}

/**
 * The least room the strip needs for [tabCount] tabs before the title bar takes it from its neighbours: the
 * current tab's icon and the chevron to the others, beside the drag sliver. Nothing for a lone tab, which only
 * ever needs its icon.
 */
internal fun tabStripMinWidth(tabCount: Int): Dp =
    if (tabCount > 1) PinnedTabWidth + TabOverflowChevronWidth + MinReservedDragArea else 0.dp

/**
 * The run of tabs the strip shows once they don't all fit: it keeps its place while the current tab is in it, and
 * shifts just enough to take it in otherwise, as a scrolled strip would. A plain field, bookkeeping only.
 */
internal class TabWindow {
    private var start = 0

    /** The [count] of [tabs] on show, around the one at [selected] (`-1`: none of them, the run stays). */
    fun <T> follow(
        tabs: List<T>,
        count: Int,
        selected: Int,
    ): List<T> {
        val size = count.coerceIn(0, tabs.size)
        var first = start.coerceIn(0, tabs.size - size)
        if (selected in 0 until first) first = selected
        if (selected >= first + size) first = selected - size + 1
        start = first
        return tabs.subList(first, first + size)
    }
}
