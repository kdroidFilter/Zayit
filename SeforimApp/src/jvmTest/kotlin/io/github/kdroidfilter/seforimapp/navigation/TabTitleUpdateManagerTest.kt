package io.github.kdroidfilter.seforimapp.navigation

import io.github.kdroidfilter.seforim.tabs.TabTitleUpdate
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TabTitleUpdateManagerTest {
    @Test
    fun `updateTabTitle emits update successfully`() = runTest {
        val manager = TabTitleUpdateManager()
        var received: TabTitleUpdate? = null

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            received = manager.titleUpdates.first()
        }

        val success = manager.updateTabTitle(
            tabId = "test-tab",
            newTitle = "New Title",
            tabType = TabType.BOOK,
        )

        assertTrue(success)
        job.join()
        assertEquals("test-tab", received?.tabId)
        assertEquals("New Title", received?.newTitle)
        assertEquals(TabType.BOOK, received?.tabType)
    }

    @Test
    fun `updateTabTitle uses default tabType of SEARCH`() = runTest {
        val manager = TabTitleUpdateManager()
        var received: TabTitleUpdate? = null

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            received = manager.titleUpdates.first()
        }

        manager.updateTabTitle(tabId = "tab", newTitle = "Title")

        job.join()
        assertEquals(TabType.SEARCH, received?.tabType)
    }

    @Test
    fun `multiple updates are collected in order`() = runTest {
        val manager = TabTitleUpdateManager()
        val updates = mutableListOf<TabTitleUpdate>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.titleUpdates.collect { update ->
                updates.add(update)
                if (updates.size >= 3) return@collect
            }
        }

        manager.updateTabTitle("tab1", "Title1")
        manager.updateTabTitle("tab2", "Title2")
        manager.updateTabTitle("tab3", "Title3")

        job.join()

        assertEquals(3, updates.size)
        assertEquals("tab1", updates[0].tabId)
        assertEquals("tab2", updates[1].tabId)
        assertEquals("tab3", updates[2].tabId)
    }

    @Test
    fun `TabTitleUpdate data class has correct properties`() {
        val update = TabTitleUpdate(
            tabId = "my-tab",
            newTitle = "My Title",
            tabType = TabType.BOOK,
        )

        assertEquals("my-tab", update.tabId)
        assertEquals("My Title", update.newTitle)
        assertEquals(TabType.BOOK, update.tabType)
    }

    @Test
    fun `TabTitleUpdate default tabType is SEARCH`() {
        val update = TabTitleUpdate(tabId = "tab", newTitle = "Title")
        assertEquals(TabType.SEARCH, update.tabType)
    }

    @Test
    fun `TabTitleUpdate equals and hashCode work correctly`() {
        val update1 = TabTitleUpdate("tab", "Title", TabType.BOOK)
        val update2 = TabTitleUpdate("tab", "Title", TabType.BOOK)
        val update3 = TabTitleUpdate("tab", "Different", TabType.BOOK)

        assertEquals(update1, update2)
        assertEquals(update1.hashCode(), update2.hashCode())
        assertTrue(update1 != update3)
    }
}
