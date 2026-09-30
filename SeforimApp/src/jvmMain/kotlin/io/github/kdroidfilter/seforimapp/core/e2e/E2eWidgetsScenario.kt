package io.github.kdroidfilter.seforimapp.core.e2e

import io.github.kdroidfilter.seforim.tabs.TabsEvents

/** The Home widgets: the page before, during and after the widget gallery shows, to compare their layouts. */
object E2eWidgetsScenario {
    suspend fun run(sc: E2eScenario) {
        if (E2e.scenario != "widgets") return
        val window =
            sc
                .graph()
                .desktopManager.windows.value
                .first()
        window.tabsViewModel.onEvent(TabsEvents.OnAdd)
        sc.step("w1-home", 6000)
        val widgets = E2e.homeWidgets ?: error("no Home widgets on screen")
        widgets.editingWidgets = true
        sc.step("w2-editing")
        widgets.editingWidgets = false
        sc.step("w3-done")
    }
}
