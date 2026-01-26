package io.github.kdroidfilter.seforimapp.navigation

import io.github.kdroidfilter.seforim.tabs.TabItem
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdate
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TabsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var titleUpdateManager: TabTitleUpdateManager
    private lateinit var viewModel: TabsViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        titleUpdateManager = TabTitleUpdateManager()
        viewModel = TabsViewModel(
            titleUpdateManager = titleUpdateManager,
            startDestination = TabsDestination.Home(tabId = "initial-tab"),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has one tab`() {
        assertEquals(1, viewModel.tabs.value.size)
        assertEquals(0, viewModel.selectedTabIndex.value)
    }

    @Test
    fun `initial tab has correct destination`() {
        val firstTab = viewModel.tabs.value.first()
        assertTrue(firstTab.destination is TabsDestination.Home)
        assertEquals("initial-tab", firstTab.destination.tabId)
    }

    @Test
    fun `OnAdd event adds new tab at beginning`() {
        viewModel.onEvent(TabsEvents.OnAdd)
        assertEquals(2, viewModel.tabs.value.size)
        assertEquals(0, viewModel.selectedTabIndex.value)
        assertTrue(viewModel.tabs.value.first().destination is TabsDestination.BookContent)
    }

    @Test
    fun `OnSelect event changes selected tab index`() {
        viewModel.onEvent(TabsEvents.OnAdd)
        viewModel.onEvent(TabsEvents.OnSelect(1))
        assertEquals(1, viewModel.selectedTabIndex.value)
    }

    @Test
    fun `OnSelect with invalid index does nothing`() {
        viewModel.onEvent(TabsEvents.OnSelect(99))
        assertEquals(0, viewModel.selectedTabIndex.value)
    }

    @Test
    fun `OnSelect with negative index does nothing`() {
        viewModel.onEvent(TabsEvents.OnSelect(-1))
        assertEquals(0, viewModel.selectedTabIndex.value)
    }

    @Test
    fun `OnClose removes tab and adjusts selection`() {
        viewModel.onEvent(TabsEvents.OnAdd)
        viewModel.onEvent(TabsEvents.OnAdd)
        assertEquals(3, viewModel.tabs.value.size)

        viewModel.onEvent(TabsEvents.OnClose(1))
        assertEquals(2, viewModel.tabs.value.size)
    }

    @Test
    fun `OnClose last remaining tab replaces with fresh tab`() {
        viewModel.onEvent(TabsEvents.OnClose(0))
        assertEquals(1, viewModel.tabs.value.size)
        assertTrue(viewModel.tabs.value.first().destination is TabsDestination.BookContent)
    }

    @Test
    fun `OnClose with invalid index does nothing`() {
        viewModel.onEvent(TabsEvents.OnClose(99))
        assertEquals(1, viewModel.tabs.value.size)
    }

    @Test
    fun `OnReorder moves tab to new position`() {
        viewModel.onEvent(TabsEvents.OnAdd)
        viewModel.onEvent(TabsEvents.OnAdd)
        val originalFirst = viewModel.tabs.value[0].id

        viewModel.onEvent(TabsEvents.OnReorder(0, 2))
        assertEquals(originalFirst, viewModel.tabs.value[2].id)
    }

    @Test
    fun `OnReorder with same indices does nothing`() {
        viewModel.onEvent(TabsEvents.OnAdd)
        val originalTabs = viewModel.tabs.value.toList()

        viewModel.onEvent(TabsEvents.OnReorder(0, 0))
        assertEquals(originalTabs.map { it.id }, viewModel.tabs.value.map { it.id })
    }

    @Test
    fun `OnReorder with invalid indices does nothing`() {
        val originalTabs = viewModel.tabs.value.toList()
        viewModel.onEvent(TabsEvents.OnReorder(-1, 5))
        assertEquals(originalTabs.map { it.id }, viewModel.tabs.value.map { it.id })
    }

    @Test
    fun `CloseAll replaces all tabs with fresh one`() {
        viewModel.onEvent(TabsEvents.OnAdd)
        viewModel.onEvent(TabsEvents.OnAdd)
        assertEquals(3, viewModel.tabs.value.size)

        viewModel.onEvent(TabsEvents.CloseAll)
        assertEquals(1, viewModel.tabs.value.size)
        assertEquals(0, viewModel.selectedTabIndex.value)
    }

    @Test
    fun `CloseOthers keeps only specified tab`() {
        viewModel.onEvent(TabsEvents.OnAdd)
        viewModel.onEvent(TabsEvents.OnAdd)
        val middleTabId = viewModel.tabs.value[1].id

        viewModel.onEvent(TabsEvents.CloseOthers(1))
        assertEquals(1, viewModel.tabs.value.size)
        assertEquals(middleTabId, viewModel.tabs.value.first().id)
    }

    @Test
    fun `CloseOthers with invalid index does nothing`() {
        viewModel.onEvent(TabsEvents.OnAdd)
        viewModel.onEvent(TabsEvents.CloseOthers(99))
        assertEquals(2, viewModel.tabs.value.size)
    }

    @Test
    fun `CloseLeft removes tabs to the left of specified index`() {
        viewModel.onEvent(TabsEvents.OnAdd)
        viewModel.onEvent(TabsEvents.OnAdd)
        viewModel.onEvent(TabsEvents.OnAdd)
        assertEquals(4, viewModel.tabs.value.size)

        viewModel.onEvent(TabsEvents.CloseLeft(2))
        assertEquals(2, viewModel.tabs.value.size)
    }

    @Test
    fun `CloseRight removes tabs to the right of specified index`() {
        viewModel.onEvent(TabsEvents.OnAdd)
        viewModel.onEvent(TabsEvents.OnAdd)
        viewModel.onEvent(TabsEvents.OnAdd)
        assertEquals(4, viewModel.tabs.value.size)

        viewModel.onEvent(TabsEvents.CloseRight(1))
        assertEquals(2, viewModel.tabs.value.size)
    }

    @Test
    fun `openTab adds tab with given destination`() {
        val destination = TabsDestination.Search(searchQuery = "test", tabId = "search-tab")
        viewModel.openTab(destination)

        assertEquals(2, viewModel.tabs.value.size)
        assertTrue(viewModel.tabs.value.first().destination is TabsDestination.Search)
    }

    @Test
    fun `replaceCurrentTabDestination preserves tabId`() {
        val originalTabId = viewModel.tabs.value.first().destination.tabId

        viewModel.replaceCurrentTabDestination(TabsDestination.Search(searchQuery = "test", tabId = "ignored"))

        val newDestination = viewModel.tabs.value.first().destination
        assertEquals(originalTabId, newDestination.tabId)
        assertTrue(newDestination is TabsDestination.Search)
    }

    @Test
    fun `replaceCurrentTabWithNewTabId creates new tabId`() {
        val originalTabId = viewModel.tabs.value.first().destination.tabId

        viewModel.replaceCurrentTabWithNewTabId(TabsDestination.Search(searchQuery = "test", tabId = "ignored"))

        val newTabId = viewModel.tabs.value.first().destination.tabId
        assertNotEquals(originalTabId, newTabId)
    }

    @Test
    fun `restoreTabs replaces all tabs with given destinations`() {
        val destinations = listOf(
            TabsDestination.Home(tabId = "tab-1"),
            TabsDestination.Search(searchQuery = "query", tabId = "tab-2"),
            TabsDestination.BookContent(bookId = 123, tabId = "tab-3"),
        )

        viewModel.restoreTabs(destinations, selectedIndex = 1)

        assertEquals(3, viewModel.tabs.value.size)
        assertEquals(1, viewModel.selectedTabIndex.value)
    }

    @Test
    fun `restoreTabs with empty list does nothing`() {
        viewModel.restoreTabs(emptyList(), selectedIndex = 0)
        assertEquals(1, viewModel.tabs.value.size)
    }

    @Test
    fun `restoreTabs clamps selectedIndex to valid range`() {
        val destinations = listOf(
            TabsDestination.Home(tabId = "tab-1"),
            TabsDestination.Home(tabId = "tab-2"),
        )

        viewModel.restoreTabs(destinations, selectedIndex = 99)
        assertEquals(1, viewModel.selectedTabIndex.value)
    }

    @Test
    fun `tab title updates via titleUpdateManager`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        titleUpdateManager.updateTabTitle(
            tabId = "initial-tab",
            newTitle = "Updated Title",
            tabType = TabType.BOOK,
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Updated Title", viewModel.tabs.value.first().title)
        assertEquals(TabType.BOOK, viewModel.tabs.value.first().tabType)
    }
}
