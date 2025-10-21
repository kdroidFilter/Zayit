package io.github.kdroidfilter.seforimapp.features.onboarding.business

import io.github.kdroidfilter.platformtools.releasefetcher.github.GitHubReleaseFetcher
import io.github.kdroidfilter.seforimapp.network.HttpsConnectionFactory
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Encapsulates the download of the latest database .zst asset with reactive progress.
 */
class DownloadUseCase(
    private val gitHubReleaseFetcher: GitHubReleaseFetcher,
) {
    /**
     * Downloads the latest database .zst from releases into the app databases directory.
     * Reports progress and speed via [onProgress]. Returns the absolute path to the downloaded file.
     */
    suspend fun downloadLatestDatabase(
        onProgress: (readSoFar: Long, totalBytes: Long?, progress: Float, speedBytesPerSec: Long) -> Unit
    ): String = withContext(Dispatchers.Default) {
        val latestRelease = withContext(Dispatchers.IO) { gitHubReleaseFetcher.getLatestRelease() }
            ?: error("No release found")
        val asset = latestRelease.assets.firstOrNull { it.name.endsWith(".zst", ignoreCase = true) }
            ?: error("No .zst database asset found in latest release")

        val downloadUrl = asset.browser_download_url
        val dbDirV = FileKit.databasesDir
        val dbDir = File(dbDirV.path).apply { mkdirs() }
        val zstFile = File(dbDir, asset.name)

        var lastBytes = 0L
        var lastTimeNs = System.nanoTime()
        var lastSpeed = 0L

        downloadFile(downloadUrl, zstFile) { read, total ->
            val now = System.nanoTime()
            val dt = now - lastTimeNs
            if (dt >= 200_000_000L) { // update speed ~5 times per second
                val delta = read - lastBytes
                if (delta >= 0) {
                    lastSpeed = (delta.toDouble() * 1_000_000_000.0 / dt.toDouble()).toLong()
                    lastBytes = read
                    lastTimeNs = now
                }
            }
            val progress = if (total != null && total > 0) (read.toDouble() / total.toDouble()).toFloat() else 0f
            onProgress(read, total, progress.coerceIn(0f, 1f), lastSpeed)
        }
        // Ensure final callback shows completion
        onProgress(zstFile.length(), zstFile.length().takeIf { it > 0L }, 1f, 0L)

        return@withContext zstFile.absolutePath
    }

    private suspend fun downloadFile(
        url: String,
        dest: File,
        onBytes: (readSoFar: Long, totalBytes: Long?) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val connection = HttpsConnectionFactory.openConnection(url) {
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "SeforimApp/1.0 (+https://github.com/kdroidFilter/SeforimApp)")
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                connection.disconnect()
                error("Download failed: ${code}")
            }
            val totalLength = connection.contentLengthLong.takeIf { it > 0 }
                ?: connection.getHeaderFieldLong("Content-Length", -1L).takeIf { it > 0 }
            connection.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        total += read
                        onBytes(total, totalLength)
                    }
                    out.flush()
                }
            }
            connection.disconnect()
        }
        // Final callback to ensure UI shows completed values
        onBytes(dest.length(), dest.length().takeIf { it > 0L })
    }
}
