package io.github.kdroidfilter.seforimapp.features.siddur

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import java.util.UUID

/** Brings the window's siddur tab forward, or opens one on the tefila of the hour. */
fun openSiddurTab(tabsViewModel: TabsViewModel) {
    val existing =
        tabsViewModel.state.value.tabs
            .indexOfFirst { it.destination is TabsDestination.Siddur }
    if (existing >= 0) {
        tabsViewModel.onEvent(TabsEvents.OnSelect(existing))
    } else {
        tabsViewModel.openTab(TabsDestination.Siddur(tabId = UUID.randomUUID().toString()))
    }
}
