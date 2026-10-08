@file:OptIn(ExperimentalNucleusApi::class)

package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.*
import androidx.compose.foundation.border
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondary
import androidx.compose.ui.input.pointer.isTertiary
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.tao.TabGripCursor
import dev.nucleusframework.window.tao.TabHoverPreview
import dev.nucleusframework.window.tao.TabHoverPreviewPopup
import dev.nucleusframework.window.tao.TabHoverPreviewScope
import dev.nucleusframework.window.tao.TabPreview
import dev.nucleusframework.window.tao.TabStripScope
import dev.nucleusframework.window.tao.rememberTabStripDrag
import dev.nucleusframework.window.tao.tabStripCarry
import dev.nucleusframework.window.tao.tabStripGeometry
import dev.nucleusframework.window.tao.tabStripGrip
import io.github.kdroidfilter.seforim.tabs.*
import io.github.kdroidfilter.seforimapp.core.deeplink.toShareLink
import io.github.kdroidfilter.seforimapp.core.presentation.components.TitleBarActionButton
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
import io.github.kdroidfilter.seforimapp.icons.bookOpenTabs
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
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
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlin.math.roundToInt
import kotlin.ranges.coerceAtLeast
import kotlin.ranges.coerceAtMost
import kotlin.time.Duration.Companion.milliseconds

// Carry both TabData and its label for tooltips anchored on the whole tab container
private data class TabEntry(
    val key: String,
    val data: TabData,
    val labelProvider: @Composable () -> String,
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
)

private val TabTooltipWidthThreshold = 140.dp
private val CompactTabWidthThreshold = 50.dp

// Chromium's kMinimumContentsWidthForCloseButtons: below it an inactive tab drops its close button,
// so the title always keeps some room next to it.
private val MinContentWidthForCloseButton = 68.dp
private val TabOuterHorizontalPadding = 2.dp

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
                            onClose = { onEvents(TabsEvents.OnClose(actualIndex)) },
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
                            onClose = { onEvents(TabsEvents.OnClose(index)) },
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
        // Reserve a non-interactive draggable area at the trailing edge to allow window move
        val reservedDragArea = 40.dp
        // + button (40.dp) + divider (1.dp) + divider padding (8.dp) + reserved drag area
        val extrasWidth = 40.dp + 1.dp + 8.dp + reservedDragArea
        val availableForTabs = (maxWidthDp - extrasWidth).coerceAtLeast(0.dp)
        val tabsCount = tabs.size.coerceAtLeast(1)
        // Chrome-like: tabs shrink to fill available width, capped by a max width
        val maxTabWidth = AppSettings.TAB_FIXED_WIDTH_DP.dp
        val naturalTabWidth = (availableForTabs / tabsCount)
        val computedTabWidthTarget = naturalTabWidth.coerceAtMost(maxTabWidth)
        val tabWidth = frozenTabWidth?.coerceAtMost(computedTabWidthTarget) ?: computedTabWidthTarget

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
                val rowModifier = if (shrinkToFitActive) Modifier.fillMaxWidth() else Modifier
                val tabEntriesByKey = remember(tabs) { tabs.associateBy { it.key } }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = rowModifier,
                ) {
                    currentKeys.forEachIndexed { index, key ->
                        val tabEntry = tabEntriesByKey[key] ?: return@forEachIndexed
                        val workspaceTab = workspace.tab(key) ?: return@forEachIndexed
                        key(key) {
                            val isClosing = closingKeys.contains(tabEntry.key)
                            val isNew = !knownKeys.contains(tabEntry.key)

                            Box(
                                modifier =
                                    Modifier
                                        // Carried or sliding home, it is drawn over its neighbours.
                                        .zIndex(if (tabDrag.animating == key) 1f else 0f)
                                        .then(
                                            // A tab closing (or just moved away) is no longer in the group.
                                            if (reorderingEnabled && !isClosing && key in group.ids) {
                                                Modifier.tabStripGrip(
                                                    this@RtlAwareTabStripContent,
                                                    tabDrag,
                                                    workspaceTab,
                                                    group.ids.indexOf(key),
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
                                Row(Modifier.tabStripCarry(tabDrag, workspaceTab)) {
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
                                            tabWidth = tabWidth,
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
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tabs container: fills available width when shrink-to-fit is active,
            // so the + button stays fixed at the trailing edge.
            Row(
                modifier =
                    Modifier
                        .let { baseModifier ->
                            if (shrinkToFitActive) {
                                baseModifier.weight(1f)
                            } else {
                                baseModifier
                            }
                        }.hoverable(interactionSource)
                        .animateContentSize(animationSpec = tabsContainerAnimationSpec),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabsOnly()
            }

            Divider(
                orientation = Orientation.Vertical,
                modifier =
                    Modifier
                        .fillMaxHeight(0.8f)
                        .padding(horizontal = 4.dp)
                        .width(1.dp),
                color = JewelTheme.globalColors.borders.disabled,
            )

            val shortcutHint = if (PlatformInfo.isMacOS) "⌘+T" else "Ctrl+T"
            TitleBarActionButton(
                onClick = onAddClick,
                key = AllIconsKeys.General.Add,
                contentDescription = stringResource(Res.string.add_tab),
                tooltipText = stringResource(Res.string.add_tab),
                shortcutHint = shortcutHint,
            )

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
        val roomForCloseButton = contentWidth >= MinContentWidthForCloseButton

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
                    ).padding(tabStyle.metrics.tabPadding)
                    .onPointerEvent(PointerEventType.Release) { ev ->
                        // Middle-click closes tab (Chrome-like)
                        if (ev.button.isTertiary) onClose()
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
                val showCloseIcon = tabData.closable && (isSelected || roomForCloseButton)

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

                        Icon(
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
                            hint = Stateful(closeButtonState),
                        )
                    }
                }

                // In compact mode, selected tab shows only close icon (centered)
                if (isCompact && isSelected) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        closeIconComposable()
                    }
                } else {
                    val iconOnly = isCompact
                    Box(Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalCompactIconOnly provides iconOnly) {
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
            // Resource strings must be resolved outside MenuScope
            val closeAllLabel = stringResource(Res.string.close_all_tabs)
            val closeOthersLabel = stringResource(Res.string.close_other_tabs)
            val closeLeftLabel = stringResource(Res.string.close_tabs_left)
            val closeRightLabel = stringResource(Res.string.close_tabs_right)
            val copyLinkLabel = stringResource(Res.string.copy_tab_link)
            val detachLabel = stringResource(Res.string.tab_open_in_new_window)
            val desktops by LocalAppGraph.current.desktopManager.desktops
                .collectAsState()
            val currentDesktopId = LocalOpenWindow.current.session.desktopId
            val otherDesktops = desktops.filter { it.id != currentDesktopId }
            val moveToDesktopLabels =
                otherDesktops.associate { it.id to stringResource(Res.string.tab_move_to_desktop, it.name) }

            TabContextMenu(
                anchorOffset = anchorOffset,
                contextClickOffset = contextClickOffset,
                onDismissRequest = { contextMenuOpen = false },
            ) {
                if (onDetach != null) {
                    tabContextMenuItem(
                        label = detachLabel,
                        icon = AllIconsKeys.Actions.OpenNewTab,
                        onClick = {
                            contextMenuOpen = false
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
                                contextMenuOpen = false
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
                            contextMenuOpen = false
                            onCopyLink()
                        },
                    )
                }
                tabContextMenuItem(
                    label = closeAllLabel,
                    icon = CloseAll,
                    onClick = {
                        contextMenuOpen = false
                        onCloseAll()
                    },
                )
                if (tabCount > 1) {
                    tabContextMenuItem(
                        label = closeOthersLabel,
                        icon = Tab_close,
                        onClick = {
                            contextMenuOpen = false
                            onCloseOthers()
                        },
                    )
                }
                if (tabIndex > 0) {
                    tabContextMenuItem(
                        label = closeLeftLabel,
                        icon = Tab_close_right,
                        onClick = {
                            contextMenuOpen = false
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
                            contextMenuOpen = false
                            onCloseRight()
                        },
                    )
                }
            }
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
            // The side the title is cut on: its paragraph's end, whatever the strip's direction.
            var cutSide by remember { mutableStateOf<ResolvedTextDirection?>(null) }
            Text(
                label,
                modifier = Modifier.alpha(contentAlpha).fadeCutEnd(cutSide),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                onTextLayout = { layout ->
                    cutSide = if (layout.hasVisualOverflow) layout.getParagraphDirection(0) else null
                },
            )
        }
    }
}

/** Fades the last [TitleFadeWidth] of a cut title out instead of an ellipsis (Chromium's FADE_TAIL). */
private fun Modifier.fadeCutEnd(direction: ResolvedTextDirection?): Modifier {
    if (direction == null) return this
    return graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        val fade = TitleFadeWidth.toPx().coerceAtMost(size.width / 2)
        val (startX, endX) = if (direction == ResolvedTextDirection.Rtl) fade to 0f else size.width - fade to size.width
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = startX, endX = endX),
            blendMode = BlendMode.DstIn,
        )
    }
}

// Tab context menu using native Jewel styling
@OptIn(InternalJewelApi::class)
@Composable
private fun TabContextMenu(
    anchorOffset: IntOffset,
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
                    var x = anchorOffset.x + contextClickOffset.x
                    var y = anchorOffset.y + contextClickOffset.y
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

private fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
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
