package io.github.kdroidfilter.seforimapp.features.settings

import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsWindowViewModelTest {
    private fun desktopManager() =
        DesktopManager(
            tabPersistedStateStore = TabPersistedStateStore(),
            titleUpdateManager = TabTitleUpdateManager(),
            searchHomeViewModelFactory = { mockk(relaxed = true) },
            defaultDesktopName = "D1",
        )

    @Test
    fun `initial state has isVisible false`() =
        runTest {
            val viewModel = SettingsWindowViewModel(desktopManager())
            assertFalse(viewModel.state.value.isVisible)
        }

    @Test
    fun `OnOpen event sets isVisible to true`() =
        runTest {
            val viewModel = SettingsWindowViewModel(desktopManager())

            viewModel.onEvent(SettingsWindowEvents.OnOpen)

            assertTrue(viewModel.state.value.isVisible)
        }

    @Test
    fun `OnClose event sets isVisible to false`() =
        runTest {
            val viewModel = SettingsWindowViewModel(desktopManager())

            // First open
            viewModel.onEvent(SettingsWindowEvents.OnOpen)
            assertTrue(viewModel.state.value.isVisible)

            // Then close
            viewModel.onEvent(SettingsWindowEvents.OnClose)
            assertFalse(viewModel.state.value.isVisible)
        }

    @Test
    fun `multiple OnOpen events keep isVisible true`() =
        runTest {
            val viewModel = SettingsWindowViewModel(desktopManager())

            viewModel.onEvent(SettingsWindowEvents.OnOpen)
            viewModel.onEvent(SettingsWindowEvents.OnOpen)

            assertTrue(viewModel.state.value.isVisible)
        }

    @Test
    fun `multiple OnClose events keep isVisible false`() =
        runTest {
            val viewModel = SettingsWindowViewModel(desktopManager())

            viewModel.onEvent(SettingsWindowEvents.OnClose)
            viewModel.onEvent(SettingsWindowEvents.OnClose)

            assertFalse(viewModel.state.value.isVisible)
        }

    @Test
    fun `state flow emits updates`() =
        runTest {
            val viewModel = SettingsWindowViewModel(desktopManager())

            // Collect initial state
            val initialState = viewModel.state.value
            assertFalse(initialState.isVisible)

            // Trigger event
            viewModel.onEvent(SettingsWindowEvents.OnOpen)

            // Verify state changed
            val newState = viewModel.state.value
            assertTrue(newState.isVisible)
        }

    @Test
    fun `state is a StateFlow`() {
        val viewModel = SettingsWindowViewModel(desktopManager())
        assertEquals(SettingsWindowState::class, viewModel.state.value::class)
    }
}
