package io.github.kdroidfilter.seforimapp.features.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsWindowViewModelTest {
    @Test
    fun `initial state has isVisible false`() =
        runTest {
            val viewModel = SettingsWindowViewModel()
            assertFalse(viewModel.state.value.isVisible)
        }

    @Test
    fun `OnOpen event sets isVisible to true`() =
        runTest {
            val viewModel = SettingsWindowViewModel()

            viewModel.onEvent(SettingsWindowEvents.OnOpen)

            assertTrue(viewModel.state.value.isVisible)
        }

    @Test
    fun `OnClose event sets isVisible to false`() =
        runTest {
            val viewModel = SettingsWindowViewModel()

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
            val viewModel = SettingsWindowViewModel()

            viewModel.onEvent(SettingsWindowEvents.OnOpen)
            viewModel.onEvent(SettingsWindowEvents.OnOpen)

            assertTrue(viewModel.state.value.isVisible)
        }

    @Test
    fun `multiple OnClose events keep isVisible false`() =
        runTest {
            val viewModel = SettingsWindowViewModel()

            viewModel.onEvent(SettingsWindowEvents.OnClose)
            viewModel.onEvent(SettingsWindowEvents.OnClose)

            assertFalse(viewModel.state.value.isVisible)
        }

    @Test
    fun `state flow emits updates`() =
        runTest {
            val viewModel = SettingsWindowViewModel()

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
        val viewModel = SettingsWindowViewModel()
        assertEquals(SettingsWindowState::class, viewModel.state.value::class)
    }
}
