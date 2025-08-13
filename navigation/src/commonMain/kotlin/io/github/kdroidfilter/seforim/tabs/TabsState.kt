package io.github.kdroidfilter.seforim.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState

data class TabsState(
    val tabs: List<TabItem>,
    val selectedTabIndex: Int,
)

@Composable
fun rememberTabsState(viewModel: TabsViewModel): TabsState {
    return TabsState(
        tabs = viewModel.tabs.collectAsState().value,
        selectedTabIndex = viewModel.selectedTabIndex.collectAsState().value,
    )
}
