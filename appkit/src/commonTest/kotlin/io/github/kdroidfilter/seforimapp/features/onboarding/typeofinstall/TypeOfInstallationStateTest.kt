package io.github.kdroidfilter.seforimapp.features.onboarding.typeofinstall

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypeOfInstallationStateTest {
    @Test
    fun `default state allows proceeding`() {
        val state = TypeOfInstallationState()
        assertTrue(state.canProceed)
    }

    @Test
    fun `state can be created with canProceed false`() {
        val state = TypeOfInstallationState(canProceed = false)
        assertFalse(state.canProceed)
    }

    @Test
    fun `copy works correctly`() {
        val original = TypeOfInstallationState(canProceed = true)
        val modified = original.copy(canProceed = false)

        assertTrue(original.canProceed)
        assertFalse(modified.canProceed)
    }

    @Test
    fun `equals works correctly`() {
        val state1 = TypeOfInstallationState(true)
        val state2 = TypeOfInstallationState(true)
        val state3 = TypeOfInstallationState(false)

        assertEquals(state1, state2)
        assertTrue(state1 != state3)
    }
}
