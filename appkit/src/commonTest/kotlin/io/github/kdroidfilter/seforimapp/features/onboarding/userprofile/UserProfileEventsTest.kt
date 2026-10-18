package io.github.kdroidfilter.seforimapp.features.onboarding.userprofile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UserProfileEventsTest {
    @Test
    fun `FirstNameChanged stores value`() {
        val event = UserProfileEvents.FirstNameChanged("John")
        assertEquals("John", event.value)
    }

    @Test
    fun `FirstNameChanged implements UserProfileEvents`() {
        val event: UserProfileEvents = UserProfileEvents.FirstNameChanged("Test")
        assertIs<UserProfileEvents.FirstNameChanged>(event)
    }

    @Test
    fun `FirstNameChanged equality works`() {
        val event1 = UserProfileEvents.FirstNameChanged("Alice")
        val event2 = UserProfileEvents.FirstNameChanged("Alice")
        assertEquals(event1, event2)
    }

    @Test
    fun `LastNameChanged stores value`() {
        val event = UserProfileEvents.LastNameChanged("Doe")
        assertEquals("Doe", event.value)
    }

    @Test
    fun `LastNameChanged implements UserProfileEvents`() {
        val event: UserProfileEvents = UserProfileEvents.LastNameChanged("Test")
        assertIs<UserProfileEvents.LastNameChanged>(event)
    }

    @Test
    fun `LastNameChanged equality works`() {
        val event1 = UserProfileEvents.LastNameChanged("Smith")
        val event2 = UserProfileEvents.LastNameChanged("Smith")
        assertEquals(event1, event2)
    }

    @Test
    fun `SelectCommunity stores index`() {
        val event = UserProfileEvents.SelectCommunity(2)
        assertEquals(2, event.index)
    }

    @Test
    fun `SelectCommunity implements UserProfileEvents`() {
        val event: UserProfileEvents = UserProfileEvents.SelectCommunity(0)
        assertIs<UserProfileEvents.SelectCommunity>(event)
    }

    @Test
    fun `SelectCommunity equality works`() {
        val event1 = UserProfileEvents.SelectCommunity(1)
        val event2 = UserProfileEvents.SelectCommunity(1)
        assertEquals(event1, event2)
    }

    @Test
    fun `all event types are exhaustive in when expression`() {
        val events: List<UserProfileEvents> =
            listOf(
                UserProfileEvents.FirstNameChanged("First"),
                UserProfileEvents.LastNameChanged("Last"),
                UserProfileEvents.SelectCommunity(0),
            )

        events.forEach { event ->
            when (event) {
                is UserProfileEvents.FirstNameChanged -> assertEquals("First", event.value)
                is UserProfileEvents.LastNameChanged -> assertEquals("Last", event.value)
                is UserProfileEvents.SelectCommunity -> assertEquals(0, event.index)
            }
        }
    }
}
