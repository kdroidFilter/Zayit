package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.core.presentation.components.TitleBarActionButton
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import seforimapp.composeapp.generated.resources.Res
import seforimapp.composeapp.generated.resources.add_tab
import org.jetbrains.jewel.ui.component.SimpleTabContent
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.hints.Stateful
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun TabsView() {
    val viewModel: TabsViewModel = koinViewModel()
    val state = rememberTabsState(viewModel)
    DefaultTabShowcase(state = state, onEvents = viewModel::onEvent)
}

@Composable
private fun DefaultTabShowcase(onEvents: (TabsEvents) -> Unit, state: TabsState) {
    val tabs = remember(state.tabs, state.selectedTabIndex) {
        state.tabs.mapIndexed { index, tabItem ->
            TabData.Default(
                selected = index == state.selectedTabIndex,
                content = { tabState ->
                    val iconProvider = rememberResourcePainterProvider(AllIconsKeys.Actions.Find)
                    val icon by iconProvider.getPainter(Stateful(tabState))
                    SimpleTabContent(
                        label = tabItem.title,
                        state = tabState,
                        icon = icon
                    )
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
        TabStrip(tabs = tabs, style = style, modifier = Modifier.weight(1f))

        TitleBarActionButton(
            onClick = onAddClick,
            key = AllIconsKeys.General.Add,
            contentDescription = stringResource(Res.string.add_tab),
            tooltipText = stringResource(Res.string.add_tab)
        )
    }
}
