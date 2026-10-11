package io.github.kdroidfilter.seforimapp.features.onboarding.download

import io.github.kdroidfilter.seforimapp.network.HttpsConnectionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.security.MessageDigest

/** HTTP downloads of the database bundle files, verified and resumed after a dropped connection. */
internal class HttpFileDownloader(
    private val retryDelayMs: Long = 2_000L,
) {
    private fun openConnection(
        url: String,
        fromByte: Long = 0L,
    ): HttpURLConnection {
        val connection =
            HttpsConnectionFactory.openConnection(url) {
                setRequestProperty("Accept", "application/octet-stream")
                if (fromByte > 0) setRequestProperty("Range", "bytes=$fromByte-")
                setRequestProperty("User-Agent", "SeforimApp/1.0 (+https://github.com/kdroidFilter/SeforimApp)")
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
        connection.connect()
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            // Server-side errors are transient and retried; client errors are not
            if (code >= 500) throw IOException("Download failed: $code")
            error("Download failed: $code")
        }
        return connection
    }

    suspend fun downloadText(url: String): String =
        withContext(Dispatchers.IO) {
            val connection = openConnection(url)
            try {
                connection.inputStream.use { it.readBytes().decodeToString() }
            } finally {
                connection.disconnect()
            }
        }

    /**
     * Downloads [url] to [dest], failing if it does not have the expected size and sha256.
     * A dropped connection is retried, resuming from the last byte received (HTTP Range).
     */
    suspend fun download(
        url: String,
        dest: File,
        size: Long,
        sha256: String?,
        onBytes: (readSoFar: Long) -> Unit = {},
    ) {
        withContext(Dispatchers.IO) {
            val digest = sha256?.let { MessageDigest.getInstance("SHA-256") }
            var written = 0L
            var failures = 0
            dest.outputStream().use { out ->
                while (written < size) {
                    try {
                        val connection = openConnection(url, fromByte = written)
                        try {
                            connection.inputStream.use { input ->
                                // A server ignoring Range resends the file from the start
                                if (written > 0 && connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                                    input.skipNBytes(written)
                                }
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    out.write(buffer, 0, read)
                                    digest?.update(buffer, 0, read)
                                    written += read
                                    failures = 0
                                    onBytes(written)
                                }
                            }
                        } finally {
                            connection.disconnect()
                        }
                        if (written < size) throw IOException("Connection closed at $written of $size bytes")
                    } catch (e: IOException) {
                        if (++failures > MAX_RETRIES) throw e
                        delay(retryDelayMs * failures)
                    }
                }
            }
            check(written == size) { "Incomplete download of ${dest.name}: $written of $size bytes" }
            val actualSha = digest?.digest()?.joinToString("") { "%02x".format(it) }
            check(actualSha == null || actualSha.equals(sha256, ignoreCase = true)) {
                dest.delete()
                "Corrupted download of ${dest.name} (sha256 mismatch)"
            }
        }
    }

    private companion object {
        /** Consecutive failures without any byte received before giving up. */
        const val MAX_RETRIES = 5
    }
}
