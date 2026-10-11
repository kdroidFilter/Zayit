package io.github.kdroidfilter.seforimapp.core.e2e

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import java.util.UUID

/** The notes page, then its latest note opened as the page opens it: on its line, with the notes pane. Read only. */
object E2eNotesScenario {
    suspend fun run(sc: E2eScenario) {
        if (E2e.scenario != "notes") return
        val graph = sc.graph()
        val tabs =
            graph.desktopManager.windows.value
                .first()
                .tabsViewModel
        tabs.openTab(TabsDestination.Notes(tabId = UUID.randomUUID().toString()))
        sc.step("n1-notes-page", 4000)
        val latest = graph.noteStore.recent(1).firstOrNull() ?: return sc.note("no notes to open")
        tabs.openTab(
            TabsDestination.BookContent(
                bookId = latest.bookId,
                tabId = UUID.randomUUID().toString(),
                lineId = latest.note.lineId,
                openNotes = true,
            ),
        )
        sc.step("n2-note-opened", 6000)
    }
}
