package io.github.kdroidfilter.seforimapp.core.e2e

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import java.util.UUID

/** Pinned tabs on the strip (`ZAYIT_E2E_SCENARIO=pin`): a few tabs, three of them pinned. Read only. */
object E2ePinScenario {
    suspend fun run(sc: E2eScenario) {
        if (E2e.scenario != "pin") return
        val tabs =
            sc
                .graph()
                .desktopManager.windows.value
                .first()
                .tabsViewModel
        tabs.openTab(TabsDestination.History(tabId = UUID.randomUUID().toString()))
        tabs.openTab(TabsDestination.Favorites(tabId = UUID.randomUUID().toString()))
        tabs.openTab(TabsDestination.Search(searchQuery = "בראשית ברא", tabId = UUID.randomUUID().toString()))
        sc.step("pin-01-unpinned", 3000)
        tabs.setPinned(2, true)
        tabs.setPinned(1, true)
        tabs.setPinned(0, true)
        sc.step("pin-02-pinned", 2000)
        val order =
            tabs.state.value.tabs
                .joinToString { "${it.tabType}:${it.pinned}" }
        sc.note("pin order: $order")
    }
}
