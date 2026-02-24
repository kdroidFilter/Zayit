package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.kdroidfilter.seforim.tabs.*
import io.github.kdroidfilter.seforimapp.core.presentation.components.TitleBarActionButton
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.icons.CloseAll
import io.github.kdroidfilter.seforimapp.icons.Tab_close
import io.github.kdroidfilter.seforimapp.icons.Tab_close_right
import io.github.kdroidfilter.seforimapp.icons.bookOpenTabs
import io.github.santimattius.structured.annotations.StructuredScope
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
import sh.calvin.reorderable.ReorderableRow
import kotlin.math.roundToInt
import kotlin.ranges.coerceAtLeast
import kotlin.ranges.coerceAtMost

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
)

private val TabTooltipWidthThreshold = 140.dp

@Composable
fun TabsView() {
    val viewModel: TabsViewModel = LocalAppGraph.current.tabsViewModel
    val state = rememberTabsState(viewModel)
    DefaultTabShowcase(state = state, onEvents = viewModel::onEvent)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DefaultTabShowcase(
    onEvents: (TabsEvents) -> Unit,
    state: TabsState,
) {
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    // Track for auto-scrolling (no-op in shrink-to-fit mode)
    var previousTabCount by remember { mutableStateOf(state.tabs.size) }
    val newTabAdded = state.tabs.size > previousTabCount
    LaunchedEffect(state.tabs.size) { previousTabCount = state.tabs.size }

    // Create TabData objects with RTL support
    val tabs =
        remember(state.tabs, state.selectedTabIndex, isRtl) {
            if (isRtl) {
                // For RTL: reverse the list and use the reversed index for display
                state.tabs.reversed().mapIndexed { visualIndex, tabItem ->
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
                                        tabItem.tabType == TabType.SEARCH ->
                                            stringResource(
                                                Res.string.search_results_tab_title,
                                                tabItem.title,
                                            )
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
                            tabItem.tabType == TabType.SEARCH -> stringResource(Res.string.search_results_tab_title, tabItem.title)
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
                    )
                }
            } else {
                // For LTR: use normal order
                state.tabs.mapIndexed { index, tabItem ->
                    val isSelected = index == state.selectedTabIndex

                    val tabData =
                        TabData.Default(
                            selected = isSelected,
                            content = { tabState ->
                                val icon: Painter =
                                    if (tabItem.tabType == TabType.BOOK) {
                                        rememberVectorPainter(bookOpenTabs(JewelTheme.globalColors.text.normal))
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
                                        tabItem.tabType == TabType.SEARCH ->
                                            stringResource(
                                                Res.string.search_results_tab_title,
                                                tabItem.title,
                                            )
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
                            tabItem.tabType == TabType.SEARCH -> stringResource(Res.string.search_results_tab_title, tabItem.title)
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
                    )
                }
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
private fun RtlAwareTabStripWithAddButton(
    tabs: List<TabEntry>,
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
private fun RtlAwareTabStripContent(
    tabs: List<TabEntry>,
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
    var knownKeys by remember { mutableStateOf(tabs.map { it.key }.toSet()) }
    val currentKeys = remember(tabs) { tabs.map { it.key } }

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
        val tabWidth = computedTabWidthTarget

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
                if (shouldAnimateTabsContainer) {
                    tween(durationMillis = containerTransitionDurationMs, easing = FastOutSlowInEasing)
                } else {
                    snap()
                }
            SideEffect {
                lastShrinkToFitActive = shrinkToFitActive
            }

            // Helper to render all tab items with animations and reordering support
            val tabsOnly: @Composable RowScope.() -> Unit = {
                val reorderingEnabled = closingKeys.isEmpty()
                val rowModifier = if (shrinkToFitActive) Modifier.fillMaxWidth() else Modifier
                val tabEntriesByKey = remember(tabs) { tabs.associateBy { it.key } }

                ReorderableRow(
                    list = currentKeys,
                    onSettle = { fromIdx, toIdx ->
                        if (!reorderingEnabled) return@ReorderableRow
                        onReorder(fromIdx, toIdx)
                    },
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = rowModifier,
                ) { index, key, isBeingDragged ->
                    val tabEntry = tabEntriesByKey[key] ?: return@ReorderableRow
                    key(key) {
                        val isClosing = closingKeys.contains(tabEntry.key)
                        val isNew = !knownKeys.contains(tabEntry.key)

                        ReorderableItem {
                            Box(
                                modifier =
                                    Modifier.draggableHandle(
                                        enabled = reorderingEnabled && !isClosing,
                                        onDragStarted = {
                                            // Chrome-like behavior: selecting a tab when starting to drag it
                                            tabEntry.onClick()
                                        },
                                    ),
                            ) {
                                var visible by remember(isClosing) { mutableStateOf(!isClosing) }
                                LaunchedEffect(isClosing) {
                                    if (isClosing) visible = false else visible = true
                                }
                                Row {
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
                                            animateWidth = !isNew,
                                            enterFromSmall = isNew,
                                            enterDurationMs = enterDurationMs,
                                            isDragging = isBeingDragged,
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
    val isIslands = ThemeUtils.isIslandsStyle()
    val lineColor by tabStyle.colors.underlineFor(tabState)
    val lineThickness = tabStyle.metrics.underlineThickness
    val defaultBg by tabStyle.colors.backgroundFor(state = tabState)
    val accent = JewelTheme.globalColors.outlines.focused
    val backgroundColor =
        if (isIslands) {
            when {
                tabState.isSelected -> accent.copy(alpha = 0.20f)
                tabState.isPressed -> accent.copy(alpha = 0.18f)
                tabState.isHovered -> accent.copy(alpha = 0.10f)
                else -> Color.Transparent
            }
        } else {
            defaultBg
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

        val container: @Composable () -> Unit = {
            Row(
                modifier
                    .height(tabStyle.metrics.tabHeight)
                    .width(widthForThisTab)
                    .let { m ->
                        if (isIslands) {
                            m.padding(horizontal = 2.dp, vertical = 4.dp).clip(RoundedCornerShape(8.dp))
                        } else {
                            m
                        }
                    }.background(backgroundColor)
                    .alpha(if (isDragging) 0.7f else 1f) // Visual feedback when dragging
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
                    ).let { m ->
                        if (isIslands) {
                            m
                        } else {
                            m.drawBehind {
                                val strokeThickness = lineThickness.toPx()
                                val startY = size.height - (strokeThickness / 2f)
                                val endX = size.width
                                val capDxFix = strokeThickness / 2f

                                drawLine(
                                    brush = SolidColor(lineColor),
                                    start = Offset(0 + capDxFix, startY),
                                    end = Offset(endX - capDxFix, startY),
                                    strokeWidth = strokeThickness,
                                    cap = StrokeCap.Round,
                                )
                            }
                        }
                    }.padding(tabStyle.metrics.tabPadding)
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
                horizontalArrangement = Arrangement.spacedBy(tabStyle.metrics.closeContentGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Build content/close order to keep close at the trailing edge
                val showCloseIcon = tabData.closable

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

                Box(Modifier.weight(1f)) {
                    tabData.content(TabContentScopeContainer(), tabState)
                }
                closeIconComposable()
            }
        }

        val label = labelProvider()
        val showTooltip =
            label.isNotBlank() &&
                (label.length > AppSettings.MAX_TAB_TITLE_LENGTH || tabWidth < TabTooltipWidthThreshold)

        // Read theme colors outside remember so they act as a cache key.
        val tooltipColors = JewelTheme.tooltipStyle.colors
        val chromeTooltipStyle =
            remember(tooltipColors) {
                // Positions the tooltip centered below the anchor (the tab) — Chrome style.
                // ComponentRect relies on LayoutBoundsHolder which isn't wired in JewelTooltipArea,
                // so we use a raw PopupPositionProvider that reads anchorBounds directly.
                @OptIn(ExperimentalFoundationApi::class)
                val belowAnchorPlacement = object : TooltipPlacement {
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
                                    x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
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

            TabContextMenu(
                anchorOffset = anchorOffset,
                contextClickOffset = contextClickOffset,
                onDismissRequest = { contextMenuOpen = false },
            ) {
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
        Text(
            label,
            modifier = Modifier.alpha(contentAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
