@file:OptIn(ExperimentalNucleusApi::class)

package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.RenderVectorGroup
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondary
import androidx.compose.ui.input.pointer.isTertiary
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.tao.LocalTaoWindow
import dev.nucleusframework.window.tao.TabDropGhost
import dev.nucleusframework.window.tao.TabGripCursor
import dev.nucleusframework.window.tao.TabHoverPreview
import dev.nucleusframework.window.tao.TabHoverPreviewPopup
import dev.nucleusframework.window.tao.TabHoverPreviewScope
import dev.nucleusframework.window.tao.TabPreview
import dev.nucleusframework.window.tao.TabStripScope
import dev.nucleusframework.window.tao.TaoWindow
import dev.nucleusframework.window.tao.dropGhost
import dev.nucleusframework.window.tao.rememberTabStripDrag
import dev.nucleusframework.window.tao.tabSlot
import dev.nucleusframework.window.tao.tabStripCarry
import dev.nucleusframework.window.tao.tabStripGeometry
import dev.nucleusframework.window.tao.tabStripGrip
import io.github.kdroidfilter.seforim.tabs.*
import io.github.kdroidfilter.seforimapp.core.deeplink.toShareLink
import io.github.kdroidfilter.seforimapp.core.presentation.components.ActionIcon
import io.github.kdroidfilter.seforimapp.core.presentation.components.BelowAnchorEndPositionProvider
import io.github.kdroidfilter.seforimapp.core.presentation.components.SharedIcon
import io.github.kdroidfilter.seforimapp.core.presentation.components.TitleBarActionButton
import io.github.kdroidfilter.seforimapp.core.presentation.utils.rememberCopyToClipboard
import io.github.kdroidfilter.seforimapp.core.presentation.window.TabDragGhostCard
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.icons.CloseAll
import io.github.kdroidfilter.seforimapp.icons.JournalBookmark
import io.github.kdroidfilter.seforimapp.icons.Link
import io.github.kdroidfilter.seforimapp.icons.NotebookPen
import io.github.kdroidfilter.seforimapp.icons.Tab_close
import io.github.kdroidfilter.seforimapp.icons.Tab_close_right
import io.github.kdroidfilter.seforimapp.icons.WritingHand
import io.github.kdroidfilter.seforimapp.icons.bookOpenTabs
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.component.styling.TooltipMetrics
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.hints.Stateful
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import org.jetbrains.jewel.ui.theme.menuStyle
import org.jetbrains.jewel.ui.theme.tooltipStyle
import seforimapp.seforimapp.generated.resources.*
import seforimapp.seforimapp.generated.resources.tab_move_to_desktop
import sh.calvin.reorderable.ReorderableColumn
import java.text.Bidi
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.ranges.coerceAtLeast
import kotlin.ranges.coerceAtMost
import kotlin.time.Duration.Companion.milliseconds

// Carry both TabData and its label for tooltips anchored on the whole tab container
private data class TabEntry(
    val key: String,
    val data: TabData,
    val labelProvider: @Composable () -> String,
    // By id, not index: a close run behind its exit animation (or a second click) must not hit the tab that has
    // taken its index since.
    val onClose: () -> Unit,
    val onClick: () -> Unit,
    val onCloseAll: () -> Unit,
    val onCloseOthers: () -> Unit,
    val onCloseLeft: () -> Unit,
    val onCloseRight: () -> Unit,
    // null when the destination has nothing shareable (e.g. Home, or a book still loading)
    val onCopyLink: (() -> Unit)?,
    // null when the tab is the window's only one (the window itself already is that tab)
    val onDetach: (() -> Unit)?,
    // Moves the tab to the desktop of that id; null for the window's only tab
    val onMoveToDesktop: ((String) -> Unit)?,
    val pinned: Boolean,
    // What the tab shows once pinned instead of its icon; null for the icon alone
    val pinnedLabel: String?,
    val onTogglePin: () -> Unit,
    // Its place on the strip, left to right as laid out (what "close left/right" counts from).
    val stripIndex: Int,
)

private val TabTooltipWidthThreshold = 140.dp
private val CompactTabWidthThreshold = 50.dp

// Chromium's kMinimumContentsWidthForCloseButtons: below it an inactive tab drops its close button,
// so the title always keeps some room next to it.
private val MinContentWidthForCloseButton = 68.dp
private val TabOuterHorizontalPadding = 2.dp

// A pinned tab showing a label grows to fit it, up to this; a longer one fades out.
private val PinnedLabelTabMaxWidth = 80.dp

// The pin marking a pinned tab, beside its icon or label.
private val PinMarkSize = 12.dp
private val PinMarkGap = 4.dp
private val PinMarkWidth = PinMarkSize + PinMarkGap

// The slide itself: a spring follows a target that keeps moving while the widths animate (LazyList's animateItem).
private val TabSlideSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)

// Width of the fade replacing the ellipsis on a cut title (Chromium's FADE_TAIL).
private val TitleFadeWidth = 24.dp

/** When true, [SingleLineTabContent] hides the label and shows only the icon. */
private val LocalCompactIconOnly = compositionLocalOf { false }

@Composable
fun TabsView() {
    val openWindow = LocalOpenWindow.current
    val viewModel: TabsViewModel = openWindow.tabsViewModel
    val state by viewModel.state.collectAsState()
    // The strip of this window's group: what tab drags, drops and tear-offs resolve against.
    val workspace = openWindow.session.workspace
    val group = openWindow.group() ?: return
    val stripScope =
        remember(workspace, group) {
            object : TabStripScope {
                override val workspace = workspace
                override val group = group
            }
        }
    with(stripScope) { DefaultTabShowcase(state = state, onEvents = viewModel::onEvent) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabStripScope.DefaultTabShowcase(
    onEvents: (TabsEvents) -> Unit,
    state: TabsState,
) {
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val desktopManager = LocalAppGraph.current.desktopManager
    val windowId = LocalOpenWindow.current.id
    val tabsViewModel = LocalOpenWindow.current.tabsViewModel
    val copyToClipboard = rememberCopyToClipboard()

    // Track for auto-scrolling (no-op in shrink-to-fit mode)
    var previousTabCount by remember { mutableStateOf(state.tabs.size) }
    val newTabAdded = state.tabs.size > previousTabCount
    LaunchedEffect(state.tabs.size) { previousTabCount = state.tabs.size }

    // Create TabData objects with RTL support
    val tabs: ImmutableList<TabEntry> =
        remember(state.tabs, state.selectedTabIndex, isRtl) {
            if (isRtl) {
                // For RTL: reverse the list and use the reversed index for display
                state.tabs
                    .reversed()
                    .mapIndexed { visualIndex, tabItem ->
                        // The actual index in the original list
                        val actualIndex = state.tabs.size - 1 - visualIndex
                        val isSelected = actualIndex == state.selectedTabIndex

                        val tabData =
                            TabData.Default(
                                selected = isSelected,
                                content = { tabState ->
                                    val icon: Painter =
                                        if (tabItem.tabType == TabType.BOOK) {
                                            rememberVectorPainter(bookOpenTabs(JewelTheme.contentColor))
                                        } else if (tabItem.tabType == TabType.HISTORY) {
                                            val iconProvider = rememberResourcePainterProvider(AllIconsKeys.Vcs.History)
                                            iconProvider.getPainter(Stateful(tabState)).value
                                        } else if (tabItem.tabType == TabType.FAVORITES) {
                                            val iconProvider = rememberResourcePainterProvider(AllIconsKeys.Nodes.Favorite)
                                            iconProvider.getPainter(Stateful(tabState)).value
                                        } else if (tabItem.tabType == TabType.NOTES) {
                                            rememberTintedVectorPainter(NotebookPen, JewelTheme.contentColor)
                                        } else if (tabItem.tabType == TabType.SIDDUR) {
                                            rememberTintedVectorPainter(JournalBookmark, JewelTheme.contentColor)
                                        } else if (tabItem.tabType == TabType.AUTHOR) {
                                            rememberTintedVectorPainter(WritingHand, JewelTheme.contentColor)
                                        } else {
                                            if (tabItem.title.isEmpty()) {
                                                rememberVectorPainter(
                                                    io.github.kdroidfilter.seforimapp.icons
                                                        .homeTabs(JewelTheme.contentColor),
                                                )
                                            } else {
                                                val iconProvider = rememberResourcePainterProvider(AllIconsKeys.Actions.Find)
                                                iconProvider.getPainter(Stateful(tabState)).value
                                            }
                                        }

                                    val appTitle = stringResource(Res.string.app_name)
                                    val label =
                                        when {
                                            tabItem.title.isEmpty() -> stringResource(Res.string.home_tab_with_app, appTitle)
                                            else -> tabItem.title
                                        }

                                    SingleLineTabContent(
                                        label = label,
                                        state = tabState,
                                        icon = icon,
                                    )
                                },
                                onClose = {},
                                onClick = {},
                            )

                        val labelProvider: @Composable () -> String = {
                            val appTitle = stringResource(Res.string.app_name)
                            when {
                                tabItem.title.isEmpty() -> stringResource(Res.string.home_tab_with_app, appTitle)
                                else -> tabItem.title
                            }
                        }

                        TabEntry(
                            key = tabItem.destination.tabId,
                            data = tabData,
                            labelProvider = labelProvider,
                            onClose = { workspace.close(tabItem.destination.tabId) },
                            onClick = { onEvents(TabsEvents.OnSelect(actualIndex)) },
                            onCloseAll = { onEvents(TabsEvents.CloseAll) },
                            onCloseOthers = { onEvents(TabsEvents.CloseOthers(actualIndex)) },
                            // RTL: visual "left" corresponds to higher indices (right of actual index)
                            onCloseLeft = { onEvents(TabsEvents.CloseRight(actualIndex)) },
                            onCloseRight = { onEvents(TabsEvents.CloseLeft(actualIndex)) },
                            onCopyLink = tabItem.destination.toShareLink()?.let { link -> { copyToClipboard(link) } },
                            onDetach =
                                if (state.tabs.size > 1) {
                                    { desktopManager.detachTabToNewWindow(tabItem.destination.tabId, windowId) }
                                } else {
                                    null
                                },
                            onMoveToDesktop =
                                if (state.tabs.size > 1) {
                                    { desktopId -> desktopManager.moveTabToDesktop(tabItem.destination.tabId, windowId, desktopId) }
                                } else {
                                    null
                                },
                            pinned = tabItem.pinned,
                            pinnedLabel = tabItem.pinnedLabel(),
                            onTogglePin = { tabsViewModel.setPinned(actualIndex, !tabItem.pinned) },
                            stripIndex = visualIndex,
                        )
                    }.toImmutableList()
            } else {
                // For LTR: use normal order
                state.tabs
                    .mapIndexed { index, tabItem ->
                        val isSelected = index == state.selectedTabIndex

                        val tabData =
                            TabData.Default(
                                selected = isSelected,
                                content = { tabState ->
                                    val icon: Painter =
                                        if (tabItem.tabType == TabType.BOOK) {
                                            rememberVectorPainter(bookOpenTabs(JewelTheme.globalColors.text.normal))
                                        } else if (tabItem.tabType == TabType.HISTORY) {
                                            val iconProvider = rememberResourcePainterProvider(AllIconsKeys.Vcs.History)
                                            iconProvider.getPainter(Stateful(tabState)).value
                                        } else if (tabItem.tabType == TabType.FAVORITES) {
                                            val iconProvider = rememberResourcePainterProvider(AllIconsKeys.Nodes.Favorite)
                                            iconProvider.getPainter(Stateful(tabState)).value
                                        } else if (tabItem.tabType == TabType.NOTES) {
                                            rememberTintedVectorPainter(NotebookPen, JewelTheme.globalColors.text.normal)
                                        } else if (tabItem.tabType == TabType.SIDDUR) {
                                            rememberTintedVectorPainter(JournalBookmark, JewelTheme.globalColors.text.normal)
                                        } else if (tabItem.tabType == TabType.AUTHOR) {
                                            rememberTintedVectorPainter(WritingHand, JewelTheme.globalColors.text.normal)
                                        } else {
                                            if (tabItem.title.isEmpty()) {
                                                rememberVectorPainter(
                                                    io.github.kdroidfilter.seforimapp.icons
                                                        .homeTabs(JewelTheme.globalColors.text.normal),
                                                )
                                            } else {
                                                val iconProvider = rememberResourcePainterProvider(AllIconsKeys.Actions.Find)
                                                iconProvider.getPainter(Stateful(tabState)).value
                                            }
                                        }

                                    val appTitle = stringResource(Res.string.app_name)
                                    val label =
                                        when {
                                            tabItem.title.isEmpty() -> stringResource(Res.string.home_tab_with_app, appTitle)
                                            else -> tabItem.title
                                        }

                                    SingleLineTabContent(
                                        label = label,
                                        state = tabState,
                                        icon = icon,
                                    )
                                },
                                onClose = {},
                                onClick = {},
                            )

                        val labelProvider: @Composable () -> String = {
                            val appTitle = stringResource(Res.string.app_name)
                            when {
                                tabItem.title.isEmpty() -> stringResource(Res.string.home_tab_with_app, appTitle)
                                else -> tabItem.title
                            }
                        }

                        TabEntry(
                            key = tabItem.destination.tabId,
                            data = tabData,
                            labelProvider = labelProvider,
                            onClose = { workspace.close(tabItem.destination.tabId) },
                            onClick = { onEvents(TabsEvents.OnSelect(index)) },
                            onCloseAll = { onEvents(TabsEvents.CloseAll) },
                            onCloseOthers = { onEvents(TabsEvents.CloseOthers(index)) },
                            onCloseLeft = { onEvents(TabsEvents.CloseLeft(index)) },
                            onCloseRight = { onEvents(TabsEvents.CloseRight(index)) },
                            onCopyLink = tabItem.destination.toShareLink()?.let { link -> { copyToClipboard(link) } },
                            onDetach =
                                if (state.tabs.size > 1) {
                                    { desktopManager.detachTabToNewWindow(tabItem.destination.tabId, windowId) }
                                } else {
                                    null
                                },
                            onMoveToDesktop =
                                if (state.tabs.size > 1) {
                                    { desktopId -> desktopManager.moveTabToDesktop(tabItem.destination.tabId, windowId, desktopId) }
                                } else {
                                    null
                                },
                            pinned = tabItem.pinned,
                            pinnedLabel = tabItem.pinnedLabel(),
                            onTogglePin = { tabsViewModel.setPinned(index, !tabItem.pinned) },
                            stripIndex = index,
                        )
                    }.toImmutableList()
            }
        }

    RtlAwareTabStripWithAddButton(
        tabs = tabs,
        style = JewelTheme.defaultTabStyle,
        isRtl = isRtl,
        newTabAdded = newTabAdded,
        onReorder = { fromIndex, toIndex ->
            // For RTL, indices are already in visual order (reversed list),
            // so we need to convert back to actual indices
            val actualFrom = if (isRtl) state.tabs.size - 1 - fromIndex else fromIndex
            val actualTo = if (isRtl) state.tabs.size - 1 - toIndex else toIndex
            onEvents(TabsEvents.OnReorder(actualFrom, actualTo))
        },
    ) {
        onEvents(TabsEvents.OnAdd)
    }
}

@Composable
private fun TabStripScope.RtlAwareTabStripWithAddButton(
    tabs: ImmutableList<TabEntry>,
    style: TabStyle,
    isRtl: Boolean,
    newTabAdded: Boolean,
    onReorder: (Int, Int) -> Unit,
    onAddClick: () -> Unit,
) {
    // Shrink-to-fit mode: no horizontal scroll, tabs adjust width.

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RtlAwareTabStripContent(
            tabs = tabs,
            style = style,
            onAddClick = onAddClick,
            onReorder = onReorder,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TabStripScope.RtlAwareTabStripContent(
    tabs: ImmutableList<TabEntry>,
    style: TabStyle,
    onAddClick: () -> Unit,
    onReorder: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val interactionSource = remember { MutableInteractionSource() }
    var isActive by remember { mutableStateOf(false) }

    // Keep track of tabs that are animating out before we notify the ViewModel
    var closingKeys by remember { mutableStateOf(setOf<String>()) }
    val exitDurationMs = 200
    val enterDurationMs = 200
    val containerTransitionDurationMs = enterDurationMs + 80

    // Track which tabs already existed to avoid double width + expand animation on new entries
    val openWindow = LocalOpenWindow.current
    val tabsViewModel = openWindow.tabsViewModel
    // The workspace's gestures for this strip: carry along it, slide home, hand-over to another
    // window or a new one once a tab leaves it (the platform drag session on native Wayland).
    val tabDrag = rememberTabStripDrag()
    val taoWindow = LocalTaoWindow.current
    val openWindowCount =
        LocalAppGraph.current.desktopManager.windows
            .collectAsState()
            .value.size
    TabHoverPreviewPopup(remember { TabHoverPreview(content = { TabPreviewCard() }) })
    // The last look at the selected tab before a click may leave it (see rememberTabThumbnails).
    val stripHovered by interactionSource.collectIsHoveredAsState()
    // Chromium's tab closing mode: closing tabs with the mouse keeps the others at their width, so the
    // next close button lands under the pointer; they reflow once it leaves the strip or a tab is added.
    var frozenTabWidth by remember { mutableStateOf<Dp?>(null) }
    LaunchedEffect(stripHovered) {
        openWindow.pointerOnStrip = stripHovered
        if (!stripHovered) frozenTabWidth = null
    }
    var tabCount by remember { mutableIntStateOf(tabs.size) }
    LaunchedEffect(tabs.size) {
        if (tabs.size > tabCount) frozenTabWidth = null
        tabCount = tabs.size
    }
    // Chrome-like: the tab being dragged is selected.
    LaunchedEffect(tabDrag.held) { tabDrag.held?.let(workspace::select) }
    val skipAnimation by tabsViewModel.skipNextAnimation.collectAsState()
    var knownKeys by remember { mutableStateOf(tabs.map { it.key }.toSet()) }
    val currentKeys = remember(tabs) { tabs.map { it.key } }

    // When desktop switch restores tabs, treat all as already known to skip enter animation
    var skipContainerAnimation by remember { mutableStateOf(false) }
    if (skipAnimation) {
        knownKeys = currentKeys.toSet()
        skipContainerAnimation = true
        tabsViewModel.consumeSkipAnimation()
    }

    // A reorder not made by a drag (a pin, the overflow menu, a move between windows) slides the tabs it moved
    // rather than jump them; a drag carries its own. Adds and closes have their own enter and exit animations.
    val stripOrder = remember { StripOrder(currentKeys) }
    // Read by the tabs' placement only, so bumping it here recomposes nothing.
    val slideGeneration = remember { mutableIntStateOf(0) }
    // Only the restore that asked for no animation is spared, not the moments after it.
    if (stripOrder.reorderedTo(currentKeys, dragging = tabDrag.held != null || tabDrag.animating != null) && !skipAnimation) {
        slideGeneration.intValue++
    }

    val scope = rememberCoroutineScope()

    fun closeTabWithAnimation(
        @StructuredScope scope: CoroutineScope,
        delayMs: Long,
        onClose: () -> Unit,
        onFinished: () -> Unit,
    ) {
        scope.launch {
            delay(delayMs)
            onClose()
            onFinished()
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val maxWidthDp = this.maxWidth
        // Pinned tabs keep their narrow width; the others share what is left (Chromium's layout).
        val pinnedWidths = rememberPinnedTabWidths(tabs, style)
        val pinnedCount = pinnedWidths.size
        val pinnedTotal = pinnedWidths.values.fold(0.dp) { sum, width -> sum + width }
        // Sized for the most tabs it could ever count, so the + never gets pushed out.
        val chipWidth = rememberTabOverflowChipWidth(tabs.size)
        // A tab dragged in from another window opens a slot where it would drop; the others make room.
        val ghost = dropGhost?.takeIf { tabDrag.animating == null }
        val ghostWidth = ghost?.width ?: 0.dp
        // When the tab lands, its slot vanishes at once rather than shutting beside it (Nucleus's TabStrip).
        val landing = remember(group) { DropLanding() }
        workspace.draggedTab?.let { dragged -> if (ghost != null) landing.expect(dragged.id, ghost.index) }
        if (ghost == null) landing.settle(group.ids)
        // Chrome-like: tabs shrink to fill available width, capped by a max width
        val maxTabWidth = AppSettings.TAB_FIXED_WIDTH_DP.dp
        val overflowMemory = remember { OverflowMemory() }
        val layout =
            computeTabStripLayout(
                stripWidth = maxWidthDp,
                tabCount = tabs.size,
                pinnedCount = pinnedCount,
                pinnedWidth = pinnedTotal,
                chipWidth = chipWidth,
                ghostWidth = ghostWidth,
                maxTabWidth = maxTabWidth,
                wasTooNarrow = overflowMemory.tooNarrow,
            )
        overflowMemory.tooNarrow = layout.tooNarrow
        val reservedDragArea = layout.reservedDragArea
        val naturalTabWidth = layout.naturalTabWidth
        val noTabFits = layout.currentTabAsIcon
        val showOverflowMenu = layout.overflowing
        val visibleCount = layout.visibleCount
        val tabWindow = remember { TabWindow() }
        val visibleKeys =
            remember(tabs, pinnedWidths, visibleCount, showOverflowMenu) {
                if (!showOverflowMenu) {
                    null
                } else if (visibleCount == 0) {
                    // The current tab always stays, as its icon at least.
                    setOfNotNull(tabs.firstOrNull { it.data.selected }?.key)
                } else {
                    // In reading order, as the strip shows them.
                    val unpinned = tabs.filter { it.key !in pinnedWidths }
                    pinnedWidths.keys +
                        tabWindow.follow(unpinned, visibleCount, unpinned.indexOfFirst { it.data.selected }).map { it.key }.toSet()
                }
            }
        // The only tab on show has nowhere to go along the strip: dragging it moves the window, as the title bar
        // does. Where the app places its windows, a window's real only tab needs nothing: Nucleus already carries
        // the window with it, and merges it into the strip it is dropped on. On native Wayland the window can't
        // follow a drag, so the tab keeps its drag-and-drop, the one way into another window's strip; with no other
        // window open there is none to reach, and it moves the window too. Without a window, nothing to move.
        val movesWindow =
            taoWindow != null &&
                (visibleKeys?.size ?: tabs.size) == 1 &&
                if (taoWindow.canPlaceOnScreen) tabs.size > 1 else openWindowCount == 1
        val tabWidth = frozenTabWidth?.coerceAtMost(layout.tabWidth) ?: layout.tabWidth

        // The whole strip is the drop target of a tab dragged from another window (Chrome accepts
        // drops on the whole strip area). Published left to right: the tabs are laid out in their
        // logical order from the left edge, whatever the app's direction.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.matchParentSize().tabStripGeometry(workspace, group))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Use hysteresis around the threshold to avoid flicker/glitch when toggling modes
            var shrinkToFitActive by remember { mutableStateOf(false) }
            val hysteresis = 6.dp
            LaunchedEffect(naturalTabWidth, maxTabWidth) {
                if (!shrinkToFitActive && naturalTabWidth < (maxTabWidth - hysteresis)) {
                    shrinkToFitActive = true
                } else if (shrinkToFitActive && naturalTabWidth > (maxTabWidth + hysteresis)) {
                    shrinkToFitActive = false
                }
            }

            // Only animate the tabs container size when shrink-to-fit mode toggles,
            // so we keep the + button fixed while the bar is full, and avoid extra motion
            // when closing tabs in non-shrink mode.
            var lastShrinkToFitActive by remember { mutableStateOf(shrinkToFitActive) }
            val shouldAnimateTabsContainer = shrinkToFitActive != lastShrinkToFitActive
            val tabsContainerAnimationSpec: FiniteAnimationSpec<IntSize> =
                if (shouldAnimateTabsContainer && !skipContainerAnimation) {
                    tween(durationMillis = containerTransitionDurationMs, easing = FastOutSlowInEasing)
                } else {
                    snap()
                }
            LaunchedEffect(skipContainerAnimation) {
                if (skipContainerAnimation) {
                    delay(containerTransitionDurationMs.toLong())
                    skipContainerAnimation = false
                }
            }
            SideEffect {
                lastShrinkToFitActive = shrinkToFitActive
            }

            // Helper to render all tab items with animations and reordering support
            val tabsOnly: @Composable RowScope.() -> Unit = {
                val reorderingEnabled = closingKeys.isEmpty()
                val groupIndex = group.ids.withIndex().associate { (i, id) -> id to i }
                val rowModifier = if (shrinkToFitActive && !showOverflowMenu) Modifier.fillMaxWidth() else Modifier
                val tabEntriesByKey = remember(tabs) { tabs.associateBy { it.key } }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = rowModifier,
                ) {
                    // Gap i is before logical tab i, logical 0 at the left: in RTL the keys come reversed, so the
                    // gap follows its tab here, and the one after the last tab comes first.
                    key(landing.generation) { if (isRtl) DropGhostSlot(ghost, currentKeys.size) }
                    currentKeys.forEachIndexed { index, key ->
                        key(landing.generation, "gap", index) { if (!isRtl) DropGhostSlot(ghost, index) }
                        val tabEntry = tabEntriesByKey[key] ?: return@forEachIndexed
                        val workspaceTab = workspace.tab(key) ?: return@forEachIndexed
                        // Keyed outside the visible/hidden branch, so a tab moving along the strip keeps its composition.
                        key(key) {
                            if (visibleKeys == null || tabEntry.key in visibleKeys) {
                                val isClosing = closingKeys.contains(tabEntry.key)
                                val isNew = !knownKeys.contains(tabEntry.key)
                                val slide = remember { TabSlide() }

                                Box(
                                    modifier =
                                        Modifier
                                            // Carried or sliding home, it is drawn over its neighbours; sliding after a
                                            // reorder, over those that travel less.
                                            .zIndex(if (tabDrag.animating == key) Float.MAX_VALUE else slide.lift)
                                            .then(
                                                // A tab closing (or just moved away) is no longer in the group.
                                                if (!isClosing && key in groupIndex && movesWindow) {
                                                    Modifier.tabSlot(group, groupIndex.getValue(key))
                                                } else if (reorderingEnabled && !isClosing && key in groupIndex) {
                                                    Modifier.tabStripGrip(
                                                        this@RtlAwareTabStripContent,
                                                        tabDrag,
                                                        workspaceTab,
                                                        groupIndex.getValue(key),
                                                        // The strip keeps the plain arrow over its tabs.
                                                        cursor = TabGripCursor.None,
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                ) {
                                    var visible by remember(isClosing) { mutableStateOf(!isClosing) }
                                    LaunchedEffect(isClosing) {
                                        visible = !isClosing
                                    }
                                    // The tab as drawn and clicked slides; its slot and grip stay where drops resolve.
                                    Row(Modifier.tabStripCarry(tabDrag, workspaceTab).slideToNewPlace(slide, slideGeneration)) {
                                        AnimatedVisibility(
                                            visible = visible,
                                            exit =
                                                shrinkHorizontally(
                                                    animationSpec = tween(durationMillis = exitDurationMs),
                                                    shrinkTowards = Alignment.Start,
                                                ) + fadeOut(animationSpec = tween(exitDurationMs, easing = LinearEasing)),
                                        ) {
                                            RtlAwareTab(
                                                isActive = isActive,
                                                tabData = tabEntry.data,
                                                tabStyle = style,
                                                tabIndex = index,
                                                tabCount = tabs.size,
                                                tabWidth = pinnedWidths[tabEntry.key] ?: tabWidth,
                                                pinned = tabEntry.pinned,
                                                pinnedLabel = tabEntry.pinnedLabel,
                                                onTogglePin = tabEntry.onTogglePin,
                                                labelProvider = tabEntry.labelProvider,
                                                onClick = tabEntry.onClick,
                                                onClose = {
                                                    if (stripHovered && frozenTabWidth == null) frozenTabWidth = tabWidth
                                                    if (!closingKeys.contains(tabEntry.key)) {
                                                        closingKeys = closingKeys + tabEntry.key
                                                        closeTabWithAnimation(
                                                            scope = scope,
                                                            delayMs = exitDurationMs.toLong(),
                                                            onClose = tabEntry.onClose,
                                                            onFinished = { closingKeys = closingKeys - tabEntry.key },
                                                        )
                                                    }
                                                },
                                                onCloseAll = tabEntry.onCloseAll,
                                                onCloseOthers = tabEntry.onCloseOthers,
                                                onCloseLeft = tabEntry.onCloseLeft,
                                                onCloseRight = tabEntry.onCloseRight,
                                                onCopyLink = tabEntry.onCopyLink,
                                                onDetach = tabEntry.onDetach,
                                                onMoveToDesktop = tabEntry.onMoveToDesktop,
                                                animateWidth = !isNew,
                                                enterFromSmall = isNew,
                                                enterDurationMs = enterDurationMs,
                                                isDragging = tabDrag.held == key,
                                                windowToMove = if (movesWindow) taoWindow else null,
                                                iconOnly = noTabFits,
                                            )
                                        }
                                    }
                                }
                            } else if (key in groupIndex) {
                                // A hidden tab keeps an empty slot at its place, so drags and drops along the strip
                                // still resolve against the whole group.
                                Box(Modifier.tabSlot(group, groupIndex.getValue(key)))
                            }
                        }
                        key(landing.generation, "gap", index) { if (isRtl) DropGhostSlot(ghost, currentKeys.size - 1 - index) }
                    }
                    key(landing.generation) { if (!isRtl) DropGhostSlot(ghost, currentKeys.size) }
                }
            }

            // Tabs container: fills available width when shrink-to-fit is active,
            // so the + button stays fixed at the trailing edge.
            Row(
                modifier =
                    Modifier
                        .let { baseModifier ->
                            // Overflowing, the + follows the current tab and its chip, as when the tabs are few.
                            if (shrinkToFitActive && !showOverflowMenu) {
                                baseModifier.weight(1f)
                            } else if (showOverflowMenu) {
                                // Measured after the + and the drag area, so on a strip too narrow even for the
                                // current tab's icon and the chevron, those give way rather than the +.
                                baseModifier.weight(1f, fill = false)
                            } else {
                                baseModifier
                            }
                        }.hoverable(interactionSource)
                        .animateContentSize(animationSpec = tabsContainerAnimationSpec),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabsOnly()
                // A window's only tab, with no room left for it, just isn't shown: a menu of one tab would be noise.
                if (showOverflowMenu && tabs.size > 1) {
                    TabOverflowChip(
                        hiddenTabs = tabs.filter { visibleKeys != null && it.key !in visibleKeys },
                        tabCount = tabs.size,
                        height = style.metrics.tabHeight,
                        showCount = layout.chipShowsCount,
                        // Onto the place of the hidden tab it was dropped on, in the whole strip.
                        onMove = { fromKey, toKey ->
                            onReorder(tabs.indexOfFirst { it.key == fromKey }, tabs.indexOfFirst { it.key == toKey })
                        },
                        // Only while the strip has no room for its own +.
                        onAdd = if (layout.showAddButton) null else onAddClick,
                    )
                }
            }

            if (layout.showAddButton) {
                Divider(
                    orientation = Orientation.Vertical,
                    modifier =
                        Modifier
                            .fillMaxHeight(0.8f)
                            .padding(horizontal = 4.dp)
                            .width(1.dp),
                    color = JewelTheme.globalColors.borders.disabled,
                )

                TitleBarActionButton(
                    onClick = onAddClick,
                    key = AllIconsKeys.General.Add,
                    contentDescription = stringResource(Res.string.add_tab),
                    tooltipText = stringResource(Res.string.add_tab),
                    shortcutHint = NewTabShortcutHint,
                )
            }

            // Reserved draggable area — intentionally empty/non-interactive
            Spacer(modifier = Modifier.width(reservedDragArea).fillMaxHeight())
        }
    }

    // Update known keys after composition so new tabs are only flagged once
    LaunchedEffect(currentKeys) {
        val incoming = currentKeys.toSet()
        // If there are newly added keys, keep them marked as new long enough to finish the enter animation
        val hasNew = !knownKeys.containsAll(incoming)
        if (hasNew) delay(enterDurationMs.toLong())
        knownKeys = incoming
    }
}

// Custom Tab implementation based on Jewel's internal TabImpl
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun RtlAwareTab(
    isActive: Boolean,
    tabData: TabData,
    tabStyle: TabStyle,
    tabIndex: Int,
    tabCount: Int,
    tabWidth: Dp,
    pinned: Boolean,
    pinnedLabel: String?,
    onTogglePin: () -> Unit,
    labelProvider: @Composable () -> String,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onCloseAll: () -> Unit,
    onCloseOthers: () -> Unit,
    onCloseLeft: () -> Unit,
    onCloseRight: () -> Unit,
    modifier: Modifier = Modifier,
    onCopyLink: (() -> Unit)? = null,
    onDetach: (() -> Unit)? = null,
    onMoveToDesktop: ((String) -> Unit)? = null,
    animateWidth: Boolean = true,
    enterFromSmall: Boolean = false,
    enterDurationMs: Int = 200,
    isDragging: Boolean = false,
    // Set when dragging this tab moves the window rather than the tab.
    windowToMove: TaoWindow? = null,
    // Drawn as its icon alone, as a pinned tab is: the current tab once no readable one fits.
    iconOnly: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var tabState by remember { mutableStateOf(TabState.of(selected = tabData.selected, active = isActive)) }

    remember(tabData.selected, isActive) {
        tabState = tabState.copy(selected = tabData.selected, active = isActive)
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> tabState = tabState.copy(pressed = true)
                is PressInteraction.Cancel,
                is PressInteraction.Release,
                -> tabState = tabState.copy(pressed = false)

                is HoverInteraction.Enter -> tabState = tabState.copy(hovered = true)
                is HoverInteraction.Exit -> tabState = tabState.copy(hovered = false)
            }
        }
    }

    var closeButtonState by remember(isActive) { mutableStateOf(ButtonState.of(active = isActive)) }
    val dragAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.7f else 1f,
        animationSpec = tween(durationMillis = 150),
    )
    val accent = JewelTheme.globalColors.outlines.focused
    val backgroundColor =
        when {
            tabState.isSelected -> accent.copy(alpha = 0.20f)
            tabState.isPressed -> accent.copy(alpha = 0.18f)
            tabState.isHovered -> accent.copy(alpha = 0.10f)
            else -> Color.Transparent
        }
    val resolvedContentColor =
        tabStyle.colors
            .contentFor(tabState)
            .value
            .takeOrElse { LocalContentColor.current }

    CompositionLocalProvider(LocalContentColor provides resolvedContentColor) {
        val animatedWidth by animateDpAsState(
            targetValue = tabWidth,
            animationSpec = tween(durationMillis = 200),
        )
        // For brand-new tabs, start from a small width and grow smoothly to target
        val minEnterWidth = minOf(72.dp, tabWidth)
        val widthAnim =
            remember(enterFromSmall) {
                if (enterFromSmall) Animatable(minEnterWidth, Dp.VectorConverter) else null
            }
        LaunchedEffect(enterFromSmall, tabWidth) {
            if (enterFromSmall) {
                widthAnim?.snapTo(minEnterWidth)
                widthAnim?.animateTo(tabWidth, animationSpec = tween(durationMillis = enterDurationMs, easing = FastOutSlowInEasing))
            }
        }
        val widthForThisTab =
            when {
                enterFromSmall -> widthAnim?.value ?: tabWidth
                animateWidth -> animatedWidth
                else -> tabWidth
            }
        var anchorOffset by remember { mutableStateOf(IntOffset.Zero) }
        var anchorSize by remember { mutableStateOf(IntSize.Zero) }
        var contextMenuOpen by remember { mutableStateOf(false) }
        var contextClickOffset by remember { mutableStateOf(IntOffset.Zero) }

        val tabPadding = tabStyle.metrics.tabPadding
        val contentWidth =
            tabWidth - TabOuterHorizontalPadding * 2 -
                tabPadding.calculateLeftPadding(LayoutDirection.Ltr) - tabPadding.calculateRightPadding(LayoutDirection.Ltr)
        // A pinned tab never shows its close button (Chromium's Tab::UpdateIconVisibility).
        val asIcon = pinned || iconOnly
        val roomForCloseButton = !asIcon && contentWidth >= MinContentWidthForCloseButton

        val container: @Composable () -> Unit = {
            Row(
                modifier
                    .height(tabStyle.metrics.tabHeight)
                    .width(widthForThisTab)
                    .padding(horizontal = TabOuterHorizontalPadding, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(backgroundColor)
                    .alpha(dragAlpha)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        anchorOffset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                        anchorSize = coords.size
                    }.selectable(
                        onClick = onClick,
                        selected = tabData.selected,
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Tab,
                    ) // Inside the click, so a press the close button took never reaches the window move.
                    .windowMoveGrip(windowToMove)
                    .padding(tabStyle.metrics.tabPadding)
                    .onPointerEvent(PointerEventType.Release) { ev ->
                        // Middle-click closes tab (Chrome-like), but not a pinned one: pinning guards against that.
                        if (ev.button.isTertiary && !pinned) onClose()
                        // Right-click opens context menu
                        if (ev.button.isSecondary) {
                            val p = ev.changes.firstOrNull()?.position ?: Offset.Zero
                            contextClickOffset = IntOffset(p.x.roundToInt(), p.y.roundToInt())
                            contextMenuOpen = true
                            ev.changes.forEach { it.consume() }
                        }
                    },
                horizontalArrangement =
                    if (roomForCloseButton || tabData.selected) {
                        Arrangement.spacedBy(tabStyle.metrics.closeContentGap)
                    } else {
                        Arrangement.spacedBy(0.dp)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val isCompact = tabWidth < CompactTabWidthThreshold
                val isSelected = tabData.selected
                // Hide close for non-selected tabs when space is tight, always show for selected
                // Shrunk to its icon, the current tab trades it for its close button under the pointer.
                val showCloseIcon =
                    tabData.closable &&
                        !pinned &&
                        if (iconOnly) tabState.isHovered else isSelected || roomForCloseButton

                val closeIconComposable: @Composable () -> Unit = {
                    if (showCloseIcon) {
                        val closeActionInteractionSource = remember { MutableInteractionSource() }
                        LaunchedEffect(closeActionInteractionSource) {
                            closeActionInteractionSource.interactions.collect { interaction ->
                                when (interaction) {
                                    is PressInteraction.Press -> closeButtonState = closeButtonState.copy(pressed = true)
                                    is PressInteraction.Cancel,
                                    is PressInteraction.Release,
                                    -> closeButtonState = closeButtonState.copy(pressed = false)
                                    is HoverInteraction.Enter -> closeButtonState = closeButtonState.copy(hovered = true)
                                    is HoverInteraction.Exit -> closeButtonState = closeButtonState.copy(hovered = false)
                                }
                            }
                        }

                        SharedIcon(
                            key = tabStyle.icons.close,
                            modifier =
                                Modifier
                                    .clickable(
                                        interactionSource = closeActionInteractionSource,
                                        indication = null,
                                        onClick = onClose,
                                        role = Role.Button,
                                    ).size(16.dp),
                            contentDescription = stringResource(Res.string.close_tab),
                            hints = *arrayOf(Stateful(closeButtonState)),
                        )
                    }
                }

                // In compact mode, selected tab shows only close icon (centered)
                if ((isCompact && isSelected && !asIcon) || (iconOnly && showCloseIcon)) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        closeIconComposable()
                    }
                } else if (pinned) {
                    // A pin, then the tab's label (a book's or author's icon is every book's) or its icon.
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(PinMarkGap, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            key = AllIconsKeys.General.Pin_tab,
                            contentDescription = stringResource(Res.string.pinned_tab),
                            modifier = Modifier.size(PinMarkSize),
                        )
                        if (pinnedLabel != null) {
                            SingleLineTabContent(
                                label = pinnedLabel,
                                state = tabState,
                                icon = null,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        } else {
                            CompositionLocalProvider(LocalCompactIconOnly provides true) {
                                tabData.content(TabContentScopeContainer(), tabState)
                            }
                        }
                    }
                } else {
                    val showIconOnly = isCompact || asIcon
                    // A tab shrunk to its icon shows it centred.
                    Box(Modifier.weight(1f), contentAlignment = if (asIcon) Alignment.Center else Alignment.TopStart) {
                        CompositionLocalProvider(LocalCompactIconOnly provides showIconOnly) {
                            tabData.content(TabContentScopeContainer(), tabState)
                        }
                    }
                    closeIconComposable()
                }
            }
        }

        val label = labelProvider()
        // The hover card replaces the tooltip on every tab it is shown for (all but the selected one).
        val showTooltip =
            label.isNotBlank() &&
                (label.length > AppSettings.MAX_TAB_TITLE_LENGTH || tabWidth < TabTooltipWidthThreshold) &&
                tabData.selected

        // Read theme colors outside remember so they act as a cache key.
        val tooltipColors = JewelTheme.tooltipStyle.colors
        val chromeTooltipStyle =
            remember(tooltipColors) {
                // Positions the tooltip centered below the anchor (the tab) — Chrome style.
                // ComponentRect relies on LayoutBoundsHolder which isn't wired in JewelTooltipArea,
                // so we use a raw PopupPositionProvider that reads anchorBounds directly.
                @OptIn(ExperimentalFoundationApi::class)
                val belowAnchorPlacement =
                    object : TooltipPlacement {
                        @Composable
                        override fun positionProvider(cursorPosition: Offset): PopupPositionProvider =
                            object : PopupPositionProvider {
                                override fun calculatePosition(
                                    anchorBounds: IntRect,
                                    windowSize: IntSize,
                                    layoutDirection: LayoutDirection,
                                    popupContentSize: IntSize,
                                ): IntOffset =
                                    IntOffset(
                                        x =
                                            (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
                                                .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                                        y = anchorBounds.bottom,
                                    )
                            }
                    }
                TooltipStyle(
                    colors = tooltipColors,
                    metrics =
                        TooltipMetrics.defaults(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            cornerSize = CornerSize(8.dp),
                            shadowSize = 16.dp,
                            placement = belowAnchorPlacement,
                            regularDisappearDelay = 5000.milliseconds,
                            fullDisappearDelay = 15000.milliseconds,
                        ),
                )
            }

        Tooltip(
            tooltip = {
                Text(
                    text = label,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.widthIn(max = 360.dp),
                )
            },
            enabled = showTooltip,
            style = chromeTooltipStyle,
        ) { container() }

        if (contextMenuOpen) {
            TabActionsMenu(
                anchorOffset = anchorOffset,
                contextClickOffset = contextClickOffset,
                tabIndex = tabIndex,
                tabCount = tabCount,
                pinned = pinned,
                onTogglePin = onTogglePin,
                onDetach = onDetach,
                onMoveToDesktop = onMoveToDesktop,
                onCopyLink = onCopyLink,
                onCloseAll = onCloseAll,
                onCloseOthers = onCloseOthers,
                onCloseLeft = onCloseLeft,
                onCloseRight = onCloseRight,
                onDismiss = { contextMenuOpen = false },
            )
        }
    }
}

/**
 * Stands for the tabs the strip has no room for: shaped like a tab, it tells how many there are and lists them, in
 * their order (IntelliJ's hidden tabs).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TabOverflowChip(
    hiddenTabs: List<TabEntry>,
    tabCount: Int,
    height: Dp,
    showCount: Boolean,
    onMove: (fromKey: String, toKey: String) -> Unit,
    onAdd: (() -> Unit)?,
) {
    var visible by remember { mutableStateOf(false) }
    var contextMenuOpen by remember { mutableStateOf(false) }
    var contextClickOffset by remember { mutableStateOf(IntOffset.Zero) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val accent = JewelTheme.globalColors.outlines.focused
    val background =
        when {
            visible || contextMenuOpen -> accent.copy(alpha = 0.20f)
            hovered -> accent.copy(alpha = 0.10f)
            else -> Color.Transparent
        }
    val label = stringResource(Res.string.more_tabs, hiddenTabs.size)
    Box {
        Row(
            modifier =
                Modifier
                    .height(height)
                    .padding(horizontal = TabOuterHorizontalPadding, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(background)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.DropdownList,
                        onClickLabel = stringResource(Res.string.open_tabs),
                        onClick = { visible = !visible },
                    ).onPointerEvent(PointerEventType.Release) { ev ->
                        if (ev.button.isSecondary) {
                            val p = ev.changes.firstOrNull()?.position ?: Offset.Zero
                            contextClickOffset = IntOffset(p.x.roundToInt(), p.y.roundToInt())
                            contextMenuOpen = true
                            ev.changes.forEach { it.consume() }
                        }
                    }.padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showCount) Text(label, maxLines = 1)
            Icon(AllIconsKeys.General.ChevronDown, contentDescription = if (showCount) null else label, modifier = Modifier.size(16.dp))
        }
        if (contextMenuOpen) {
            // Resource strings must be resolved outside MenuScope
            val closeHiddenLabel = stringResource(Res.string.close_hidden_tabs)
            val closeAllLabel = stringResource(Res.string.close_all_tabs)
            TabContextMenu(
                anchorOffset = null,
                contextClickOffset = contextClickOffset,
                onDismissRequest = { contextMenuOpen = false },
            ) {
                // With no tab on show, the hidden ones are all of them: "close all" says it already.
                if (hiddenTabs.size < tabCount) {
                    tabContextMenuItem(
                        label = closeHiddenLabel,
                        icon = Tab_close,
                        onClick = {
                            contextMenuOpen = false
                            // By id each: closing one doesn't shift the next.
                            hiddenTabs.filterNot { it.pinned }.forEach { it.onClose() }
                        },
                    )
                }
                tabContextMenuItem(
                    label = closeAllLabel,
                    icon = CloseAll,
                    onClick = {
                        contextMenuOpen = false
                        hiddenTabs.firstOrNull()?.onCloseAll?.invoke()
                    },
                )
            }
        }
        if (visible) {
            Popup(
                onDismissRequest = { visible = false },
                popupPositionProvider = BelowAnchorEndPositionProvider,
                properties = PopupProperties(focusable = true),
            ) {
                TabOverflowMenu(
                    tabs = hiddenTabs,
                    tabCount = tabCount,
                    onSelect = { tab ->
                        visible = false
                        tab.onClick()
                    },
                    onMove = onMove,
                    onAdd =
                        onAdd?.let { add ->
                            {
                                visible = false
                                add()
                            }
                        },
                )
            }
        }
    }
}

/** The hidden tabs, as the workspaces' menu lists its desktops: dragged to reorder, closed from their ×. */
@Composable
private fun TabOverflowMenu(
    tabs: List<TabEntry>,
    tabCount: Int,
    onSelect: (TabEntry) -> Unit,
    onMove: (fromKey: String, toKey: String) -> Unit,
    // null while the strip shows its own +
    onAdd: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(6.dp)
    val tabsByKey = remember(tabs) { tabs.associateBy { it.key } }
    // Pinned tabs and the others reorder apart, as on the strip: a pinned one never moves among the rest.
    val runs =
        remember(tabs) {
            tabs
                .partition { it.pinned }
                .toList()
                .filter { it.isNotEmpty() }
                .map { run -> run.map { it.key } }
        }
    Column(
        Modifier
            .width(TabOverflowMenuWidth)
            .background(JewelTheme.globalColors.panelBackground, shape)
            .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
            .padding(4.dp),
    ) {
        Column(Modifier.heightIn(max = TabOverflowMenuMaxHeight).verticalScroll(rememberScrollState())) {
            TabOverflowMenuRuns(runs, tabsByKey, tabCount, onSelect, onMove)
        }
        if (onAdd != null) {
            Divider(
                orientation = Orientation.Horizontal,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                color = JewelTheme.globalColors.borders.disabled,
            )
            NewTabMenuRow(onAdd)
        }
    }
}

/** A tab opened from the overflow menu, as the + does, while the strip has no room for the +. */
@Composable
private fun NewTabMenuRow(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val accent = JewelTheme.globalColors.outlines.focused
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .background(if (hovered) accent.copy(alpha = 0.10f) else Color.Transparent, RoundedCornerShape(4.dp))
                .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(AllIconsKeys.General.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(stringResource(Res.string.add_tab), modifier = Modifier.weight(1f), maxLines = 1)
        Text(NewTabShortcutHint, color = JewelTheme.globalColors.text.disabled, maxLines = 1)
    }
}

@Composable
private fun TabOverflowMenuRuns(
    runs: List<List<String>>,
    tabsByKey: Map<String, TabEntry>,
    tabCount: Int,
    onSelect: (TabEntry) -> Unit,
    onMove: (fromKey: String, toKey: String) -> Unit,
) {
    Column {
        runs.forEach { keys ->
            ReorderableColumn(
                list = keys,
                onSettle = { from, to -> if (from != to) onMove(keys[from], keys[to]) },
            ) { _, key, isDragging ->
                val tab = tabsByKey[key] ?: return@ReorderableColumn
                val dragAlpha by animateFloatAsState(if (isDragging) 0.7f else 1f, tween(durationMillis = 150))
                ReorderableItem {
                    Box(Modifier.alpha(dragAlpha).draggableHandle()) {
                        TabOverflowMenuItem(tab = tab, tabCount = tabCount, onClick = { onSelect(tab) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TabOverflowMenuItem(
    tab: TabEntry,
    tabCount: Int,
    onClick: () -> Unit,
) {
    var contextMenuOpen by remember { mutableStateOf(false) }
    var contextClickOffset by remember { mutableStateOf(IntOffset.Zero) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val accent = JewelTheme.globalColors.outlines.focused
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .background(if (hovered) accent.copy(alpha = 0.10f) else Color.Transparent, RoundedCornerShape(4.dp))
                .clickable(interactionSource = interactionSource, indication = null, role = Role.Tab, onClick = onClick)
                .onPointerEvent(PointerEventType.Release) { ev ->
                    // Middle-click closes, as on the strip, but not a pinned tab: pinning guards against that.
                    if (ev.button.isTertiary && !tab.pinned) tab.onClose()
                    // Right-click: the tab's own menu, as on the strip.
                    if (ev.button.isSecondary) {
                        val p = ev.changes.firstOrNull()?.position ?: Offset.Zero
                        contextClickOffset = IntOffset(p.x.roundToInt(), p.y.roundToInt())
                        contextMenuOpen = true
                        ev.changes.forEach { it.consume() }
                    }
                }.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.weight(1f)) {
            // Drawn as the current tab is, at full strength: an inactive tab's dimming would read as a disabled item.
            tab.data.content(TabContentScopeContainer(), TabState.of(selected = true, hovered = hovered))
        }
        ActionIcon(
            key = AllIconsKeys.Actions.Close,
            // A pinned tab shows no close button, here as on the strip.
            visible = hovered && !tab.pinned,
            onClick = tab.onClose,
            contentDescription = stringResource(Res.string.close_tab),
        )
        if (contextMenuOpen) {
            TabActionsMenu(
                anchorOffset = null,
                contextClickOffset = contextClickOffset,
                tabIndex = tab.stripIndex,
                tabCount = tabCount,
                pinned = tab.pinned,
                onTogglePin = tab.onTogglePin,
                onDetach = tab.onDetach,
                onMoveToDesktop = tab.onMoveToDesktop,
                onCopyLink = tab.onCopyLink,
                onCloseAll = tab.onCloseAll,
                onCloseOthers = tab.onCloseOthers,
                onCloseLeft = tab.onCloseLeft,
                onCloseRight = tab.onCloseRight,
                onDismiss = { contextMenuOpen = false },
            )
        }
    }
}

/** The overflow chip's width once it counts [count] tabs: its label, chevron, gap and paddings. */
@Composable
private fun rememberTabOverflowChipWidth(count: Int): Dp {
    val label = stringResource(Res.string.more_tabs, count)
    val measurer = rememberTextMeasurer()
    val textStyle = JewelTheme.defaultTextStyle
    val density = LocalDensity.current
    return remember(label, textStyle, density) {
        val text =
            with(density) {
                measurer
                    .measure(label, textStyle, maxLines = 1)
                    .size.width
                    .toDp()
            }
        text + TabOverflowChipChrome
    }
}

// Outer padding (2 × 2), inner padding (2 × 10), gap (4) and chevron (16).
private val TabOverflowChipChrome = 44.dp

// As wide as a tab of the strip at its full width.
private val TabOverflowMenuWidth = AppSettings.TAB_FIXED_WIDTH_DP.dp
private val TabOverflowMenuMaxHeight = 480.dp

private val NewTabShortcutHint = if (PlatformInfo.isMacOS) "⌘+T" else "Ctrl+T"

/** A tab's right-click menu, on the strip or in the overflow menu. [anchorOffset] `null`: the anchor's own corner. */
@Composable
private fun TabActionsMenu(
    anchorOffset: IntOffset?,
    contextClickOffset: IntOffset,
    tabIndex: Int,
    tabCount: Int,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    onDetach: (() -> Unit)?,
    onMoveToDesktop: ((String) -> Unit)?,
    onCopyLink: (() -> Unit)?,
    onCloseAll: () -> Unit,
    onCloseOthers: () -> Unit,
    onCloseLeft: () -> Unit,
    onCloseRight: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Resource strings must be resolved outside MenuScope
    val closeAllLabel = stringResource(Res.string.close_all_tabs)
    val closeOthersLabel = stringResource(Res.string.close_other_tabs)
    val closeLeftLabel = stringResource(Res.string.close_tabs_left)
    val closeRightLabel = stringResource(Res.string.close_tabs_right)
    val copyLinkLabel = stringResource(Res.string.copy_tab_link)
    val detachLabel = stringResource(Res.string.tab_open_in_new_window)
    val pinLabel = stringResource(if (pinned) Res.string.unpin_tab else Res.string.pin_tab)
    val desktops by LocalAppGraph.current.desktopManager.desktops
        .collectAsState()
    val currentDesktopId = LocalOpenWindow.current.session.desktopId
    val otherDesktops = desktops.filter { it.id != currentDesktopId }
    val moveToDesktopLabels =
        otherDesktops.associate { it.id to stringResource(Res.string.tab_move_to_desktop, it.name) }

    TabContextMenu(
        anchorOffset = anchorOffset,
        contextClickOffset = contextClickOffset,
        onDismissRequest = onDismiss,
    ) {
        tabContextMenuItem(
            label = pinLabel,
            icon = AllIconsKeys.General.Pin_tab,
            onClick = {
                onDismiss()
                onTogglePin()
            },
        )
        if (onDetach != null) {
            tabContextMenuItem(
                label = detachLabel,
                icon = AllIconsKeys.Actions.OpenNewTab,
                onClick = {
                    onDismiss()
                    onDetach()
                },
            )
        }
        // ponytail: flat items, not a submenu — Jewel's submenu always opens to the right, even in RTL
        if (onMoveToDesktop != null) {
            otherDesktops.forEach { desktop ->
                tabContextMenuItem(
                    label = moveToDesktopLabels.getValue(desktop.id),
                    icon = AllIconsKeys.Actions.MoveTo2,
                    onClick = {
                        onDismiss()
                        onMoveToDesktop(desktop.id)
                    },
                )
            }
        }
        if (onCopyLink != null) {
            tabContextMenuItem(
                label = copyLinkLabel,
                icon = Link,
                onClick = {
                    onDismiss()
                    onCopyLink()
                },
            )
        }
        tabContextMenuItem(
            label = closeAllLabel,
            icon = CloseAll,
            onClick = {
                onDismiss()
                onCloseAll()
            },
        )
        if (tabCount > 1) {
            tabContextMenuItem(
                label = closeOthersLabel,
                icon = Tab_close,
                onClick = {
                    onDismiss()
                    onCloseOthers()
                },
            )
        }
        if (tabIndex > 0) {
            tabContextMenuItem(
                label = closeLeftLabel,
                icon = Tab_close_right,
                onClick = {
                    onDismiss()
                    onCloseLeft()
                },
            )
        }
        if (tabIndex < tabCount - 1) {
            tabContextMenuItem(
                label = closeRightLabel,
                icon = Tab_close_right,
                mirrorIcon = true,
                onClick = {
                    onDismiss()
                    onCloseRight()
                },
            )
        }
    }
}

/** The hover card of a tab: its full title over the picture of the window as the tab was left. */
@Composable
private fun TabHoverPreviewScope.TabPreviewCard() {
    val colors = JewelTheme.tooltipStyle.colors
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .width(TabPreviewCardWidth)
            .background(colors.background, shape)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = tab.title,
            color = colors.content,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (thumbnail != null) {
            Spacer(Modifier.height(8.dp))
            TabPreview(tab, Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
        }
    }
}

private val TabPreviewCardWidth = 280.dp

// TabContentScopeContainer implementation (same as Jewel's internal)
private class TabContentScopeContainer : TabContentScope {
    @Composable
    override fun Modifier.tabContentAlpha(state: TabState): Modifier =
        alpha(
            JewelTheme.defaultTabStyle.contentAlpha
                .contentFor(state)
                .value,
        )
}

@Composable
private fun SingleLineTabContent(
    label: String,
    state: TabState,
    icon: Painter?,
    modifier: Modifier = Modifier,
) {
    val iconOnly = LocalCompactIconOnly.current
    val contentAlpha =
        JewelTheme.defaultTabStyle.contentAlpha
            .contentFor(state)
            .value
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp).alpha(contentAlpha),
            )
        }
        if (!iconOnly) {
            // The paragraph's own direction (its first strong letter), whatever the strip's.
            val rtl = remember(label) { !Bidi(label, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight() }
            // Read by the layer and the draw only, so a title crossing its width never recomposes the tab.
            val cut = remember { mutableStateOf(false) }
            Text(
                label,
                modifier =
                    Modifier
                        .fadeCutEnd(rtl, cut)
                        .layout { measurable, constraints ->
                            // Laid out whole, then pinned by its start, so only its end is cut (a Hebrew title clipped
                            // from the left would lose its first letters instead).
                            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                            val width = placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
                            cut.value = placeable.width > width
                            layout(width, placeable.height) { placeable.place(if (rtl) width - placeable.width else 0, 0) }
                        }.alpha(contentAlpha),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/**
 * Moves [window] once a primary or touch press on this element travels past the touch slop, with the platform's own
 * move (the title bar's); a press that never travels stays a click, and one a child took is left to it. No-op
 * without a window.
 */
@OptIn(ExperimentalComposeUiApi::class) // PointerEvent.button
private fun Modifier.windowMoveGrip(window: TaoWindow?): Modifier {
    if (window == null) return this
    return pointerInput(window) {
        awaitEachGesture {
            val down = awaitFirstDown()
            // Primary button or touch, as on the title bar.
            if (currentEvent.button != PointerButton.Primary && down.type != PointerType.Touch) return@awaitEachGesture
            val drag = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
            if (drag != null) window.dragWindow()
        }
    }
}

/** The last layout's [TabStripLayout.tooNarrow], for its hysteresis. A plain field, bookkeeping only. */
private class OverflowMemory {
    var tooNarrow = false
}

/**
 * Which tab the open drop slot stands for, and where: the frame that shows it landed there turns
 * [generation] over, and the slots keyed on it leave at once. Plain fields, bookkeeping only.
 */
private class DropLanding {
    private var tabId: String? = null
    private var index = -1

    var generation = 0
        private set

    fun expect(
        tabId: String,
        index: Int,
    ) {
        this.tabId = tabId
        this.index = index
    }

    fun settle(ids: List<String>) {
        val expected = tabId ?: return
        if (ids.getOrNull(index) == expected) generation++
        tabId = null
        index = -1
    }
}

/**
 * The gap before logical tab [index] (or after the last): opens to [ghost]'s width while a tab
 * dragged from another window would drop there, and shuts when it moves on.
 */
@Composable
private fun DropGhostSlot(
    ghost: TabDropGhost?,
    index: Int,
) {
    val shown = ghost?.takeIf { it.index == index }
    // Kept through the exit, which still needs a width and a title to shut.
    var last by remember { mutableStateOf(shown) }
    if (shown != null) last = shown
    AnimatedVisibility(
        visible = shown != null,
        enter = expandHorizontally(tween(DROP_SLOT_MS)),
        exit = shrinkHorizontally(tween(DROP_SLOT_MS)),
    ) {
        last?.let {
            TabDragGhostCard(
                title = it.tab.title,
                modifier = Modifier.width(it.width).fillMaxHeight().padding(horizontal = TabOuterHorizontalPadding, vertical = 4.dp),
            )
        }
    }
}

private const val DROP_SLOT_MS = 200

/** A pinned tab's label: its short title (a book's acronym), else the title of a book, search or author tab. */
private fun TabItem.pinnedLabel(): String? =
    shortTitle.ifBlank { title.takeIf { tabType in LABELED_WHEN_PINNED }.orEmpty() }.ifBlank { null }

private val LABELED_WHEN_PINNED = setOf(TabType.BOOK, TabType.SEARCH, TabType.AUTHOR)

/** The width of each pinned tab, by key: its icon's, or its label's measured width. */
@Composable
private fun rememberPinnedTabWidths(
    tabs: List<TabEntry>,
    style: TabStyle,
): Map<String, Dp> {
    val measurer = rememberTextMeasurer()
    val textStyle = JewelTheme.defaultTextStyle
    val density = LocalDensity.current
    val padding = style.metrics.tabPadding
    val chrome =
        TabOuterHorizontalPadding * 2 + padding.calculateLeftPadding(LayoutDirection.Ltr) +
            padding.calculateRightPadding(LayoutDirection.Ltr)
    return remember(tabs, textStyle, density, chrome) {
        tabs.filter { it.pinned }.associate { tab ->
            val label = tab.pinnedLabel ?: return@associate tab.key to PinnedTabWidth + PinMarkWidth
            val textWidth =
                with(density) {
                    measurer
                        .measure(label, textStyle, maxLines = 1)
                        .size.width
                        .toDp()
                }
            tab.key to (textWidth + chrome).coerceIn(PinnedTabWidth, PinnedLabelTabMaxWidth) + PinMarkWidth
        }
    }
}

/**
 * Slides this element from where its parent stood in the strip to the parent's new place, on the first move after
 * [slideGeneration] changes; any other move (window resizes, drags) it follows at once.
 */
@Composable
private fun Modifier.slideToNewPlace(
    slide: TabSlide,
    slideGeneration: IntState,
): Modifier {
    val scope = rememberCoroutineScope()
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            // Every placement sees the generation, so only the move right after a reorder slides.
            val slideRequested = slide.see(slideGeneration.intValue)
            // The parent's place as the strip has just set it, read in the same pass rather than a frame late.
            val target = coordinates?.parentLayoutCoordinates?.positionInParent()?.round()
            // Absolute: the place is measured from the left, whatever the strip's direction.
            placeable.place(if (target == null) IntOffset.Zero else slide.offsetTo(target, slideRequested, scope))
        }
    }
}

/** A tab's slide along the strip: read and written by its placement only, but for [lift]. */
private class TabSlide {
    // Where the tab rests, and its animated position only while it slides.
    private var target: IntOffset? = null
    private var position: Animatable<IntOffset, AnimationVector2D>? = null
    private var seenGeneration: Int? = null
    private var job: Job? = null

    /** How far the slide under way travels, 0 at rest: the farther, the higher it is drawn. */
    var lift by mutableFloatStateOf(0f)
        private set

    /** Whether [generation] is new since the last placement. */
    fun see(generation: Int): Boolean {
        val new = seenGeneration != null && generation != seenGeneration
        seenGeneration = generation
        return new
    }

    /** Where to draw the element placed at [newTarget], relative to it. */
    fun offsetTo(
        newTarget: IntOffset,
        slideRequested: Boolean,
        @StructuredScope scope: CoroutineScope,
    ): IntOffset {
        val from = target
        if (newTarget != from) {
            target = newTarget
            val sliding = job?.isActive == true
            val current = position
            if (from != null && (slideRequested || sliding)) {
                // A slide under way follows its tab's place as the widths settle around it.
                val animatable = current?.takeIf { sliding } ?: Animatable(from, IntOffset.VectorConverter)
                position = animatable
                val starts = !sliding
                job =
                    scope.launch {
                        if (starts) lift = (animatable.value - newTarget).x.absoluteValue.toFloat()
                        animatable.animateTo(newTarget, TabSlideSpec)
                        lift = 0f
                    }
            } else {
                job?.cancel()
                job = null
                position = null
                lift = 0f
            }
        }
        val animated = position.takeIf { job?.isActive == true } ?: return IntOffset.Zero
        return animated.value - newTarget
    }
}

/** The strip's order as last composed, and whether a drag has been under way since. */
private class StripOrder(
    private var keys: List<String>,
) {
    private var dragSeen = false

    /**
     * Records [newKeys]; true when they are the same tabs in another order, with no drag since the last order: a
     * drag's own release reorders the strip once the tab is already home.
     */
    fun reorderedTo(
        newKeys: List<String>,
        dragging: Boolean,
    ): Boolean {
        if (dragging) dragSeen = true
        if (newKeys == keys) {
            if (!dragging) dragSeen = false
            return false
        }
        val reordered = !dragSeen && newKeys.size == keys.size && newKeys.toSet() == keys.toSet()
        keys = newKeys
        if (!dragging) dragSeen = false
        return reordered
    }
}

/** Fades the last [TitleFadeWidth] of a cut title out instead of an ellipsis (Chromium's FADE_TAIL). */
private fun Modifier.fadeCutEnd(
    rtl: Boolean,
    cut: State<Boolean>,
): Modifier =
    graphicsLayer {
        clip = true
        compositingStrategy = if (cut.value) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }.drawWithContent {
        drawContent()
        if (!cut.value) return@drawWithContent
        val fade = TitleFadeWidth.toPx().coerceAtMost(size.width / 2)
        val (startX, endX) = if (rtl) fade to 0f else size.width - fade to size.width
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = startX, endX = endX),
            blendMode = BlendMode.DstIn,
        )
    }

// Tab context menu using native Jewel styling
@OptIn(InternalJewelApi::class)
@Composable
private fun TabContextMenu(
    anchorOffset: IntOffset?,
    contextClickOffset: IntOffset,
    onDismissRequest: () -> Unit,
    style: MenuStyle = JewelTheme.menuStyle,
    content: MenuScope.() -> Unit,
) {
    val menuController =
        remember(onDismissRequest) {
            DefaultMenuController(onDismissRequest = {
                onDismissRequest()
                true
            })
        }

    val positionProvider =
        remember(anchorOffset, contextClickOffset) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset {
                    val anchor = anchorOffset ?: anchorBounds.topLeft
                    var x = anchor.x + contextClickOffset.x
                    var y = anchor.y + contextClickOffset.y
                    if (x + popupContentSize.width > windowSize.width) {
                        x = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                    }
                    if (x < 0) x = 0
                    if (y + popupContentSize.height > windowSize.height) {
                        y = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
                    }
                    return IntOffset(x, y)
                }
            }
        }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
        cornerSize = style.metrics.cornerSize,
    ) {
        CompositionLocalProvider(LocalMenuController provides menuController) {
            MenuContent(content = content)
        }
    }
}

private fun MenuScope.tabContextMenuItem(
    label: String,
    icon: org.jetbrains.jewel.ui.icon.IconKey,
    onClick: () -> Unit,
) {
    selectableItem(
        selected = false,
        onClick = onClick,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                key = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = JewelTheme.globalColors.text.normal,
            )
            Text(label)
        }
    }
}

private fun MenuScope.tabContextMenuItem(
    label: String,
    icon: ImageVector,
    mirrorIcon: Boolean = false,
    onClick: () -> Unit,
) {
    selectableItem(
        selected = false,
        onClick = onClick,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = rememberVectorPainter(icon),
                contentDescription = null,
                modifier =
                    Modifier.size(16.dp).let { m ->
                        if (mirrorIcon) m.graphicsLayer(scaleX = -1f) else m
                    },
                colorFilter =
                    androidx.compose.ui.graphics.ColorFilter
                        .tint(JewelTheme.globalColors.text.normal),
            )
            Text(label)
        }
    }
}

/** The icon sets are drawn in black: tint them so they follow the theme (dark mode). */
@Composable
private fun rememberTintedVectorPainter(
    image: ImageVector,
    tint: Color,
): Painter =
    rememberVectorPainter(
        defaultWidth = image.defaultWidth,
        defaultHeight = image.defaultHeight,
        viewportWidth = image.viewportWidth,
        viewportHeight = image.viewportHeight,
        name = image.name,
        tintColor = tint,
        tintBlendMode = BlendMode.SrcIn,
        autoMirror = image.autoMirror,
    ) { _, _ -> RenderVectorGroup(group = image.root) }
