package io.github.kdroidfilter.seforimapp.framework.update

import io.github.kdroidfilter.nucleus.updater.UpdaterConfig
import io.github.kdroidfilter.platformtools.releasefetcher.github.GitHubReleaseFetcher
import io.github.kdroidfilter.seforimapp.network.KtorConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppUpdateChecker {
    const val DOWNLOAD_URL = "https://kdroidfilter.github.io/Zayit/download"

    private val releaseFetcher =
        GitHubReleaseFetcher(
            owner = "kdroidFilter",
            repo = "Zayit",
            httpClient = KtorConfig.createHttpClient(),
        )

    /**
     * Result of an update check.
     */
    sealed class UpdateCheckResult {
        data class UpdateAvailable(
            val latestVersion: String,
        ) : UpdateCheckResult()

        data object UpToDate : UpdateCheckResult()

        data object Error : UpdateCheckResult()
    }

    /**
     * Checks if an update is available.
     *
     * @return [UpdateCheckResult] indicating whether an update is available,
     *         the app is up-to-date, or an error occurred.
     */
    suspend fun checkForUpdate(): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            try {
                val release =
                    releaseFetcher.getLatestRelease()
                        ?: return@withContext UpdateCheckResult.Error

                val latestVersion = normalizeVersion(release.tag_name)
                val currentVersion = UpdaterConfig().currentVersion.trim()

                if (latestVersion != currentVersion) {
                    UpdateCheckResult.UpdateAvailable(latestVersion)
                } else {
                    UpdateCheckResult.UpToDate
                }
            } catch (_: Exception) {
                UpdateCheckResult.Error
            }
        }

    private fun normalizeVersion(version: String): String = version.removePrefix("v").trim()
}
