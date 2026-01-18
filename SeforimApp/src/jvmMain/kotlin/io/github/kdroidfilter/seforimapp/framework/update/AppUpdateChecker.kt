package io.github.kdroidfilter.seforimapp.framework.update

import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.getAppVersion
import io.github.kdroidfilter.platformtools.getOperatingSystem
import io.github.kdroidfilter.platformtools.releasefetcher.github.GitHubReleaseFetcher
import io.github.kdroidfilter.seforimapp.network.KtorConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Checks for app updates by comparing the local version with the latest GitHub release.
 *
 * Handles macOS version quirk where local version is 1.x.x but GitHub has 0.x.x
 * (due to macOS packaging requiring MAJOR > 0).
 */
object AppUpdateChecker {

    const val DOWNLOAD_URL = "https://kdroidfilter.github.io/Zayit/download"

    private val releaseFetcher = GitHubReleaseFetcher(
        owner = "kdroidFilter",
        repo = "Zayit",
        httpClient = KtorConfig.createHttpClient()
    )

    /**
     * Result of an update check.
     */
    sealed class UpdateCheckResult {
        data class UpdateAvailable(val latestVersion: String) : UpdateCheckResult()
        data object UpToDate : UpdateCheckResult()
        data object Error : UpdateCheckResult()
    }

    /**
     * Checks if an update is available.
     *
     * @return [UpdateCheckResult] indicating whether an update is available,
     *         the app is up-to-date, or an error occurred.
     */
    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val release = releaseFetcher.getLatestRelease()
                ?: return@withContext UpdateCheckResult.Error

            val latestVersion = normalizeVersion(release.tag_name)
            val currentVersion = normalizeLocalVersion(getAppVersion())

            if (latestVersion != currentVersion) {
                UpdateCheckResult.UpdateAvailable(latestVersion)
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (_: Exception) {
            UpdateCheckResult.Error
        }
    }

    /**
     * Normalizes a GitHub version tag by removing the "v" prefix.
     * Example: "v0.3.17" -> "0.3.17"
     */
    private fun normalizeVersion(version: String): String {
        return version.removePrefix("v").trim()
    }

    /**
     * Normalizes the local app version, handling the macOS quirk.
     * On macOS, version 1.x.y is converted back to 0.x.y for comparison.
     * Example: "1.3.17" on macOS -> "0.3.17"
     */
    private fun normalizeLocalVersion(version: String): String {
        val normalized = version.trim()

        // On macOS, convert 1.x.y back to 0.x.y (reverse of macSafeVersion in build.gradle.kts)
        if (getOperatingSystem() == OperatingSystem.MACOS) {
            val parts = normalized.split(".")
            if (parts.isNotEmpty() && parts[0] == "1") {
                return "0.${parts.drop(1).joinToString(".")}"
            }
        }

        return normalized
    }
}
