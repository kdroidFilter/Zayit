package io.github.kdroidfilter.seforim.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState

@Immutable
data class TabsState(
    val tabs: List<TabItem>,
    val selectedTabIndex: Int,
)

@Composable
fun rememberTabsState(viewModel: TabsViewModel): TabsState =
    viewModel.state.collectAsState().value
