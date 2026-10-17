package io.github.kdroidfilter.seforimapp.features.onboarding.userprofile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserProfileUseCaseTest {
    @Test
    fun communities_available() {
        val useCase = UserProfileUseCase()
        val communities = useCase.getCommunities()
        assertEquals(3, communities.size)
        assertTrue(communities.containsAll(listOf(Community.SEPHARADE, Community.ASHKENAZE, Community.SEFARD)))
    }
}
