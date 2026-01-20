package io.github.kdroidfilter.seforimapp.framework.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseVersionManagerTest {
    @Test
    fun `getMinimumRequiredVersion returns valid version string`() {
        val version = DatabaseVersionManager.getMinimumRequiredVersion()
        assertTrue(version.isNotEmpty())
        assertEquals(14, version.length)
    }

    @Test
    fun `getMinimumRequiredVersion returns numeric string`() {
        val version = DatabaseVersionManager.getMinimumRequiredVersion()
        assertTrue(version.all { it.isDigit() })
    }

    @Test
    fun `formatVersionForDisplay formats valid version`() {
        val version = "20260111222851"
        val formatted = DatabaseVersionManager.formatVersionForDisplay(version)
        assertEquals("11/01/2026 22:28:51", formatted)
    }

    @Test
    fun `formatVersionForDisplay handles different dates`() {
        val version = "20250615143022"
        val formatted = DatabaseVersionManager.formatVersionForDisplay(version)
        assertEquals("15/06/2025 14:30:22", formatted)
    }

    @Test
    fun `formatVersionForDisplay returns original for invalid length`() {
        val shortVersion = "2025061514"
        val formatted = DatabaseVersionManager.formatVersionForDisplay(shortVersion)
        assertEquals(shortVersion, formatted)
    }

    @Test
    fun `formatVersionForDisplay returns original for too long version`() {
        val longVersion = "202506151430221234"
        val formatted = DatabaseVersionManager.formatVersionForDisplay(longVersion)
        assertEquals(longVersion, formatted)
    }

    @Test
    fun `formatVersionForDisplay handles midnight time`() {
        val version = "20250101000000"
        val formatted = DatabaseVersionManager.formatVersionForDisplay(version)
        assertEquals("01/01/2025 00:00:00", formatted)
    }

    @Test
    fun `formatVersionForDisplay handles end of day time`() {
        val version = "20251231235959"
        val formatted = DatabaseVersionManager.formatVersionForDisplay(version)
        assertEquals("31/12/2025 23:59:59", formatted)
    }

    @Test
    fun `minimum required version is in correct format`() {
        val version = DatabaseVersionManager.getMinimumRequiredVersion()

        // Check year is reasonable (2000-2099)
        val year = version.substring(0, 4).toInt()
        assertTrue(year in 2000..2099)

        // Check month is valid (01-12)
        val month = version.substring(4, 6).toInt()
        assertTrue(month in 1..12)

        // Check day is valid (01-31)
        val day = version.substring(6, 8).toInt()
        assertTrue(day in 1..31)

        // Check hour is valid (00-23)
        val hour = version.substring(8, 10).toInt()
        assertTrue(hour in 0..23)

        // Check minute is valid (00-59)
        val minute = version.substring(10, 12).toInt()
        assertTrue(minute in 0..59)

        // Check second is valid (00-59)
        val second = version.substring(12, 14).toInt()
        assertTrue(second in 0..59)
    }
}
