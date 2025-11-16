package io.github.kdroidfilter.buildsrc

import org.gradle.api.Project
import java.net.HttpURLConnection
import java.net.URL

object Versioning {
    @Suppress("UNUSED_PARAMETER")
    fun resolveVersion(project: Project): String {
        val fromEnv = resolveFromGitHubRef()
        if (!fromEnv.isNullOrBlank()) {
            return fromEnv
        }

        val fromGitHub = resolveFromGitHubApi()
        if (!fromGitHub.isNullOrBlank()) {
            return fromGitHub
        }

        return "1.0.0"
    }

    private fun resolveFromGitHubRef(): String? {
        val ref = System.getenv("GITHUB_REF") ?: return null
        if (!ref.startsWith("refs/tags/")) {
            return null
        }
        val tag = ref.removePrefix("refs/tags/")
        return tag.removePrefix("v")
    }

    private fun resolveFromGitHubApi(): String? {
        val repository = System.getenv("GITHUB_REPOSITORY") ?: return null

        return try {
            val url = URL("https://api.github.com/repos/$repository/tags?per_page=1")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "SeforimApp-build")
                val token = System.getenv("GITHUB_TOKEN") ?: System.getenv("GH_TOKEN")
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }

            connection.use { conn ->
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    return null
                }

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                extractFirstTagName(body)?.removePrefix("v")
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractFirstTagName(json: String): String? {
        // Very small JSON parser: look for "name":"...".
        val nameKeyIndex = json.indexOf("\"name\"")
        if (nameKeyIndex == -1) return null

        val colonIndex = json.indexOf(':', nameKeyIndex)
        if (colonIndex == -1) return null

        val firstQuote = json.indexOf('"', colonIndex + 1)
        if (firstQuote == -1) return null

        val secondQuote = json.indexOf('"', firstQuote + 1)
        if (secondQuote == -1 || secondQuote <= firstQuote) return null

        return json.substring(firstQuote + 1, secondQuote)
    }

    // Simple extension to ensure HttpURLConnection is closed
    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T {
        return try {
            block(this)
        } finally {
            try {
                inputStream?.close()
            } catch (_: Exception) {
            }
            try {
                errorStream?.close()
            } catch (_: Exception) {
            }
            disconnect()
        }
    }
}
