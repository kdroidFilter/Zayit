package io.github.kdroidfilter.seforimapp.core.e2e

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.siddur.installedSiddur
import java.util.UUID

/**
 * The screenshots of the website and the README (`ZAYIT_E2E_SCENARIO=showcase`, with `ZAYIT_E2E_QUICK=1`): the main
 * screens, light then dark, exported as `sc-<screen>-<theme>-w0.png`. Read only.
 */
object E2eShowcaseScenario {
    private const val LOAD_MS = 7000L
    private const val TEXT_SIZE = 22f

    suspend fun run(sc: E2eScenario) {
        if (E2e.scenario != "showcase") return
        val graph = sc.graph()
        val repository = graph.repository
        val tabs =
            graph.desktopManager.windows.value
                .first()
                .tabsViewModel

        suspend fun lineId(
            title: String,
            heRef: String,
        ): Pair<Long, Long>? {
            val book = repository.getBookByTitle(title) ?: return null
            val line =
                repository
                    .getLines(book.id, 0, book.totalLines)
                    .firstOrNull { it.heRef == heRef } ?: return null
            return book.id to line.id
        }

        val bereshit = lineId("בראשית", "בראשית א, א") ?: error("בראשית א, א not found")
        val berakhot = lineId("ברכות", "ברכות ב., א")
        val shulchanAruch = lineId("שולחן ערוך, אורח חיים", "שולחן ערוך, אורח חיים א, א")
        sc.note("SHOWCASE berakhot=$berakhot shulchanAruch=$shulchanAruch")

        // Each shot with its title bar (the tabs) on top, as `<name>.png`
        suspend fun shot(
            name: String,
            waitMs: Long = 2500L,
        ) {
            sc.step(name, waitMs)
            val window =
                graph.desktopManager.windows.value
                    .first()
            if (!E2e.captureWithTitleBar(window.id, name)) sc.note("$name: no full capture")
        }

        fun currentTabId(): String = tabs.state.value.let { it.tabs[it.selectedTabIndex].destination.tabId }

        fun book(event: BookContentEvent) {
            E2e.bookViewModel(currentTabId())?.onEvent(event) ?: sc.note("no book ViewModel for ${currentTabId()}")
        }

        // The run restores the user's text size at its end
        graph.appSettings.setTextSize(TEXT_SIZE)

        for (theme in listOf(IntUiThemes.Light, IntUiThemes.Dark)) {
            graph.mainAppState.setTheme(theme)
            val t = if (theme == IntUiThemes.Dark) "dark" else "light"

            tabs.onEvent(TabsEvents.OnAdd)
            shot("sc-home-$t", LOAD_MS)

            // The reader: Bereshit with its commentaries and targumim
            tabs.openTab(
                TabsDestination.BookContent(bookId = bereshit.first, tabId = UUID.randomUUID().toString(), lineId = bereshit.second),
            )
            shot("sc-book-$t", LOAD_MS)
            book(BookContentEvent.ToggleCommentaries)
            shot("sc-commentaries-$t")
            book(BookContentEvent.ToggleTargum)
            shot("sc-commentaries-targum-$t")
            book(BookContentEvent.ToggleTargum)
            book(BookContentEvent.ToggleCommentaries)
            book(BookContentEvent.ToggleToc)
            book(BookContentEvent.ToggleBookTree)
            shot("sc-toc-$t")
            book(BookContentEvent.ToggleToc)
            book(BookContentEvent.ToggleBookTree)

            // Shnayim mikra: each verse twice, then its targum
            tabs.openTab(
                TabsDestination.BookContent(
                    bookId = bereshit.first,
                    tabId = UUID.randomUUID().toString(),
                    lineId = bereshit.second,
                    shnayimMikra = true,
                ),
            )
            shot("sc-shnayim-mikra-$t", LOAD_MS)

            berakhot?.let { (bookId, lineId) ->
                tabs.openTab(TabsDestination.BookContent(bookId = bookId, tabId = UUID.randomUUID().toString(), lineId = lineId))
                shot("sc-talmud-$t", LOAD_MS)
                book(BookContentEvent.ToggleCommentaries)
                shot("sc-talmud-commentaries-$t")
                book(BookContentEvent.ToggleCommentaries)
            }
            shulchanAruch?.let { (bookId, lineId) ->
                tabs.openTab(TabsDestination.BookContent(bookId = bookId, tabId = UUID.randomUUID().toString(), lineId = lineId))
                shot("sc-halacha-$t", LOAD_MS)
                book(BookContentEvent.ToggleSources)
                shot("sc-sources-$t")
                book(BookContentEvent.ToggleSources)
                book(BookContentEvent.ToggleBookDetails)
                shot("sc-book-details-$t")
                book(BookContentEvent.ToggleBookDetails)
            }

            tabs.openTab(TabsDestination.Search(searchQuery = "ואהבת לרעך כמוך", tabId = UUID.randomUUID().toString()))
            shot("sc-search-$t", LOAD_MS * 2)
            tabs.openTab(TabsDestination.Search(searchQuery = "כבוד אב ואם", tabId = UUID.randomUUID().toString()))
            shot("sc-search2-$t", LOAD_MS * 2)

            tabs.openTab(TabsDestination.Author(tabId = UUID.randomUUID().toString(), authorId = 11L))
            shot("sc-author-$t", LOAD_MS)

            if (installedSiddur != null) {
                tabs.openTab(TabsDestination.Siddur(tabId = UUID.randomUUID().toString()))
                shot("sc-siddur-$t", LOAD_MS)
            }

            // The Home again, its widgets being edited
            tabs.onEvent(TabsEvents.OnAdd)
            shot("sc-home2-$t", LOAD_MS)
            E2e.homeWidgets?.let { widgets ->
                widgets.editingWidgets = true
                shot("sc-widgets-edit-$t")
                widgets.editingWidgets = false
            }
        }
    }
}
