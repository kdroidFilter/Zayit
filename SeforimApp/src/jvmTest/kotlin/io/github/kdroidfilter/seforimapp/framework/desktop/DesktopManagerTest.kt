package io.github.kdroidfilter.seforimapp.framework.desktop

import io.github.kdroidfilter.seforim.desktop.VirtualDesktop
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import io.github.kdroidfilter.seforimapp.framework.session.DesktopTabsSnapshot
import io.github.kdroidfilter.seforimapp.framework.session.DesktopsState
import io.github.kdroidfilter.seforimapp.framework.session.SerializableTabTitle
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimapp.framework.session.TabThumbnailStore
import io.github.kdroidfilter.seforimapp.framework.session.WindowSnapshot
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The desktop layer without windows: the tabs only reach their workspace groups once `Tab`
 * declares them in composition, so these cover what must hold before that — session restore and
 * save, and the desktop lifecycle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopManagerTest {
    private val store = TabPersistedStateStore()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun manager(bootState: DesktopsState? = null) =
        DesktopManager(
            tabPersistedStateStore = store,
            thumbnails =
                TabThumbnailStore(
                    kotlin.io.path
                        .createTempDirectory()
                        .toFile(),
                ),
            titleUpdateManager = TabTitleUpdateManager(),
            searchHomeViewModelFactory = {
                SearchHomeViewModel(TabPersistedStateStore(), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
            },
            defaultDesktopName = "D1",
            bootState = bootState,
        )

    @Test
    fun `boot opens one desktop with a Home tab, also from an empty saved session`() {
        val fresh = manager()
        assertEquals(1, fresh.sessions.value.size)
        assertEquals(
            1,
            fresh.sessions.value
                .single()
                .tabs.size,
        )

        val fromEmpty = manager(bootState = DesktopsState())
        assertEquals(1, fromEmpty.sessions.value.size)
    }

    @Test
    fun `a restored session saves back unchanged before its tabs are declared`() {
        val book = TabsDestination.BookContent(bookId = 7, tabId = "t1")
        val search = TabsDestination.Search(searchQuery = "q", tabId = "t2")
        val window =
            WindowSnapshot(
                destinations = listOf(book, search),
                selectedIndex = 1,
                titles = mapOf("t1" to SerializableTabTitle("Book", TabType.BOOK)),
            )
        val state =
            DesktopsState(
                desktops = listOf(VirtualDesktop(id = "a", name = "A"), VirtualDesktop(id = "b", name = "B")),
                activeDesktopId = "a",
                snapshots =
                    mapOf(
                        "a" to DesktopTabsSnapshot(tabStates = mapOf("t1" to TabPersistedState()), windows = listOf(window)),
                        "b" to DesktopTabsSnapshot(),
                    ),
                openDesktopIds = listOf("a"),
                focusedDesktopId = "a",
            )

        val dm = manager(bootState = state)

        assertEquals(listOf("a"), dm.openDesktopIds())
        assertEquals(
            "Book",
            dm.sessions.value
                .single()
                .item("t1")
                ?.title,
        )
        val saved = dm.buildDesktopsState()
        val savedWindow =
            saved.snapshots
                .getValue("a")
                .windows
                .single()
        assertEquals(listOf("t1", "t2"), savedWindow.destinations.map { it.tabId })
        assertEquals(1, savedWindow.selectedIndex)
        assertTrue("t1" in saved.snapshots.getValue("a").tabStates)
        // The dormant desktop is kept as it was.
        assertTrue(
            saved.snapshots
                .getValue("b")
                .windows
                .isEmpty(),
        )
    }

    @Test
    fun `new desktop in a new window opens beside the others and deleting it closes it`() {
        val dm = manager()
        val id = dm.createDesktopInNewWindow("D2")
        assertEquals(2, dm.sessions.value.size)
        assertTrue(dm.isDesktopOpen(id))

        dm.deleteDesktop(id)
        assertFalse(dm.isDesktopOpen(id))
        assertEquals(1, dm.desktops.value.size)
        assertEquals(1, dm.sessions.value.size)
    }

    @Test
    fun `closing a desktop's only window puts it to sleep, the app's last window is never closed here`() {
        val dm = manager()
        val second = dm.createDesktopInNewWindow("D2")
        assertEquals(2, dm.windows.value.size)

        dm.closeWindow(dm.windowsOf(second).single().id)
        assertFalse(dm.isDesktopOpen(second))
        assertTrue(dm.desktops.value.any { it.id == second })
        assertEquals(1, dm.windows.value.size)

        dm.closeWindow(
            dm.windows.value
                .single()
                .id,
        )
        assertEquals(1, dm.windows.value.size, "the quit path is the caller's")
    }

    @Test
    fun `switching desktop keeps the window and brings the desktop back as it was`() {
        val dm = manager()
        val window = dm.windows.value.single()
        val first = window.desktopId.value
        val firstTabs = window.session.tabIds()

        val second = dm.createDesktop(window.id, "D2")
        assertSame(window, dm.windows.value.single(), "switched in place")
        assertEquals(second, window.desktopId.value)

        window.clearSwitching() // TabsContent clears it once the desktop has rendered
        dm.switchTo(window.id, first)
        assertSame(window, dm.windows.value.single())
        assertEquals(first, window.desktopId.value)
        assertEquals(firstTabs, window.session.tabIds())
    }
}
