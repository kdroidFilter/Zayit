package io.github.kdroidfilter.seforimapp.framework.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppUpdateCheckerTest {
    @Test
    fun `DOWNLOAD_URL is valid`() {
        assertEquals("https://kdroidfilter.github.io/Zayit/download", AppUpdateChecker.DOWNLOAD_URL)
        assertTrue(AppUpdateChecker.DOWNLOAD_URL.startsWith("https://"))
    }

    @Test
    fun `UpdateAvailable stores version`() {
        val result = AppUpdateChecker.UpdateCheckResult.UpdateAvailable("1.0.0")
        assertEquals("1.0.0", result.latestVersion)
        assertIs<AppUpdateChecker.UpdateCheckResult>(result)
    }

    @Test
    fun `UpToDate is singleton`() {
        val result1 = AppUpdateChecker.UpdateCheckResult.UpToDate
        val result2 = AppUpdateChecker.UpdateCheckResult.UpToDate
        assertEquals(result1, result2)
        assertIs<AppUpdateChecker.UpdateCheckResult>(result1)
    }

    @Test
    fun `Error is singleton`() {
        val result1 = AppUpdateChecker.UpdateCheckResult.Error
        val result2 = AppUpdateChecker.UpdateCheckResult.Error
        assertEquals(result1, result2)
        assertIs<AppUpdateChecker.UpdateCheckResult>(result1)
    }

    @Test
    fun `UpdateAvailable equals works correctly`() {
        val result1 = AppUpdateChecker.UpdateCheckResult.UpdateAvailable("1.0.0")
        val result2 = AppUpdateChecker.UpdateCheckResult.UpdateAvailable("1.0.0")
        val result3 = AppUpdateChecker.UpdateCheckResult.UpdateAvailable("2.0.0")

        assertEquals(result1, result2)
        assertNotEquals(result1, result3)
    }

    @Test
    fun `different result types are not equal`() {
        val available = AppUpdateChecker.UpdateCheckResult.UpdateAvailable("1.0.0")
        val upToDate = AppUpdateChecker.UpdateCheckResult.UpToDate
        val error = AppUpdateChecker.UpdateCheckResult.Error

        assertNotEquals<AppUpdateChecker.UpdateCheckResult>(available, upToDate)
        assertNotEquals<AppUpdateChecker.UpdateCheckResult>(available, error)
        assertNotEquals<AppUpdateChecker.UpdateCheckResult>(upToDate, error)
    }
}
