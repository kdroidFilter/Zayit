package io.github.kdroidfilter.seforimapp.releasefetcher.github

import io.github.kdroidfilter.seforimapp.network.KtorConfig
import io.github.kdroidfilter.seforimapp.releasefetcher.github.model.Release
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json

/**
 * Fetches GitHub releases over a Ktor client backed by Nucleus native SSL.
 *
 * The default [httpClient] uses the OS certificate stores via [KtorConfig], so HTTPS
 * calls trust the same roots as the host platform.
 */
class GitHubReleaseFetcher(
    private val owner: String,
    private val repo: String,
    private val httpClient: HttpClient = KtorConfig.createHttpClient(),
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    /**
     * Fetches the latest release from the GitHub API, or `null` on any failure.
     */
    suspend fun getLatestRelease(): Release? =
        try {
            val response: HttpResponse =
                httpClient.get("https://api.github.com/repos/$owner/$repo/releases/latest")

            if (response.status == HttpStatusCode.OK) {
                json.decodeFromString<Release>(response.body())
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
}
