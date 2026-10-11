package io.github.kdroidfilter.seforimapp.core.e2e

import io.github.kdroidfilter.seforim.desktop.VirtualDesktop
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.session.DesktopTabsSnapshot
import io.github.kdroidfilter.seforimapp.framework.session.DesktopsState
import io.github.kdroidfilter.seforimapp.framework.session.SavedGeometry
import io.github.kdroidfilter.seforimapp.framework.session.WindowSnapshot
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

/**
 * The scripted scenario of [E2e]. [runCommon] only uses what the app had before the tab
 * workspace (DesktopManager, TabsViewModel, BookContentViewModel events), so the same script runs
 * on both versions and their exports can be compared pixel for pixel.
 */
object E2eScenario {
    private const val STEP_MS = 2500L
    private const val LOAD_MS = 6000L
    private const val E2E_TEXT_SIZE = 27.381027f

    private lateinit var graph: AppGraph
    private val log = StringBuilder()

    suspend fun run(
        appGraph: AppGraph,
        extra: suspend (E2eScenario) -> Unit = {},
        quit: () -> Unit,
    ) {
        graph = appGraph
        val state = appGraph.mainAppState
        val theme = state.theme.value
        val textSize = graph.appSettings.getTextSize()
        // Every run starts from the same reading settings, whatever the machine was left at.
        graph.appSettings.setTextSize(E2E_TEXT_SIZE)
        val restoreLayout = E2eMemoryScenario.withoutWidgets(appGraph)
        try {
            runCommon()
            extra(this)
        } catch (t: Throwable) {
            note("FAILED: $t\n${t.stackTraceToString()}")
        } finally {
            state.setTheme(theme)
            graph.appSettings.setTextSize(textSize)
            restoreLayout()
            E2e.outDir?.let { File(it, "log.txt").writeText(synchronized(log) { log.toString() }) }
            quit()
        }
    }

    fun graph(): AppGraph = graph

    /** Whether the run opens on Bereshit rather than the Home (`ZAYIT_E2E_START_BOOK=1`), for [E2eMemoryScenario]. */
    val startsOnBook: Boolean = System.getenv("ZAYIT_E2E_START_BOOK") == "1"

    /** Thread-safe: the torture's watchdog notes from its own thread. */
    fun note(line: String) {
        synchronized(log) { log.appendLine(line) }
        println("E2E $line")
    }

    private val dm get() = graph.desktopManager

    private fun window(index: Int = 0) = dm.windows.value[index]

    private fun currentTabId(index: Int = 0): String {
        val tabs = window(index).tabsViewModel.state.value
        return tabs.tabs[tabs.selectedTabIndex].destination.tabId
    }

    private fun bookEvent(event: BookContentEvent) {
        val tabId = currentTabId()
        val vm = E2e.bookViewModel(tabId) ?: error("no book ViewModel for $tabId")
        vm.onEvent(event)
    }

    /** Waits, then exports every window as `<name>-w<i>.png`. */
    suspend fun step(
        name: String,
        waitMs: Long = STEP_MS,
    ) {
        delay(waitMs)
        dm.windows.value.forEachIndexed { i, w ->
            val ok = E2e.capture(w.id, "$name-w$i")
            E2e.capture(w.id + E2e.TITLE_BAR, "$name-w$i-title")
            if (!ok) note("$name: no capture for window $i")
        }
        val scroll =
            runCatching {
                graph.tabPersistedStateStore.get(currentTabId())?.bookContent?.let {
                    "scroll=${it.contentScrollIndex}/${it.contentScrollOffset} anchor=${it.contentAnchorLineId}@${it.contentAnchorIndex}"
                }
            }.getOrNull()
        val tabs = dm.windows.value.map { it.tabsViewModel.state.value.tabs.size }
        note("$name: text=${graph.appSettings.textSizeFlow.value} ${dm.windows.value.size} window(s), tabs=$tabs $scroll")
    }

    private suspend fun runCommon() {
        val state = graph.mainAppState
        state.setTheme(IntUiThemes.Light)
        val bereshit = graph.repository.getBookByTitle("בראשית")?.id ?: error("book not found")
        val desktop = "e2e-a"
        dm.restoreFromDesktopsState(
            DesktopsState(
                desktops = listOf(VirtualDesktop(id = desktop, name = "A")),
                activeDesktopId = desktop,
                snapshots =
                    mapOf(
                        desktop to
                            DesktopTabsSnapshot(
                                windows =
                                    listOf(
                                        WindowSnapshot(
                                            destinations =
                                                listOf(
                                                    TabsDestination.BookContent(
                                                        bookId = if (startsOnBook) bereshit else -1,
                                                        tabId = UUID.randomUUID().toString(),
                                                    ),
                                                ),
                                            geometry =
                                                SavedGeometry(
                                                    x = 80,
                                                    y = 60,
                                                    width = 1400,
                                                    height = 900,
                                                    placement = "Maximized",
                                                ),
                                        ),
                                    ),
                            ),
                    ),
                openDesktopIds = listOf(desktop),
                focusedDesktopId = desktop,
            ),
        )
        step("01-home", LOAD_MS)
        // Only the setup, for runs (a torture over many seeds) that start from a clean desktop and need nothing else
        if (System.getenv("ZAYIT_E2E_QUICK") == "1") return

        window().tabsViewModel.replaceCurrentTabDestination(TabsDestination.BookContent(bookId = bereshit, tabId = currentTabId()))
        step("02-book", LOAD_MS)
        bookEvent(BookContentEvent.ToggleCommentaries)
        step("03-commentaries")
        bookEvent(BookContentEvent.ToggleTargum)
        step("04-targum")
        bookEvent(BookContentEvent.ToggleToc)
        step("05-toc")
        bookEvent(BookContentEvent.ToggleBookTree)
        step("06-tree")
        bookEvent(BookContentEvent.ToggleNotes)
        step("07-notes")
        bookEvent(BookContentEvent.ToggleSources)
        step("08-sources")

        window().tabsViewModel.onEvent(TabsEvents.OnAdd)
        step("09-newtab", LOAD_MS)
        window().tabsViewModel.replaceCurrentTabDestination(TabsDestination.Search(searchQuery = "בראשית ברא", tabId = currentTabId()))
        step("10-search", LOAD_MS)
        window().tabsViewModel.openTab(TabsDestination.History(tabId = UUID.randomUUID().toString()))
        step("11-history")
        window().tabsViewModel.openTab(TabsDestination.Favorites(tabId = UUID.randomUUID().toString()))
        step("12-favorites")

        val bookIndex =
            window().tabsViewModel.state.value.tabs.indexOfFirst {
                (it.destination as? TabsDestination.BookContent)?.bookId ==
                    bereshit
            }
        window().tabsViewModel.onEvent(TabsEvents.OnSelect(bookIndex))
        step("13-back-to-book")
        state.setTheme(IntUiThemes.Dark)
        step("15-dark")

        dm.createDesktop(window().id, "B")
        step("17-new-desktop", LOAD_MS)
        step("17b-new-desktop-later", LOAD_MS * 2)
        dm.switchToPrevious(window().id)
        step("18-back-to-desktop-a", LOAD_MS)
        dm.createDesktopInNewWindow("C")
        step("19-desktop-in-new-window", LOAD_MS)
    }
}
