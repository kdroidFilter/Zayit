package io.github.kdroidfilter.seforimapp.core.e2e

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * Book tabs through repeated cold restarts (the session saved and restored as at boot): every restored book
 * tab, once selected, must leave the loader and show its book. Knob: `ZAYIT_E2E_ROUNDS`.
 */
object E2eRestoreScenario {
    private const val TABS = 6
    private const val LOAD_LIMIT_MS = 10_000L
    private const val POLL_MS = 100L

    suspend fun run(sc: E2eScenario) {
        if (E2e.scenario != "restore") return
        val graph = sc.graph()
        val dm = graph.desktopManager
        val rounds = System.getenv("ZAYIT_E2E_ROUNDS")?.toIntOrNull() ?: 8
        val books =
            listOf("בראשית", "שמות", "ויקרא", "במדבר", "דברים", "יהושע")
                .mapNotNull { graph.repository.getBookByTitle(it)?.id }
                .take(TABS)
        val tabs =
            dm.windows.value
                .first()
                .tabsViewModel
        books.forEach { tabs.openTab(TabsDestination.BookContent(bookId = it, tabId = UUID.randomUUID().toString())) }
        sc.step("r0-opened", 6000)

        var stuck = 0
        repeat(rounds) { round ->
            dm.restoreFromDesktopsState(dm.buildDesktopsState())
            // Selected at once, as a user clicking through the restored tabs would
            delay(if (round % 2 == 0) 0 else 400)
            val w = dm.windows.value.first()
            val ids =
                w.tabsViewModel.state.value.tabs
                    .map { it.destination }
            ids.forEachIndexed { i, dest ->
                if (dest !is TabsDestination.BookContent || dest.bookId <= 0) return@forEachIndexed
                w.tabsViewModel.onEvent(TabsEvents.OnSelect(i))
                var waited = 0L
                while (waited < LOAD_LIMIT_MS && !loaded(dest.tabId, w)) {
                    delay(POLL_MS)
                    waited += POLL_MS
                }
                if (!loaded(dest.tabId, w)) {
                    stuck++
                    sc.note(
                        "STUCK round $round tab $i book ${dest.bookId}: ${describe(
                            dest.tabId,
                        )} switching=${w.isSwitching.value} panesReadyFor=${w.panesReadyFor}",
                    )
                    sc.step("r$round-stuck-$i", 0)
                }
            }
            sc.note("round $round done")
        }
        sc.note("RESTORE rounds=$rounds stuck=$stuck")
    }

    private fun loaded(
        tabId: String,
        w: OpenWindow,
    ): Boolean {
        // The text lays out only once the window's panes are ready for it (BookContentPanel)
        if (w.isSwitching.value || w.panesReadyFor != tabId) return false
        val s = E2e.bookViewModel(tabId)?.uiState?.value ?: return false
        return s.navigation.selectedBook != null && !s.isLoading
    }

    private fun describe(tabId: String): String {
        val s = E2e.bookViewModel(tabId)?.uiState?.value ?: return "no ViewModel"
        return "book=${s.navigation.selectedBook?.id} isLoading=${s.isLoading}"
    }
}
