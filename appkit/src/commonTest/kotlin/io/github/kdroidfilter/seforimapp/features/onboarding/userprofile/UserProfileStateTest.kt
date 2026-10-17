package io.github.kdroidfilter.seforimapp.features.onboarding.userprofile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserProfileStateTest {
    @Test
    fun `default state has empty values`() {
        val state = UserProfileState()

        assertEquals("", state.firstName)
        assertEquals("", state.lastName)
        assertTrue(state.communities.isEmpty())
        assertEquals(-1, state.selectedCommunityIndex)
    }

    @Test
    fun `state can be created with custom values`() {
        val communities = listOf(Community.SEPHARADE, Community.ASHKENAZE)
        val state =
            UserProfileState(
                firstName = "John",
                lastName = "Doe",
                communities = communities,
                selectedCommunityIndex = 0,
            )

        assertEquals("John", state.firstName)
        assertEquals("Doe", state.lastName)
        assertEquals(2, state.communities.size)
        assertEquals(0, state.selectedCommunityIndex)
    }

    @Test
    fun `copy preserves unchanged values`() {
        val original =
            UserProfileState(
                firstName = "Original",
                lastName = "Name",
                selectedCommunityIndex = 1,
            )
        val modified = original.copy(firstName = "Modified")

        assertEquals("Modified", modified.firstName)
        assertEquals("Name", modified.lastName)
        assertEquals(1, modified.selectedCommunityIndex)
    }

    @Test
    fun `equals works correctly`() {
        val state1 = UserProfileState(firstName = "A", lastName = "B")
        val state2 = UserProfileState(firstName = "A", lastName = "B")
        val state3 = UserProfileState(firstName = "C", lastName = "B")

        assertEquals(state1, state2)
        assertTrue(state1 != state3)
    }

    @Test
    fun `state with all communities`() {
        val allCommunities = Community.entries.toList()
        val state = UserProfileState(communities = allCommunities)

        assertEquals(3, state.communities.size)
        assertTrue(state.communities.contains(Community.SEPHARADE))
        assertTrue(state.communities.contains(Community.ASHKENAZE))
        assertTrue(state.communities.contains(Community.SEFARD))
    }
}
