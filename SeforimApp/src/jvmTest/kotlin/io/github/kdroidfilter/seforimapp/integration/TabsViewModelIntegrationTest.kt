package io.github.kdroidfilter.seforimapp.integration

import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Integration tests for [TabsViewModel].
 * Tests the full tab lifecycle including creation, selection, closure, and reordering.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TabsViewModelIntegrationTest {
    private lateinit var titleUpdateManager: TabTitleUpdateManager
    private lateinit var viewModel: TabsViewModel

    @BeforeTest
    fun setup() {
        titleUpdateManager = TabTitleUpdateManager()
        val startDestination =
            TabsDestination.BookContent(
                bookId = -1,
                tabId = UUID.randomUUID().toString(),
            )
        viewModel =
            TabsViewModel(
                titleUpdateManager = titleUpdateManager,
                startDestination = startDestination,
            )
    }

    // ==================== Initialization Tests ====================

    @Test
    fun `viewModel initializes with one tab`() =
        runTest {
            val tabs = viewModel.state.value.tabs

            assertEquals(1, tabs.size)
            assertEquals(0, viewModel.state.value.selectedTabIndex)
        }

    @Test
    fun `initial tab has correct structure`() =
        runTest {
            val tab =
                viewModel.state.value.tabs
                    .first()

            assertEquals(1, tab.id)
            assertTrue(tab.destination is TabsDestination.BookContent)
        }

    // ==================== Tab Addition Tests ====================

    @Test
    fun `OnAdd event creates new tab at beginning`() =
        runTest {
            val initialCount = viewModel.state.value.tabs.size

            viewModel.onEvent(TabsEvents.OnAdd)

            assertEquals(initialCount + 1, viewModel.state.value.tabs.size)
            assertEquals(0, viewModel.state.value.selectedTabIndex) // New tab is selected
        }

    @Test
    fun `openTab creates tab with specific destination`() =
        runTest {
            val bookId = 123L
            val tabId = UUID.randomUUID().toString()
            val destination = TabsDestination.BookContent(bookId = bookId, tabId = tabId)

            viewModel.openTab(destination)

            val tabs = viewModel.state.value.tabs
            assertEquals(2, tabs.size)
            val newTab = tabs.first() // New tab is at the beginning
            assertTrue(newTab.destination is TabsDestination.BookContent)
            assertEquals(bookId, (newTab.destination as TabsDestination.BookContent).bookId)
        }

    @Test
    fun `openTab with Search destination creates search tab`() =
        runTest {
            val searchQuery = "Torah"
            val tabId = UUID.randomUUID().toString()
            val destination = TabsDestination.Search(searchQuery = searchQuery, tabId = tabId)

            viewModel.openTab(destination)

            val tabs = viewModel.state.value.tabs
            val newTab = tabs.first()
            assertTrue(newTab.destination is TabsDestination.Search)
            assertEquals(searchQuery, (newTab.destination as TabsDestination.Search).searchQuery)
        }

    @Test
    fun `openTab with Home destination creates home tab`() =
        runTest {
            val tabId = UUID.randomUUID().toString()
            val destination = TabsDestination.Home(tabId = tabId)

            viewModel.openTab(destination)

            val tabs = viewModel.state.value.tabs
            val newTab = tabs.first()
            assertTrue(newTab.destination is TabsDestination.Home)
        }

    @Test
    fun `multiple tabs can be opened`() =
        runTest {
            repeat(5) {
                viewModel.onEvent(TabsEvents.OnAdd)
            }

            assertEquals(6, viewModel.state.value.tabs.size) // 1 initial + 5 added
        }

    // ==================== Tab Selection Tests ====================

    @Test
    fun `OnSelect event changes selected tab`() =
        runTest {
            // Add more tabs
            viewModel.onEvent(TabsEvents.OnAdd)
            viewModel.onEvent(TabsEvents.OnAdd)

            viewModel.onEvent(TabsEvents.OnSelect(2))

            assertEquals(2, viewModel.state.value.selectedTabIndex)
        }

    @Test
    fun `OnSelect with invalid index does nothing`() =
        runTest {
            val initialIndex = viewModel.state.value.selectedTabIndex

            viewModel.onEvent(TabsEvents.OnSelect(999))

            assertEquals(initialIndex, viewModel.state.value.selectedTabIndex)
        }

    @Test
    fun `OnSelect with negative index does nothing`() =
        runTest {
            val initialIndex = viewModel.state.value.selectedTabIndex

            viewModel.onEvent(TabsEvents.OnSelect(-1))

            assertEquals(initialIndex, viewModel.state.value.selectedTabIndex)
        }

    // ==================== Tab Closure Tests ====================

    @Test
    fun `OnClose removes tab and adjusts selection`() =
        runTest {
            // Add tabs
            viewModel.onEvent(TabsEvents.OnAdd)
            viewModel.onEvent(TabsEvents.OnAdd)
            assertEquals(3, viewModel.state.value.tabs.size)

            // Select middle tab
            viewModel.onEvent(TabsEvents.OnSelect(1))

            // Close the selected tab
            viewModel.onEvent(TabsEvents.OnClose(1))

            assertEquals(2, viewModel.state.value.tabs.size)
        }

    @Test
    fun `closing last remaining tab replaces it with fresh tab`() =
        runTest {
            val originalTabId =
                viewModel.state.value.tabs
                    .first()
                    .destination.tabId

            viewModel.onEvent(TabsEvents.OnClose(0))

            // Should still have one tab
            assertEquals(1, viewModel.state.value.tabs.size)
            // But with a different tabId
            assertNotEquals(
                originalTabId,
                viewModel.state.value.tabs
                    .first()
                    .destination.tabId,
            )
        }

    @Test
    fun `CloseAll replaces all tabs with single fresh tab`() =
        runTest {
            // Add multiple tabs
            viewModel.onEvent(TabsEvents.OnAdd)
            viewModel.onEvent(TabsEvents.OnAdd)
            viewModel.onEvent(TabsEvents.OnAdd)
            assertEquals(4, viewModel.state.value.tabs.size)

            viewModel.onEvent(TabsEvents.CloseAll)

            assertEquals(1, viewModel.state.value.tabs.size)
            assertEquals(0, viewModel.state.value.selectedTabIndex)
        }

    @Test
    fun `CloseOthers keeps only the specified tab`() =
        runTest {
            // Add tabs
            viewModel.onEvent(TabsEvents.OnAdd)
            viewModel.onEvent(TabsEvents.OnAdd)
            viewModel.onEvent(TabsEvents.OnAdd)
            assertEquals(4, viewModel.state.value.tabs.size)

            val targetTab = viewModel.state.value.tabs[2]
            viewModel.onEvent(TabsEvents.CloseOthers(2))

            assertEquals(1, viewModel.state.value.tabs.size)
            assertEquals(
                targetTab.id,
                viewModel.state.value.tabs
                    .first()
                    .id,
            )
        }

    @Test
    fun `CloseLeft closes all tabs to the left`() =
        runTest {
            // Add tabs
            viewModel.onEvent(TabsEvents.OnAdd)
            viewModel.onEvent(TabsEvents.OnAdd)
            viewModel.onEvent(TabsEvents.OnAdd)
            assertEquals(4, viewModel.state.value.tabs.size)

            viewModel.onEvent(TabsEvents.CloseLeft(2))

            assertEquals(2, viewModel.state.value.tabs.size)
        }

    @Test
    fun `CloseRight closes all tabs to the right`() =
        runTest {
            // Add tabs
            viewModel.onEvent(TabsEvents.OnAdd)
            viewModel.onEvent(TabsEvents.OnAdd)
            viewModel.onEvent(TabsEvents.OnAdd)
            assertEquals(4, viewModel.state.value.tabs.size)

            viewModel.onEvent(TabsEvents.CloseRight(1))

            assertEquals(2, viewModel.state.value.tabs.size)
        }

    // ==================== Tab Reordering Tests ====================

    @Test
    fun `OnReorder moves tab from one position to another`() =
        runTest {
            // Add tabs
            viewModel.onEvent(TabsEvents.OnAdd)
            viewModel.onEvent(TabsEvents.OnAdd)
            viewModel.onEvent(TabsEvents.OnAdd)

            val originalFirstTabId =
                viewModel.state.value.tabs[0]
                    .id
            val originalLastTabId =
                viewModel.state.value.tabs[3]
                    .id

            // Move first tab to last position
            viewModel.onEvent(TabsEvents.OnReorder(0, 3))

            assertEquals(
                originalFirstTabId,
                viewModel.state.value.tabs[3]
                    .id,
            )
            assertNotEquals(
                originalFirstTabId,
                viewModel.state.value.tabs[0]
                    .id,
            )
        }

    @Test
    fun `OnReorder with same indices does nothing`() =
        runTest {
            viewModel.onEvent(TabsEvents.OnAdd)
            viewModel.onEvent(TabsEvents.OnAdd)

            val tabsBefore =
                viewModel.state.value.tabs
                    .map { it.id }

            viewModel.onEvent(TabsEvents.OnReorder(1, 1))

            val tabsAfter =
                viewModel.state.value.tabs
                    .map { it.id }
            assertEquals(tabsBefore, tabsAfter)
        }

    @Test
    fun `OnReorder with invalid indices does nothing`() =
        runTest {
            val tabsBefore =
                viewModel.state.value.tabs
                    .toList()

            viewModel.onEvent(TabsEvents.OnReorder(-1, 999))

            assertEquals(tabsBefore, viewModel.state.value.tabs)
        }

    // ==================== Replace Tab Tests ====================

    @Test
    fun `replaceCurrentTabDestination preserves tabId`() =
        runTest {
            val originalTabId =
                viewModel.state.value.tabs
                    .first()
                    .destination.tabId
            val newDestination = TabsDestination.Search(searchQuery = "test", tabId = "ignored")

            viewModel.replaceCurrentTabDestination(newDestination)

            val updatedTab =
                viewModel.state.value.tabs
                    .first()
            assertEquals(originalTabId, updatedTab.destination.tabId)
            assertTrue(updatedTab.destination is TabsDestination.Search)
        }

    @Test
    fun `replaceCurrentTabWithNewTabId creates new tabId`() =
        runTest {
            val originalTabId =
                viewModel.state.value.tabs
                    .first()
                    .destination.tabId
            val newDestination = TabsDestination.BookContent(bookId = 100, tabId = "ignored")

            viewModel.replaceCurrentTabWithNewTabId(newDestination)

            val updatedTab =
                viewModel.state.value.tabs
                    .first()
            assertNotEquals(originalTabId, updatedTab.destination.tabId)
        }

    // ==================== Session Restore Tests ====================

    @Test
    fun `restoreTabs replaces all tabs with provided destinations`() =
        runTest {
            val destinations =
                listOf(
                    TabsDestination.BookContent(bookId = 1, tabId = "tab1"),
                    TabsDestination.Search(searchQuery = "Torah", tabId = "tab2"),
                    TabsDestination.Home(tabId = "tab3"),
                )

            viewModel.restoreTabs(destinations, selectedIndex = 1)

            assertEquals(3, viewModel.state.value.tabs.size)
            assertEquals(1, viewModel.state.value.selectedTabIndex)
        }

    @Test
    fun `restoreTabs with empty list does nothing`() =
        runTest {
            val tabsBefore =
                viewModel.state.value.tabs
                    .toList()

            viewModel.restoreTabs(emptyList(), selectedIndex = 0)

            assertEquals(tabsBefore, viewModel.state.value.tabs)
        }

    @Test
    fun `restoreTabs clamps selectedIndex to valid range`() =
        runTest {
            val destinations =
                listOf(
                    TabsDestination.BookContent(bookId = 1, tabId = "tab1"),
                    TabsDestination.BookContent(bookId = 2, tabId = "tab2"),
                )

            viewModel.restoreTabs(destinations, selectedIndex = 999)

            assertEquals(1, viewModel.state.value.selectedTabIndex) // Clamped to last valid index
        }

    // ==================== Title Update Tests ====================

    @Test
    fun `title update manager updates tab title`() =
        runTest {
            val tabId =
                viewModel.state.value.tabs
                    .first()
                    .destination.tabId
            val newTitle = "Updated Title"

            titleUpdateManager.updateTabTitle(tabId, newTitle, TabType.BOOK)

            // Allow the coroutine to process
            kotlinx.coroutines.delay(100)

            val tab =
                viewModel.state.value.tabs
                    .first()
            assertEquals(newTitle, tab.title)
            assertEquals(TabType.BOOK, tab.tabType)
        }

    // ==================== Tab Type Tests ====================

    @Test
    fun `BookContent with positive bookId has BOOK type`() =
        runTest {
            val destination =
                TabsDestination.BookContent(
                    bookId = 123,
                    tabId = UUID.randomUUID().toString(),
                )

            viewModel.openTab(destination)

            val tab =
                viewModel.state.value.tabs
                    .first()
            assertEquals(TabType.BOOK, tab.tabType)
        }

    @Test
    fun `BookContent with negative bookId has SEARCH type`() =
        runTest {
            val destination =
                TabsDestination.BookContent(
                    bookId = -1,
                    tabId = UUID.randomUUID().toString(),
                )

            viewModel.openTab(destination)

            val tab =
                viewModel.state.value.tabs
                    .first()
            assertEquals(TabType.SEARCH, tab.tabType)
        }

    @Test
    fun `Search destination has SEARCH type`() =
        runTest {
            val destination =
                TabsDestination.Search(
                    searchQuery = "test",
                    tabId = UUID.randomUUID().toString(),
                )

            viewModel.openTab(destination)

            val tab =
                viewModel.state.value.tabs
                    .first()
            assertEquals(TabType.SEARCH, tab.tabType)
        }

    @Test
    fun `Home destination has SEARCH type`() =
        runTest {
            val destination =
                TabsDestination.Home(
                    tabId = UUID.randomUUID().toString(),
                )

            viewModel.openTab(destination)

            val tab =
                viewModel.state.value.tabs
                    .first()
            assertEquals(TabType.SEARCH, tab.tabType)
        }

    // ==================== Edge Case Tests ====================

    @Test
    fun `closing tab before selected adjusts selection index`() =
        runTest {
            // Add tabs: [0, 1, 2]
            viewModel.onEvent(TabsEvents.OnAdd)
            viewModel.onEvent(TabsEvents.OnAdd)

            // Select last tab (index 2)
            viewModel.onEvent(TabsEvents.OnSelect(2))
            assertEquals(2, viewModel.state.value.selectedTabIndex)

            // Close first tab (index 0)
            viewModel.onEvent(TabsEvents.OnClose(0))

            // Selected index should decrease by 1
            assertEquals(1, viewModel.state.value.selectedTabIndex)
        }

    @Test
    fun `closing tab after selected keeps selection index`() =
        runTest {
            // Add tabs: [0, 1, 2]
            viewModel.onEvent(TabsEvents.OnAdd)
            viewModel.onEvent(TabsEvents.OnAdd)

            // Select first tab (index 0)
            viewModel.onEvent(TabsEvents.OnSelect(0))
            assertEquals(0, viewModel.state.value.selectedTabIndex)

            // Close last tab (index 2)
            viewModel.onEvent(TabsEvents.OnClose(2))

            // Selected index should remain 0
            assertEquals(0, viewModel.state.value.selectedTabIndex)
        }

    @Test
    fun `tab ids are unique across all operations`() =
        runTest {
            // Perform various operations
            viewModel.onEvent(TabsEvents.OnAdd)
            viewModel.onEvent(TabsEvents.OnAdd)
            viewModel.onEvent(TabsEvents.OnClose(1))
            viewModel.onEvent(TabsEvents.OnAdd)
            viewModel.onEvent(TabsEvents.OnAdd)

            val tabIds =
                viewModel.state.value.tabs
                    .map { it.id }
            assertEquals(tabIds.size, tabIds.toSet().size) // All IDs should be unique
        }
}
