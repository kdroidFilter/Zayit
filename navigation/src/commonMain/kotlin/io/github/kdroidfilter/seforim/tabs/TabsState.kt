package io.github.kdroidfilter.seforim.tabs

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class TabItem(
    val id: Int,
    val title: String = "Default Tab",
    val destination: TabsDestination = TabsDestination.Home(UUID.randomUUID().toString()),
    val tabType: TabType = TabType.SEARCH,
    // Pinned tabs stay ahead of the others and are not closed by accident.
    val pinned: Boolean = false,
    // What the tab shows once pinned (a book's acronym); empty for its icon alone.
    val shortTitle: String = "",
)

@Immutable
data class TabsState(
    val tabs: List<TabItem>,
    val selectedTabIndex: Int,
)
