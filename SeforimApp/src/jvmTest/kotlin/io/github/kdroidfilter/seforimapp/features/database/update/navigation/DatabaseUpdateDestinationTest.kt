package io.github.kdroidfilter.seforimapp.features.database.update.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DatabaseUpdateDestinationTest {
    @Test
    fun `VersionCheckScreen is singleton`() {
        val screen1 = DatabaseUpdateDestination.VersionCheckScreen
        val screen2 = DatabaseUpdateDestination.VersionCheckScreen
        assertEquals(screen1, screen2)
        assertIs<DatabaseUpdateDestination>(screen1)
    }

    @Test
    fun `UpdateOptionsScreen is singleton`() {
        val screen1 = DatabaseUpdateDestination.UpdateOptionsScreen
        val screen2 = DatabaseUpdateDestination.UpdateOptionsScreen
        assertEquals(screen1, screen2)
        assertIs<DatabaseUpdateDestination>(screen1)
    }

    @Test
    fun `OnlineUpdateScreen is singleton`() {
        val screen1 = DatabaseUpdateDestination.OnlineUpdateScreen
        val screen2 = DatabaseUpdateDestination.OnlineUpdateScreen
        assertEquals(screen1, screen2)
        assertIs<DatabaseUpdateDestination>(screen1)
    }

    @Test
    fun `OfflineUpdateScreen is singleton`() {
        val screen1 = DatabaseUpdateDestination.OfflineUpdateScreen
        val screen2 = DatabaseUpdateDestination.OfflineUpdateScreen
        assertEquals(screen1, screen2)
        assertIs<DatabaseUpdateDestination>(screen1)
    }

    @Test
    fun `CompletionScreen is singleton`() {
        val screen1 = DatabaseUpdateDestination.CompletionScreen
        val screen2 = DatabaseUpdateDestination.CompletionScreen
        assertEquals(screen1, screen2)
        assertIs<DatabaseUpdateDestination>(screen1)
    }

    @Test
    fun `all destinations are different`() {
        val destinations =
            listOf(
                DatabaseUpdateDestination.VersionCheckScreen,
                DatabaseUpdateDestination.UpdateOptionsScreen,
                DatabaseUpdateDestination.OnlineUpdateScreen,
                DatabaseUpdateDestination.OfflineUpdateScreen,
                DatabaseUpdateDestination.CompletionScreen,
            )

        for (i in destinations.indices) {
            for (j in destinations.indices) {
                if (i != j) {
                    assertTrue(destinations[i] != destinations[j])
                }
            }
        }
    }
}
