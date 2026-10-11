package io.github.kdroidfilter.seforimapp.core.e2e

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import java.util.UUID

/** Author pages (רבי עקיבא איגר, רמב"ם), light then dark. Read only. */
object E2eAuthorScenario {
    suspend fun run(sc: E2eScenario) {
        if (E2e.scenario != "author") return
        val graph = sc.graph()
        val tabs =
            graph.desktopManager.windows.value
                .first()
                .tabsViewModel
        for ((name, authorId) in listOf("akiva-eiger" to 113L, "rambam" to 11L)) {
            tabs.openTab(TabsDestination.Author(tabId = UUID.randomUUID().toString(), authorId = authorId))
            sc.step("au-$name-light", 4000)
        }
        graph.mainAppState.setTheme(IntUiThemes.Dark)
        sc.step("au-rambam-dark", 3000)
    }
}
