package io.github.kdroidfilter.seforimapp.features.settings.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class SettingsDestinationTest {
    @Test
    fun `General is a valid SettingsDestination`() {
        val destination: SettingsDestination = SettingsDestination.General
        assertIs<SettingsDestination>(destination)
    }

    @Test
    fun `Profile is a valid SettingsDestination`() {
        val destination: SettingsDestination = SettingsDestination.Profile
        assertIs<SettingsDestination>(destination)
    }

    @Test
    fun `Fonts is a valid SettingsDestination`() {
        val destination: SettingsDestination = SettingsDestination.Fonts
        assertIs<SettingsDestination>(destination)
    }

    @Test
    fun `About is a valid SettingsDestination`() {
        val destination: SettingsDestination = SettingsDestination.About
        assertIs<SettingsDestination>(destination)
    }

    @Test
    fun `Conditions is a valid SettingsDestination`() {
        val destination: SettingsDestination = SettingsDestination.Conditions
        assertIs<SettingsDestination>(destination)
    }

    @Test
    fun `data objects are singletons`() {
        assertEquals(SettingsDestination.General, SettingsDestination.General)
        assertEquals(SettingsDestination.Profile, SettingsDestination.Profile)
        assertEquals(SettingsDestination.Fonts, SettingsDestination.Fonts)
        assertEquals(SettingsDestination.About, SettingsDestination.About)
        assertEquals(SettingsDestination.Conditions, SettingsDestination.Conditions)
    }

    @Test
    fun `different destinations are not equal`() {
        assertNotEquals(
            SettingsDestination.General as SettingsDestination,
            SettingsDestination.About as SettingsDestination,
        )
        assertNotEquals(
            SettingsDestination.Profile as SettingsDestination,
            SettingsDestination.Fonts as SettingsDestination,
        )
    }
}
