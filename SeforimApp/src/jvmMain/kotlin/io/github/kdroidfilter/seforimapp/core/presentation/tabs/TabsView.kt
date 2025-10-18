package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import seforimapp.seforimapp.generated.resources.home

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

    // Track for auto-scrolling
    var previousTabCount by remember { mutableStateOf(state.tabs.size) }
    val newTabAdded = state.tabs.size > previousTabCount

    LaunchedEffect(state.tabs.size) {
        previousTabCount = state.tabs.size
    }

    // Create TabData objects with RTL support
    val tabs = remember(state.tabs, state.selectedTabIndex, isRtl) {
        if (isRtl) {
            // For RTL: reverse the list and use the reversed index for display
            state.tabs.reversed().mapIndexed { visualIndex, tabItem ->
                // The actual index in the original list
                val actualIndex = state.tabs.size - 1 - visualIndex
                val isSelected = actualIndex == state.selectedTabIndex

                TabData.Default(
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

                        val isTruncated = tabItem.title.length > AppSettings.MAX_TAB_TITLE_LENGTH
                        val truncatedTitle = if (isTruncated) {
                            tabItem.title.take(AppSettings.MAX_TAB_TITLE_LENGTH) + "..."
                        } else {
                            tabItem.title.ifEmpty { stringResource(Res.string.home) }
                        }

                        if (isTruncated) {
                            Tooltip({
                                Text(tabItem.title)
                            }) {
                                SimpleTabContent(
                                    label = truncatedTitle,
                                    state = tabState,
                                    icon = icon,
                                )
                            }
                        } else {
                            SimpleTabContent(
                                label = truncatedTitle,
                                state = tabState,
                                icon = icon,
                            )
                        }
                    },
                    onClose = {
                        onEvents(TabsEvents.onClose(actualIndex))
                    },
                    onClick = {
                        onEvents(TabsEvents.onSelected(actualIndex))
                    },
                )
            }
        } else {
            // For LTR: use normal order
            state.tabs.mapIndexed { index, tabItem ->
                val isSelected = index == state.selectedTabIndex

                TabData.Default(
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

                        val isTruncated = tabItem.title.length > AppSettings.MAX_TAB_TITLE_LENGTH
                        val truncatedTitle = if (isTruncated) {
                            tabItem.title.take(AppSettings.MAX_TAB_TITLE_LENGTH) + "..."
                        } else {
                            tabItem.title.ifEmpty { stringResource(Res.string.home) }
                        }

                        if (isTruncated) {
                            Tooltip({
                                Text(tabItem.title)
                            }) {
                                SimpleTabContent(
                                    label = truncatedTitle,
                                    state = tabState,
                                    icon = icon,
                                )
                            }
                        } else {
                            SimpleTabContent(
                                label = truncatedTitle,
                                state = tabState,
                                icon = icon,
                            )
                        }
                    },
                    onClose = {
                        onEvents(TabsEvents.onClose(index))
                    },
                    onClick = {
                        onEvents(TabsEvents.onSelected(index))
                    },
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
    tabs: List<TabData>,
    style: TabStyle,
    isRtl: Boolean,
    newTabAdded: Boolean,
    onAddClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    // On mémorise la taille précédente côté strip
    var lastCount by remember { mutableStateOf(tabs.size) }

    LaunchedEffect(tabs.size, isRtl) {
        val added = tabs.size > lastCount
        lastCount = tabs.size
        if (added) {
            // attendre que le layout mette à jour maxValue
            withFrameNanos { }
            // si première frame = maxValue encore 0, attendre encore une frame
            if (scrollState.maxValue == 0) withFrameNanos { }

            val target = scrollState.maxValue
            scrollState.animateScrollTo(target)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isRtl) {
            TitleBarActionButton(
                onClick = onAddClick,
                key = AllIconsKeys.General.Add,
                contentDescription = stringResource(Res.string.add_tab),
                tooltipText = stringResource(Res.string.add_tab)
            )
        }

        RtlAwareTabStripContent(
            tabs = tabs,
            style = style,
            scrollState = scrollState,
            modifier = Modifier.fillMaxWidth(0.95f)
        )

        Divider(
            orientation = Orientation.Vertical,
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .padding(horizontal = 4.dp)
                .width(1.dp),
            color = JewelTheme.globalColors.borders.disabled
        )

        if (!isRtl) {
            TitleBarActionButton(
                onClick = onAddClick,
                key = AllIconsKeys.General.Add,
                contentDescription = stringResource(Res.string.add_tab),
                tooltipText = stringResource(Res.string.add_tab)
            )
        }
    }
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RtlAwareTabStripContent(
    tabs: List<TabData>,
    style: TabStyle,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val interactionSource = remember { MutableInteractionSource() }
    var isActive by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .verticalWheelToHorizontal(scrollState, multiplier = 80f)
                .horizontalScroll(scrollState, reverseScrolling = isRtl) // keep semantics consistent
                .hoverable(interactionSource)
        ) {
            tabs.forEachIndexed { index, tabData ->
                RtlAwareTab(
                    isActive = isActive,
                    tabData = tabData,
                    tabStyle = style,
                    tabIndex = index,
                    tabCount = tabs.size
                )
            }
        }

        // Scrollbar with RTL-aware adapter
        AnimatedVisibility(
            visible = (scrollState.canScrollForward || scrollState.canScrollBackward),
            enter = fadeIn(tween(durationMillis = 125, easing = LinearEasing)),
            exit = fadeOut(tween(durationMillis = 125, delayMillis = 700, easing = LinearEasing)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            HorizontalScrollbar(
                scrollState,
                style = style.scrollbarStyle,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Custom Tab implementation based on Jewel's internal TabImpl
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RtlAwareTab(
    isActive: Boolean,
    tabData: TabData,
    tabStyle: TabStyle,
    tabIndex: Int,
    tabCount: Int,
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
        Row(
            modifier
                .height(tabStyle.metrics.tabHeight)
                .background(backgroundColor)
                .selectable(
                    onClick = tabData.onClick,
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
                    if (it.button.isTertiary) tabData.onClose()
                },
            horizontalArrangement = Arrangement.spacedBy(tabStyle.metrics.closeContentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tab content
            tabData.content(TabContentScopeContainer(), tabState)

            // Close button logic (same as Jewel's TabImpl)
            val showCloseIcon = when (tabData) {
                is TabData.Default -> tabData.closable
                is TabData.Editor -> tabData.closable && (tabState.isHovered || tabState.isSelected)
            }

            if (showCloseIcon) {
                val closeActionInteractionSource = remember { MutableInteractionSource() }
                LaunchedEffect(closeActionInteractionSource) {
                    closeActionInteractionSource.interactions.collect { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> closeButtonState = closeButtonState.copy(pressed = true)
                            is PressInteraction.Cancel,
                            is PressInteraction.Release -> {
                                closeButtonState = closeButtonState.copy(pressed = false)
                            }

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
                            onClick = tabData.onClose,
                            role = Role.Button,
                        )
                        .size(16.dp),
                    contentDescription = "Close tab",
                    hint = Stateful(closeButtonState),
                )
            } else if (tabData.closable) {
                Spacer(Modifier.size(16.dp))
            }
        }
    }
}

// TabContentScopeContainer implementation (same as Jewel's internal)
private class TabContentScopeContainer : TabContentScope {
    @Composable
    override fun Modifier.tabContentAlpha(state: TabState): Modifier =
        alpha(JewelTheme.defaultTabStyle.contentAlpha.contentFor(state).value)
}
