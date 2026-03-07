package io.github.kdroidfilter.seforimapp.integration

import io.github.kdroidfilter.seforim.desktop.VirtualDesktop
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.session.BookContentPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.DesktopTabsSnapshot
import io.github.kdroidfilter.seforimapp.framework.session.DesktopsState
import io.github.kdroidfilter.seforimapp.framework.session.SerializableTabTitle
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for [DesktopManager].
 * Tests the full virtual desktop lifecycle: creation, switching, renaming, deletion,
 * reordering, snapshotting, and persistence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopManagerIntegrationTest {
    private lateinit var tabsViewModel: TabsViewModel
    private lateinit var persistedStore: TabPersistedStateStore
    private lateinit var desktopManager: DesktopManager

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        persistedStore = TabPersistedStateStore()
        tabsViewModel =
            TabsViewModel(
                titleUpdateManager = TabTitleUpdateManager(),
                startDestination =
                    TabsDestination.BookContent(
                        bookId = -1,
                        tabId = UUID.randomUUID().toString(),
                    ),
            )
        desktopManager = DesktopManager(tabsViewModel, persistedStore, defaultDesktopName = "Desktop 1")
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== Initialization Tests ====================

    @Test
    fun `initializes with one desktop`() =
        runTest {
            val desktops = desktopManager.desktops.value
            assertEquals(1, desktops.size)
        }

    @Test
    fun `initial desktop is active`() =
        runTest {
            val desktops = desktopManager.desktops.value
            assertEquals(desktops.first().id, desktopManager.activeDesktopId.value)
        }

    @Test
    fun `isSwitching is initially false`() =
        runTest {
            assertFalse(desktopManager.isSwitching.value)
        }

    // ==================== Desktop Creation Tests ====================

    @Test
    fun `createDesktop adds a new desktop`() =
        runTest {
            desktopManager.clearSwitching()
            desktopManager.createDesktop("Test")

            assertEquals(2, desktopManager.desktops.value.size)
        }

    @Test
    fun `createDesktop switches to new desktop`() =
        runTest {
            desktopManager.clearSwitching()
            val newId = desktopManager.createDesktop("Test")

            assertEquals(newId, desktopManager.activeDesktopId.value)
        }

    @Test
    fun `createDesktop uses provided name`() =
        runTest {
            desktopManager.clearSwitching()
            desktopManager.createDesktop("Desktop 2")

            val desktops = desktopManager.desktops.value
            assertEquals(2, desktops.size)
            assertEquals("Desktop 2", desktops.last().name)
        }

    @Test
    fun `createDesktop sets isSwitching to true`() =
        runTest {
            desktopManager.createDesktop("Test")

            assertTrue(desktopManager.isSwitching.value)
        }

    @Test
    fun `new desktop starts with empty home tab`() =
        runTest {
            desktopManager.clearSwitching()
            desktopManager.createDesktop("Test")
            desktopManager.clearSwitching()

            val tabs = tabsViewModel.state.value.tabs
            assertEquals(1, tabs.size)
            assertTrue(tabs.first().destination is TabsDestination.BookContent)
            assertEquals(-1L, (tabs.first().destination as TabsDestination.BookContent).bookId)
        }

    // ==================== Desktop Switching Tests ====================

    @Test
    fun `switchTo changes active desktop`() =
        runTest {
            desktopManager.clearSwitching()
            val newId = desktopManager.createDesktop("Desktop 2")
            desktopManager.clearSwitching()

            val originalId =
                desktopManager.desktops.value
                    .first()
                    .id
            desktopManager.switchTo(originalId)

            assertEquals(originalId, desktopManager.activeDesktopId.value)
        }

    @Test
    fun `switchTo same desktop does nothing`() =
        runTest {
            val activeId = desktopManager.activeDesktopId.value
            desktopManager.switchTo(activeId)

            assertFalse(desktopManager.isSwitching.value)
        }

    @Test
    fun `switchTo unknown desktop does nothing`() =
        runTest {
            val activeId = desktopManager.activeDesktopId.value
            desktopManager.switchTo("non-existent-id")

            assertEquals(activeId, desktopManager.activeDesktopId.value)
            assertFalse(desktopManager.isSwitching.value)
        }

    @Test
    fun `switchTo preserves tabs of previous desktop`() =
        runTest {
            // Open tabs on desktop 1
            val bookDest = TabsDestination.BookContent(bookId = 42, tabId = UUID.randomUUID().toString())
            tabsViewModel.openTab(bookDest)
            val desktop1TabCount = tabsViewModel.state.value.tabs.size

            // Create desktop 2
            desktopManager.clearSwitching()
            val desktop2Id = desktopManager.createDesktop("Desktop 2")
            desktopManager.clearSwitching()

            // Verify we're on desktop 2 with fresh tabs
            assertEquals(1, tabsViewModel.state.value.tabs.size)

            // Switch back to desktop 1
            val desktop1Id =
                desktopManager.desktops.value
                    .first()
                    .id
            desktopManager.switchTo(desktop1Id)
            desktopManager.clearSwitching()

            // Desktop 1 tabs should be restored
            assertEquals(desktop1TabCount, tabsViewModel.state.value.tabs.size)
        }

    @Test
    fun `switchTo sets isSwitching to true`() =
        runTest {
            desktopManager.clearSwitching()
            val newId = desktopManager.createDesktop("Desktop 2")
            desktopManager.clearSwitching()

            val originalId =
                desktopManager.desktops.value
                    .first()
                    .id
            desktopManager.switchTo(originalId)

            assertTrue(desktopManager.isSwitching.value)
        }

    @Test
    fun `switchTo is blocked while already switching`() =
        runTest {
            desktopManager.clearSwitching()
            val desktop2Id = desktopManager.createDesktop("Desktop 2")
            // isSwitching is still true from createDesktop

            val desktop1Id =
                desktopManager.desktops.value
                    .first()
                    .id
            desktopManager.switchTo(desktop1Id)

            // Should still be on desktop 2 because switch was blocked
            assertEquals(desktop2Id, desktopManager.activeDesktopId.value)
        }

    @Test
    fun `clearSwitching resets the flag`() =
        runTest {
            desktopManager.createDesktop("Test")
            assertTrue(desktopManager.isSwitching.value)

            desktopManager.clearSwitching()
            assertFalse(desktopManager.isSwitching.value)
        }

    // ==================== Desktop Rename Tests ====================

    @Test
    fun `renameDesktop changes desktop name`() =
        runTest {
            val desktopId =
                desktopManager.desktops.value
                    .first()
                    .id
            desktopManager.renameDesktop(desktopId, "Renamed")

            assertEquals(
                "Renamed",
                desktopManager.desktops.value
                    .first()
                    .name,
            )
        }

    @Test
    fun `renameDesktop with unknown id does nothing`() =
        runTest {
            val originalName =
                desktopManager.desktops.value
                    .first()
                    .name
            desktopManager.renameDesktop("unknown-id", "Renamed")

            assertEquals(
                originalName,
                desktopManager.desktops.value
                    .first()
                    .name,
            )
        }

    @Test
    fun `renameDesktop does not affect other desktops`() =
        runTest {
            desktopManager.clearSwitching()
            desktopManager.createDesktop("Desktop 2")
            desktopManager.clearSwitching()

            val firstId =
                desktopManager.desktops.value
                    .first()
                    .id
            val secondId =
                desktopManager.desktops.value
                    .last()
                    .id
            desktopManager.renameDesktop(firstId, "Renamed")

            assertEquals(
                "Renamed",
                desktopManager.desktops.value
                    .first()
                    .name,
            )
            assertEquals(
                "Desktop 2",
                desktopManager.desktops.value
                    .last()
                    .name,
            )
        }

    // ==================== Desktop Deletion Tests ====================

    @Test
    fun `deleteDesktop removes desktop`() =
        runTest {
            desktopManager.clearSwitching()
            desktopManager.createDesktop("Desktop 2")
            desktopManager.clearSwitching()
            assertEquals(2, desktopManager.desktops.value.size)

            val secondId =
                desktopManager.desktops.value
                    .last()
                    .id
            desktopManager.deleteDesktop(secondId)

            assertEquals(1, desktopManager.desktops.value.size)
        }

    @Test
    fun `deleteDesktop cannot remove last desktop`() =
        runTest {
            val desktopId =
                desktopManager.desktops.value
                    .first()
                    .id
            desktopManager.deleteDesktop(desktopId)

            assertEquals(1, desktopManager.desktops.value.size)
        }

    @Test
    fun `deleteDesktop switches to neighbor when deleting active`() =
        runTest {
            desktopManager.clearSwitching()
            desktopManager.createDesktop("Desktop 2")
            desktopManager.clearSwitching()

            val activeId = desktopManager.activeDesktopId.value
            val otherId =
                desktopManager.desktops.value
                    .first { it.id != activeId }
                    .id

            desktopManager.deleteDesktop(activeId)
            desktopManager.clearSwitching()

            assertEquals(otherId, desktopManager.activeDesktopId.value)
            assertEquals(1, desktopManager.desktops.value.size)
        }

    @Test
    fun `deleteDesktop with unknown id does nothing`() =
        runTest {
            val count = desktopManager.desktops.value.size
            desktopManager.deleteDesktop("unknown-id")

            assertEquals(count, desktopManager.desktops.value.size)
        }

    // ==================== Desktop Reorder Tests ====================

    @Test
    fun `moveDesktop reorders desktops`() =
        runTest {
            desktopManager.clearSwitching()
            desktopManager.createDesktop("Desktop 2")
            desktopManager.clearSwitching()
            desktopManager.createDesktop("Desktop 3")
            desktopManager.clearSwitching()

            val firstId = desktopManager.desktops.value[0].id
            val lastId = desktopManager.desktops.value[2].id

            desktopManager.moveDesktop(0, 2)

            assertEquals(lastId, desktopManager.desktops.value[1].id)
            assertEquals(firstId, desktopManager.desktops.value[2].id)
        }

    @Test
    fun `moveDesktop with same indices does nothing`() =
        runTest {
            desktopManager.clearSwitching()
            desktopManager.createDesktop("Desktop 2")
            desktopManager.clearSwitching()

            val before = desktopManager.desktops.value.map { it.id }
            desktopManager.moveDesktop(0, 0)

            assertEquals(before, desktopManager.desktops.value.map { it.id })
        }

    @Test
    fun `moveDesktop with invalid indices does nothing`() =
        runTest {
            val before = desktopManager.desktops.value.map { it.id }
            desktopManager.moveDesktop(-1, 5)

            assertEquals(before, desktopManager.desktops.value.map { it.id })
        }

    // ==================== Navigation Tests ====================

    @Test
    fun `switchToNext cycles forward`() =
        runTest {
            desktopManager.clearSwitching()
            desktopManager.createDesktop("Desktop 2")
            desktopManager.clearSwitching()
            desktopManager.createDesktop("Desktop 3")
            desktopManager.clearSwitching()

            val thirdId = desktopManager.activeDesktopId.value
            val firstId = desktopManager.desktops.value[0].id

            desktopManager.switchToNext()
            desktopManager.clearSwitching()

            // Should wrap to first desktop
            assertEquals(firstId, desktopManager.activeDesktopId.value)
        }

    @Test
    fun `switchToPrevious cycles backward`() =
        runTest {
            desktopManager.clearSwitching()
            desktopManager.createDesktop("Desktop 2")
            desktopManager.clearSwitching()

            // We're on desktop 2, switch back
            val firstId = desktopManager.desktops.value[0].id
            desktopManager.switchToPrevious()
            desktopManager.clearSwitching()

            assertEquals(firstId, desktopManager.activeDesktopId.value)
        }

    @Test
    fun `switchToNext with single desktop does nothing`() =
        runTest {
            val activeId = desktopManager.activeDesktopId.value
            desktopManager.switchToNext()

            assertEquals(activeId, desktopManager.activeDesktopId.value)
        }

    @Test
    fun `switchToPrevious with single desktop does nothing`() =
        runTest {
            val activeId = desktopManager.activeDesktopId.value
            desktopManager.switchToPrevious()

            assertEquals(activeId, desktopManager.activeDesktopId.value)
        }

    @Test
    fun `switchToPrevious wraps around from first to last`() =
        runTest {
            desktopManager.clearSwitching()
            desktopManager.createDesktop("Desktop 2")
            desktopManager.clearSwitching()

            // Switch to first desktop
            val firstId = desktopManager.desktops.value[0].id
            val secondId = desktopManager.desktops.value[1].id
            desktopManager.switchTo(firstId)
            desktopManager.clearSwitching()

            desktopManager.switchToPrevious()
            desktopManager.clearSwitching()

            assertEquals(secondId, desktopManager.activeDesktopId.value)
        }

    // ==================== Snapshot & Persistence Tests ====================

    @Test
    fun `snapshotCurrentDesktop captures tab state`() =
        runTest {
            val bookDest = TabsDestination.BookContent(bookId = 42, tabId = UUID.randomUUID().toString())
            tabsViewModel.openTab(bookDest)

            val snapshot = desktopManager.snapshotCurrentDesktop()

            assertEquals(tabsViewModel.state.value.tabs.size, snapshot.destinations.size)
            assertEquals(tabsViewModel.state.value.selectedTabIndex, snapshot.selectedIndex)
        }

    @Test
    fun `snapshotCurrentDesktop strips lineId from BookContent`() =
        runTest {
            val dest = TabsDestination.BookContent(bookId = 42, tabId = "t1", lineId = 100)
            tabsViewModel.openTab(dest)

            val snapshot = desktopManager.snapshotCurrentDesktop()
            val snappedDest = snapshot.destinations.first() as TabsDestination.BookContent

            assertEquals(null, snappedDest.lineId)
            assertEquals(42L, snappedDest.bookId)
        }

    @Test
    fun `snapshotCurrentDesktop captures persisted state`() =
        runTest {
            val tabId =
                tabsViewModel.state.value.tabs
                    .first()
                    .destination.tabId
            persistedStore.set(tabId, TabPersistedState(bookContent = BookContentPersistedState(selectedBookId = 99)))

            val snapshot = desktopManager.snapshotCurrentDesktop()

            assertEquals(99L, snapshot.tabStates[tabId]?.bookContent?.selectedBookId)
        }

    @Test
    fun `buildDesktopsState includes all desktops`() =
        runTest {
            desktopManager.clearSwitching()
            desktopManager.createDesktop("Desktop 2")
            desktopManager.clearSwitching()

            val state = desktopManager.buildDesktopsState()

            assertEquals(2, state.desktops.size)
            assertEquals(2, state.snapshots.size)
            assertEquals(desktopManager.activeDesktopId.value, state.activeDesktopId)
        }

    @Test
    fun `restoreFromDesktopsState restores all desktops`() =
        runTest {
            val desktop1 = VirtualDesktop(id = "d1", name = "Desktop 1")
            val desktop2 = VirtualDesktop(id = "d2", name = "Desktop 2")
            val tab1 = TabsDestination.BookContent(bookId = 10, tabId = "tab-1")
            val tab2 = TabsDestination.BookContent(bookId = 20, tabId = "tab-2")

            val desktopsState =
                DesktopsState(
                    desktops = listOf(desktop1, desktop2),
                    activeDesktopId = "d1",
                    snapshots =
                        mapOf(
                            "d1" to
                                DesktopTabsSnapshot(
                                    destinations = listOf(tab1),
                                    selectedIndex = 0,
                                    titles = mapOf("tab-1" to SerializableTabTitle("Book A", TabType.BOOK)),
                                ),
                            "d2" to
                                DesktopTabsSnapshot(
                                    destinations = listOf(tab2),
                                    selectedIndex = 0,
                                    titles = mapOf("tab-2" to SerializableTabTitle("Book B", TabType.BOOK)),
                                ),
                        ),
                )

            desktopManager.restoreFromDesktopsState(desktopsState)

            assertEquals(2, desktopManager.desktops.value.size)
            assertEquals("d1", desktopManager.activeDesktopId.value)
            // Active desktop tabs restored
            assertEquals(1, tabsViewModel.state.value.tabs.size)
            assertEquals(
                10L,
                (
                    tabsViewModel.state.value.tabs
                        .first()
                        .destination as TabsDestination.BookContent
                ).bookId,
            )
        }

    @Test
    fun `restoreFromDesktopsState with empty desktops does nothing`() =
        runTest {
            val originalDesktops = desktopManager.desktops.value
            desktopManager.restoreFromDesktopsState(DesktopsState())

            assertEquals(originalDesktops, desktopManager.desktops.value)
        }

    @Test
    fun `switching desktops preserves persisted tab state`() =
        runTest {
            // Set up state on desktop 1
            val tabId1 =
                tabsViewModel.state.value.tabs
                    .first()
                    .destination.tabId
            persistedStore.set(tabId1, TabPersistedState(bookContent = BookContentPersistedState(selectedBookId = 77)))

            // Create desktop 2
            desktopManager.clearSwitching()
            desktopManager.createDesktop("Desktop 2")
            desktopManager.clearSwitching()

            // Switch back to desktop 1
            val desktop1Id =
                desktopManager.desktops.value
                    .first()
                    .id
            desktopManager.switchTo(desktop1Id)
            desktopManager.clearSwitching()

            // Persisted state should be restored
            assertEquals(77L, persistedStore.get(tabId1)?.bookContent?.selectedBookId)
        }

    // ==================== Regression Tests ====================

    @Test
    fun `tab operations on one desktop do not affect other desktops`() =
        runTest {
            // Set up 2 tabs on desktop 1
            tabsViewModel.openTab(TabsDestination.BookContent(bookId = 1, tabId = UUID.randomUUID().toString()))
            val desktop1TabCount = tabsViewModel.state.value.tabs.size

            // Create desktop 2 and add tabs
            desktopManager.clearSwitching()
            val desktop2Id = desktopManager.createDesktop("Desktop 2")
            desktopManager.clearSwitching()
            tabsViewModel.openTab(TabsDestination.BookContent(bookId = 2, tabId = UUID.randomUUID().toString()))

            // Switch back to desktop 1
            val desktop1Id =
                desktopManager.desktops.value
                    .first()
                    .id
            desktopManager.switchTo(desktop1Id)
            desktopManager.clearSwitching()

            // Desktop 1 should still have original tab count
            assertEquals(desktop1TabCount, tabsViewModel.state.value.tabs.size)
        }

    @Test
    fun `selected tab index is preserved across switches`() =
        runTest {
            // Add tabs and select second one
            tabsViewModel.openTab(TabsDestination.BookContent(bookId = 1, tabId = UUID.randomUUID().toString()))
            tabsViewModel.openTab(TabsDestination.BookContent(bookId = 2, tabId = UUID.randomUUID().toString()))
            val desiredIndex = 1
            tabsViewModel.onEvent(
                io.github.kdroidfilter.seforim.tabs.TabsEvents
                    .OnSelect(desiredIndex),
            )

            // Switch away and back
            desktopManager.clearSwitching()
            desktopManager.createDesktop("Desktop 2")
            desktopManager.clearSwitching()
            val desktop1Id =
                desktopManager.desktops.value
                    .first()
                    .id
            desktopManager.switchTo(desktop1Id)
            desktopManager.clearSwitching()

            assertEquals(desiredIndex, tabsViewModel.state.value.selectedTabIndex)
        }

    @Test
    fun `restoreTabs uses skipAnimation on desktop switch`() =
        runTest {
            desktopManager.clearSwitching()
            desktopManager.createDesktop("Desktop 2")
            desktopManager.clearSwitching()

            val desktop1Id =
                desktopManager.desktops.value
                    .first()
                    .id
            desktopManager.switchTo(desktop1Id)

            assertTrue(tabsViewModel.skipNextAnimation.value)
        }

    @Test
    fun `desktop ids are unique`() =
        runTest {
            desktopManager.clearSwitching()
            desktopManager.createDesktop("A")
            desktopManager.clearSwitching()
            desktopManager.createDesktop("B")
            desktopManager.clearSwitching()
            desktopManager.createDesktop("C")
            desktopManager.clearSwitching()

            val ids = desktopManager.desktops.value.map { it.id }
            assertEquals(ids.size, ids.toSet().size)
        }

    @Test
    fun `rapid create-delete cycle does not corrupt state`() =
        runTest {
            desktopManager.clearSwitching()
            val id1 = desktopManager.createDesktop("A")
            desktopManager.clearSwitching()
            val id2 = desktopManager.createDesktop("B")
            desktopManager.clearSwitching()

            desktopManager.deleteDesktop(id1)
            desktopManager.clearSwitching()
            desktopManager.deleteDesktop(id2)
            desktopManager.clearSwitching()

            // Should still have at least the initial desktop
            assertTrue(desktopManager.desktops.value.isNotEmpty())
            val activeId = desktopManager.activeDesktopId.value
            assertTrue(desktopManager.desktops.value.any { it.id == activeId })
        }

    @Test
    fun `buildDesktopsState then restoreFromDesktopsState is idempotent`() =
        runTest {
            // Set up state
            tabsViewModel.openTab(TabsDestination.BookContent(bookId = 42, tabId = UUID.randomUUID().toString()))
            desktopManager.clearSwitching()
            desktopManager.createDesktop("Desktop 2")
            desktopManager.clearSwitching()

            // Snapshot
            val state = desktopManager.buildDesktopsState()

            // Restore into a fresh manager
            val freshTabsVm =
                TabsViewModel(
                    titleUpdateManager = TabTitleUpdateManager(),
                    startDestination = TabsDestination.BookContent(bookId = -1, tabId = UUID.randomUUID().toString()),
                )
            val freshStore = TabPersistedStateStore()
            val freshManager = DesktopManager(freshTabsVm, freshStore, defaultDesktopName = "Desktop 1")

            freshManager.restoreFromDesktopsState(state)

            assertEquals(state.desktops.size, freshManager.desktops.value.size)
            assertEquals(state.activeDesktopId, freshManager.activeDesktopId.value)
            assertEquals(
                state.desktops.map { it.name },
                freshManager.desktops.value.map { it.name },
            )
        }
}
