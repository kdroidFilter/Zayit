package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import io.github.kdroidfilter.seforimapp.core.presentation.components.TitleBarActionButton
import io.github.kdroidfilter.seforimapp.core.presentation.icons.BookOpenTabs
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.hints.Stateful
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import org.koin.compose.viewmodel.koinViewModel
import seforimapp.composeapp.generated.resources.Res
import seforimapp.composeapp.generated.resources.add_tab
import java.awt.Robot
import java.awt.event.KeyEvent

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
fun Modifier.verticalScrollToHorizontal(): Modifier = this.onPointerEvent(PointerEventType.Scroll) { event ->
    val change = event.changes.firstOrNull()
    if (change != null) {
        val scrollDelta = change.scrollDelta

        // If we detect vertical scroll (y != 0) and no horizontal scroll (x == 0)
        if (scrollDelta.y != 0f && scrollDelta.x == 0f) {
            try {
                // Use Robot to simulate Shift + Wheel
                val robot = Robot()

                // Press Shift
                robot.keyPress(KeyEvent.VK_SHIFT)

                // Wait a bit for the key to be properly registered
                Thread.sleep(10)

                // Simulate mouse scroll
                // Note: scrollDelta.y is negative for scroll up, positive for scroll down
                // MouseWheelEvent uses the inverse convention
                val scrollAmount = if (scrollDelta.y > 0) 1 else -1
                robot.mouseWheel(scrollAmount)

                // Release Shift
                Thread.sleep(10)
                robot.keyRelease(KeyEvent.VK_SHIFT)

                // Consume the original event to avoid double scroll
                change.consume()
            } catch (e: Exception) {
                // In case of error (for example on certain platforms where Robot is not available)
                e.printStackTrace()
            }
        }
    }
}

@Composable
fun TabsView() {
    val viewModel: TabsViewModel = koinViewModel()
    val state = rememberTabsState(viewModel)
    DefaultTabShowcase(state = state, onEvents = viewModel::onEvent)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DefaultTabShowcase(onEvents: (TabsEvents) -> Unit, state: TabsState) {
    val tabs = remember(state.tabs, state.selectedTabIndex) {
        state.tabs.mapIndexed { index, tabItem ->
            TabData.Default(
                selected = index == state.selectedTabIndex,
                content = { tabState ->
                    // Use Book_5 icon for book tabs, otherwise use the default Find icon
                    val icon = if (tabItem.tabType == TabType.BOOK) {
                        rememberVectorPainter(BookOpenTabs())
                    } else {
                        val iconProvider = rememberResourcePainterProvider(AllIconsKeys.Actions.Find)
                        iconProvider.getPainter(Stateful(tabState)).value
                    }
                    // Truncate tab title if it's longer than MAX_TAB_TITLE_LENGTH characters
                    val isTruncated = tabItem.title.length > AppSettings.MAX_TAB_TITLE_LENGTH
                    val truncatedTitle = if (isTruncated) {
                        tabItem.title.take(AppSettings.MAX_TAB_TITLE_LENGTH) + "..."
                    } else {
                        tabItem.title
                    }

                    // Add tooltip with full title for truncated tabs
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

    TabStripWithAddButton(tabs = tabs, style = JewelTheme.defaultTabStyle) {
        onEvents(TabsEvents.onAdd)
    }
}

@Composable
private fun TabStripWithAddButton(tabs: List<TabData>, style: TabStyle, onAddClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TabStrip(
            tabs = tabs,
            style = style,
            modifier = Modifier
                .weight(1f)
                .verticalScrollToHorizontal() // Convert vertical scroll to horizontal
        )

        TitleBarActionButton(
            onClick = onAddClick,
            key = AllIconsKeys.General.Add,
            contentDescription = stringResource(Res.string.add_tab),
            tooltipText = stringResource(Res.string.add_tab)
        )
    }
}