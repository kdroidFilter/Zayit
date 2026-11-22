package io.github.kdroidfilter.seforim.tabs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A window's tabs held in memory: what the desktop's workspace does, minus the windows. */
private class FakeTabsHost(
    vararg ids: String,
) : TabsHost {
    val tabs = ids.map { tab(TabsDestination.BookContent(bookId = 1, tabId = it)) }.toMutableList()
    var selected: String? = ids.firstOrNull()

    override fun ids(): List<String> = tabs.map { it.destination.tabId }

    override fun selectedId(): String? = selected

    override fun item(tabId: String): TabItem? = tabs.firstOrNull { it.destination.tabId == tabId }

    override fun isDeclared(tabId: String): Boolean = item(tabId) != null

    override fun isPinned(tabId: String): Boolean = item(tabId)?.pinned == true

    override fun setPinned(
        tabId: String,
        pinned: Boolean,
    ) {
        val index = ids().indexOf(tabId)
        tabs[index] = tabs[index].copy(pinned = pinned)
    }

    override fun select(tabId: String) {
        selected = tabId
    }

    override fun close(tabId: String) {
        tabs.removeAll { it.destination.tabId == tabId }
        if (selected == tabId) selected = ids().firstOrNull()
    }

    override fun reorder(
        tabId: String,
        toIndex: Int,
    ) {
        val item = item(tabId) ?: return
        tabs.remove(item)
        tabs.add(toIndex, item)
    }

    override fun add(
        destination: TabsDestination,
        index: Int,
        replacing: String?,
        pinned: Boolean,
    ) {
        replacing?.let(::close)
        tabs.add(index.coerceAtMost(tabs.size), tab(destination).copy(pinned = pinned))
        selected = destination.tabId
    }

    override fun updateDestination(
        tabId: String,
        destination: TabsDestination,
    ) {
        val index = ids().indexOf(tabId)
        tabs[index] = tabs[index].copy(destination = destination)
    }

    private fun tab(destination: TabsDestination) = TabItem(id = 0, title = destination.tabId, destination = destination)
}

@OptIn(ExperimentalCoroutinesApi::class)
class TabsViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `close others keeps the pinned tabs`() {
        val host = FakeTabsHost("a", "b", "c", "d")
        host.setPinned("a", true)
        val viewModel = TabsViewModel(host)

        viewModel.onEvent(TabsEvents.CloseOthers(index = 2))

        assertEquals(listOf("a", "c"), host.ids())
        viewModel.dispose()
    }

    @Test
    fun `a tab opened from another lands at its place and is selected`() {
        val host = FakeTabsHost("a", "b", "c")
        host.select("b")
        val viewModel = TabsViewModel(host)

        viewModel.openTab(TabsDestination.BookContent(bookId = 2, tabId = "new"))

        assertEquals(listOf("a", "new", "b", "c"), host.ids())
        assertEquals("new", host.selectedId())
        viewModel.dispose()
    }

    @Test
    fun `replacing the current tab gives it a new id and keeps its pin`() {
        val host = FakeTabsHost("a", "b")
        host.setPinned("a", true)
        val viewModel = TabsViewModel(host)

        viewModel.replaceCurrentTabWithNewTabId(TabsDestination.BookContent(bookId = 3, tabId = "a"))

        val newId = host.ids().first()
        assertNotEquals("a", newId)
        assertTrue(host.isPinned(newId))
        assertEquals(listOf(newId, "b"), host.ids())
        viewModel.dispose()
    }

    @Test
    fun `a pinned tab closes on the second close only`() {
        val host = FakeTabsHost("a", "b")
        host.setPinned("a", true)
        val viewModel = TabsViewModel(host)

        viewModel.closeSelectedTab()
        assertEquals(listOf("a", "b"), host.ids())
        assertTrue(viewModel.closePinnedHint.value)

        viewModel.closeSelectedTab()
        assertEquals(listOf("b"), host.ids())
        viewModel.dispose()
    }
}
