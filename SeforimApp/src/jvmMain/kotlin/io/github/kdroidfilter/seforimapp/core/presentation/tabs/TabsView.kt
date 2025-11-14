package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isTertiary
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforim.tabs.TabsState
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforim.tabs.rememberTabsState
import io.github.kdroidfilter.seforimapp.core.presentation.components.TitleBarActionButton
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.icons.BookOpenTabs
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.hints.Stateful
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.add_tab
import seforimapp.seforimapp.generated.resources.close_tab
import seforimapp.seforimapp.generated.resources.home
import seforimapp.seforimapp.generated.resources.search_results_tab_title
import seforimapp.seforimapp.generated.resources.app_name
import seforimapp.seforimapp.generated.resources.home_tab_with_app
import io.github.kdroidfilter.platformtools.getOperatingSystem
import io.github.kdroidfilter.platformtools.OperatingSystem

// Carry both TabData and its label for tooltips anchored on the whole tab container
private data class TabEntry(
    val key: String,
    val data: TabData,
    val labelProvider: @Composable () -> String,
    val onClose: () -> Unit,
    val onClick: () -> Unit,
)
private val TabTooltipWidthThreshold = 140.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
@Stable
fun Modifier.verticalWheelToHorizontal(
    scrollState: ScrollState,
    multiplier: Float = 80f
): Modifier {
    val scope = rememberCoroutineScope()
    return this.onPointerEvent(PointerEventType.Scroll) { event ->
        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
        if (dy != 0f) {
            scope.launch { scrollState.scrollBy(dy * multiplier) }
            event.changes.forEach { it.consume() }
        }
    }
}


@Composable
fun TabsView() {
    val viewModel: TabsViewModel = LocalAppGraph.current.tabsViewModel
    val state = rememberTabsState(viewModel)
    DefaultTabShowcase(state = state, onEvents = viewModel::onEvent)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DefaultTabShowcase(onEvents: (TabsEvents) -> Unit, state: TabsState) {
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    // Track for auto-scrolling (no-op in shrink-to-fit mode)
    var previousTabCount by remember { mutableStateOf(state.tabs.size) }
    val newTabAdded = state.tabs.size > previousTabCount
    LaunchedEffect(state.tabs.size) { previousTabCount = state.tabs.size }

    // Create TabData objects with RTL support
    val tabs = remember(state.tabs, state.selectedTabIndex, isRtl) {
        if (isRtl) {
            // For RTL: reverse the list and use the reversed index for display
            state.tabs.reversed().mapIndexed { visualIndex, tabItem ->
                // The actual index in the original list
                val actualIndex = state.tabs.size - 1 - visualIndex
                val isSelected = actualIndex == state.selectedTabIndex

                val tabData = TabData.Default(
                    selected = isSelected,
                    content = { tabState ->
                        val icon: Painter = if (tabItem.tabType == TabType.BOOK) {
                            rememberVectorPainter(BookOpenTabs(JewelTheme.contentColor))
                        } else {
                            if (tabItem.title.isEmpty()) {
                                rememberVectorPainter(io.github.kdroidfilter.seforimapp.icons.homeTabs(JewelTheme.contentColor))
                            } else {
                                val iconProvider = rememberResourcePainterProvider(AllIconsKeys.Actions.Find)
                                iconProvider.getPainter(Stateful(tabState)).value
                            }
                        }

                        val appTitle = stringResource(Res.string.app_name)
                        val label = when {
                            tabItem.title.isEmpty() -> stringResource(Res.string.home_tab_with_app, appTitle)
                            tabItem.tabType == TabType.SEARCH -> stringResource(Res.string.search_results_tab_title, tabItem.title)
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
                    onClose = { onEvents(TabsEvents.onClose(actualIndex)) },
                    onClick = { onEvents(TabsEvents.onSelected(actualIndex)) },
                )
            }
        } else {
            // For LTR: use normal order
            state.tabs.mapIndexed { index, tabItem ->
                val isSelected = index == state.selectedTabIndex

                val tabData = TabData.Default(
                    selected = isSelected,
                    content = { tabState ->
                        val icon: Painter = if (tabItem.tabType == TabType.BOOK) {
                            rememberVectorPainter(BookOpenTabs(JewelTheme.globalColors.text.normal))
                        } else {
                            if (tabItem.title.isEmpty()) {
                                rememberVectorPainter(io.github.kdroidfilter.seforimapp.icons.homeTabs(JewelTheme.globalColors.text.normal))
                            } else {
                                val iconProvider = rememberResourcePainterProvider(AllIconsKeys.Actions.Find)
                                iconProvider.getPainter(Stateful(tabState)).value
                            }
                        }

                        val appTitle = stringResource(Res.string.app_name)
                        val label = when {
                            tabItem.title.isEmpty() -> stringResource(Res.string.home_tab_with_app, appTitle)
                            tabItem.tabType == TabType.SEARCH -> stringResource(Res.string.search_results_tab_title, tabItem.title)
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
                    onClose = { onEvents(TabsEvents.onClose(index)) },
                    onClick = { onEvents(TabsEvents.onSelected(index)) },
                )
            }
        }
    }

    RtlAwareTabStripWithAddButton(
        tabs = tabs,
        style = JewelTheme.defaultTabStyle,
        isRtl = isRtl,
        newTabAdded = newTabAdded
    ) {
        onEvents(TabsEvents.onAdd)
    }
}

@Composable
private fun RtlAwareTabStripWithAddButton(
    tabs: List<TabEntry>,
    style: TabStyle,
    isRtl: Boolean,
    newTabAdded: Boolean,
    onAddClick: () -> Unit
) {
    // Shrink-to-fit mode: no horizontal scroll, tabs adjust width.

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        RtlAwareTabStripContent(
            tabs = tabs,
            style = style,
            onAddClick = onAddClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RtlAwareTabStripContent(
    tabs: List<TabEntry>,
    style: TabStyle,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val interactionSource = remember { MutableInteractionSource() }
    var isActive by remember { mutableStateOf(false) }

    // Keep track of tabs that are animating out before we notify the ViewModel
    var closingKeys by remember { mutableStateOf(setOf<String>()) }
    val exitDurationMs = 200
    val enterDurationMs = 200

    // Track which tabs already existed to avoid double width + expand animation on new entries
    var knownKeys by remember { mutableStateOf(tabs.map { it.key }.toSet()) }
    val currentKeys = remember(tabs) { tabs.map { it.key } }

    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier = modifier) {
        val maxWidthDp = this.maxWidth
        // Reserve a non-interactive draggable area at the trailing edge to allow window move
        val reservedDragArea = 40.dp
        val extrasWidth = 40.dp /* + button */ + 1.dp /* divider */ + 8.dp /* divider padding */ + reservedDragArea
        val availableForTabs = (maxWidthDp - extrasWidth).coerceAtLeast(0.dp)
        val tabsCount = tabs.size.coerceAtLeast(1)
        // Chrome-like: tabs shrink to fill available width, capped by a max width
        val maxTabWidth = AppSettings.TAB_FIXED_WIDTH_DP.dp
        val naturalTabWidth = (availableForTabs / tabsCount)
        val computedTabWidthTarget = naturalTabWidth.coerceAtMost(maxTabWidth)
        val tabWidth = computedTabWidthTarget

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
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

            // Helper to render all tab items with animations
            val TabsOnly: @Composable RowScope.() -> Unit = {
                tabs.forEachIndexed { index, entry ->
                    // Wrap each tab in visibility animation to mimic Chrome open/close
                    val isClosing = closingKeys.contains(entry.key)
                    // Exit shrinks towards start. For enter, we handle width growth manually from a small width.
                    val exitShrinkTowards = Alignment.Start
                    val isNew = !knownKeys.contains(entry.key)

                    key(entry.key) {
                        AnimatedVisibility(
                            visible = !isClosing,
                            // Enter is only a soft fade; width growth is handled inside the tab to avoid double motion
                            enter = fadeIn(animationSpec = tween(enterDurationMs, easing = LinearEasing)),
                            exit = shrinkHorizontally(
                                animationSpec = tween(durationMillis = exitDurationMs),
                                shrinkTowards = exitShrinkTowards
                            ) + fadeOut(animationSpec = tween(exitDurationMs, easing = LinearEasing))
                        ) {
                            RtlAwareTab(
                                isActive = isActive,
                                tabData = entry.data,
                                tabStyle = style,
                                tabIndex = index,
                                tabCount = tabs.size,
                                tabWidth = tabWidth,
                                labelProvider = entry.labelProvider,
                                onClick = entry.onClick,
                                onClose = {
                                    // Trigger exit animation first, then actually remove
                                    closingKeys = closingKeys + entry.key
                                    // After exit animation completes, notify VM and clear from closing set
                                    scope.launch {
                                        delay(exitDurationMs.toLong())
                                        entry.onClose()
                                        closingKeys = closingKeys - entry.key
                                    }
                                },
                                animateWidth = !isNew,
                                enterFromSmall = isNew,
                                enterDurationMs = enterDurationMs
                            )
                        }
                    }
                }
            }

            if (shrinkToFitActive) {
                // Keep plus fixed at trailing edge during shrink-to-fit
                Row(modifier = Modifier.weight(1f).hoverable(interactionSource), verticalAlignment = Alignment.CenterVertically) {
                    TabsOnly()
                }

                Divider(
                    orientation = Orientation.Vertical,
                    modifier = Modifier
                        .fillMaxHeight(0.8f)
                        .padding(horizontal = 4.dp)
                        .width(1.dp),
                    color = JewelTheme.globalColors.borders.disabled
                )

                val os = getOperatingSystem()
                val shortcutHint = if (os == OperatingSystem.MACOS) "⌘+T" else "Ctrl+T"
                TitleBarActionButton(
                    onClick = onAddClick,
                    key = AllIconsKeys.General.Add,
                    contentDescription = stringResource(Res.string.add_tab),
                    tooltipText = stringResource(Res.string.add_tab),
                    shortcutHint = shortcutHint
                )
            } else {
                // Before shrink-to-fit, keep plus flowing with tabs
                Row(modifier = Modifier.hoverable(interactionSource), verticalAlignment = Alignment.CenterVertically) {
                    TabsOnly()

                    Divider(
                        orientation = Orientation.Vertical,
                        modifier = Modifier
                            .fillMaxHeight(0.8f)
                            .padding(horizontal = 4.dp)
                            .width(1.dp),
                        color = JewelTheme.globalColors.borders.disabled
                    )

                    val os = getOperatingSystem()
                    val shortcutHint = if (os == OperatingSystem.MACOS) "⌘+T" else "Ctrl+T"
                    TitleBarActionButton(
                        onClick = onAddClick,
                        key = AllIconsKeys.General.Add,
                        contentDescription = stringResource(Res.string.add_tab),
                        tooltipText = stringResource(Res.string.add_tab),
                        shortcutHint = shortcutHint
                    )
                }
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
    labelProvider: @Composable () -> String,
    onClick: () -> Unit,
    onClose: () -> Unit,
    animateWidth: Boolean = true,
    enterFromSmall: Boolean = false,
    enterDurationMs: Int = 200,
    modifier: Modifier = Modifier
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
                is PressInteraction.Release -> tabState = tabState.copy(pressed = false)

                is HoverInteraction.Enter -> tabState = tabState.copy(hovered = true)
                is HoverInteraction.Exit -> tabState = tabState.copy(hovered = false)
            }
        }
    }

    var closeButtonState by remember(isActive) { mutableStateOf(ButtonState.of(active = isActive)) }
    val lineColor by tabStyle.colors.underlineFor(tabState)
    val lineThickness = tabStyle.metrics.underlineThickness
    val backgroundColor by tabStyle.colors.backgroundFor(state = tabState)
    val resolvedContentColor = tabStyle.colors.contentFor(tabState).value.takeOrElse { LocalContentColor.current }

    CompositionLocalProvider(LocalContentColor provides resolvedContentColor) {
        val animatedWidth by animateDpAsState(
            targetValue = tabWidth,
            animationSpec = tween(durationMillis = 200)
        )
        // For brand-new tabs, start from a small width and grow smoothly to target
        val minEnterWidth = minOf(72.dp, tabWidth)
        val widthAnim = remember(enterFromSmall) {
            if (enterFromSmall) Animatable(minEnterWidth, Dp.VectorConverter) else null
        }
        LaunchedEffect(enterFromSmall, tabWidth) {
            if (enterFromSmall) {
                widthAnim?.snapTo(minEnterWidth)
                widthAnim?.animateTo(tabWidth, animationSpec = tween(durationMillis = enterDurationMs, easing = FastOutSlowInEasing))
            }
        }
        val widthForThisTab = when {
            enterFromSmall -> widthAnim?.value ?: tabWidth
            animateWidth -> animatedWidth
            else -> tabWidth
        }
        val container: @Composable () -> Unit = {
        Row(
            modifier
                .height(tabStyle.metrics.tabHeight)
                .width(widthForThisTab)
                .background(backgroundColor)
                .selectable(
                    onClick = onClick,
                    selected = tabData.selected,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Tab,
                )
                .drawBehind {
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
                .padding(tabStyle.metrics.tabPadding)
                .onPointerEvent(PointerEventType.Release) {
                    if (it.button.isTertiary) onClose()
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
                                is PressInteraction.Release -> closeButtonState = closeButtonState.copy(pressed = false)
                                is HoverInteraction.Enter -> closeButtonState = closeButtonState.copy(hovered = true)
                                is HoverInteraction.Exit -> closeButtonState = closeButtonState.copy(hovered = false)
                            }
                        }
                    }

                    Icon(
                        key = tabStyle.icons.close,
                        modifier = Modifier
                            .clickable(
                                interactionSource = closeActionInteractionSource,
                                indication = null,
                                onClick = onClose,
                                role = Role.Button,
                            )
                            .size(16.dp),
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
        val showTooltip = label.isNotBlank() &&
                (label.length > AppSettings.MAX_TAB_TITLE_LENGTH || tabWidth < TabTooltipWidthThreshold)

        if (showTooltip) Tooltip({ Text(label) }) { container() } else container()
    }
}

// TabContentScopeContainer implementation (same as Jewel's internal)
private class TabContentScopeContainer : TabContentScope {
    @Composable
    override fun Modifier.tabContentAlpha(state: TabState): Modifier =
        alpha(JewelTheme.defaultTabStyle.contentAlpha.contentFor(state).value)
}

@Composable
private fun SingleLineTabContent(
    label: String,
    state: TabState,
    icon: Painter?,
    modifier: Modifier = Modifier
) {
    val contentAlpha = JewelTheme.defaultTabStyle.contentAlpha.contentFor(state).value
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp).alpha(contentAlpha)
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
