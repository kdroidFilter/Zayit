package io.github.kdroidfilter.seforimapp.features.onboarding.download

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
     * Downloads the latest split bundle (.tar.zst split into .part01/.part02) and extracts it directly,
     * without creating an intermediate .tar file. If only a single .tar.zst exists (no parts), it is
     * downloaded and extracted as well.
     *
     * Progress: during network transfers, reports bytes and speed; during extraction, progress continues
     * from the last value to 100% with speed set to 0.
     */
    suspend fun downloadLatestBundle(
        onProgress: (readSoFar: Long, totalBytes: Long?, progress: Float, speedBytesPerSec: Long) -> Unit,
    ): String =
        withContext(Dispatchers.Default) {
            val latestRelease =
                withContext(Dispatchers.IO) { gitHubReleaseFetcher.getLatestRelease() }
                    ?: error("No release found")

            // Prefer split parts if present, fallback to single .tar.zst
            val allAssets = latestRelease.assets
            val partAssets =
                allAssets
                    .filter { it.name.endsWith(".tar.zst.part01", true) || it.name.endsWith(".tar.zst.part02", true) }
                    .sortedBy { it.name }
            val singleAsset = allAssets.firstOrNull { it.name.endsWith(".tar.zst", true) && !it.name.contains(".part") }

            val dbDir = File(FileKit.databasesDir.path).apply { mkdirs() }

            // Keep running stats for a smoother, more stable UX
            var lastBytes = 0L
            var lastTimeNs = System.nanoTime()
            var emaSpeed = 0.0 // Exponential moving average (bytes/sec)
            var lastEmittedNs = 0L

            fun report(
                read: Long,
                total: Long?,
            ) {
                val now = System.nanoTime()
                val dtNs = now - lastTimeNs
                if (dtNs > 0) {
                    val deltaBytes = (read - lastBytes).coerceAtLeast(0)
                    val instSpeed = (deltaBytes.toDouble() * 1_000_000_000.0) / dtNs.toDouble() // bytes/sec

                    // Time-constant based EMA like modern browsers (~3s smoothing)
                    val dtSec = dtNs / 1_000_000_000.0
                    val tau = 3.0 // seconds
                    val alpha = 1.0 - kotlin.math.exp(-dtSec / tau)
                    emaSpeed = if (emaSpeed == 0.0) instSpeed else emaSpeed + alpha * (instSpeed - emaSpeed)

                    lastBytes = read
                    lastTimeNs = now
                }

                val progress = if (total != null && total > 0) (read.toDouble() / total.toDouble()).toFloat() else 0f

                // Throttle UI updates to reduce flicker (~4 Hz) while staying responsive
                val shouldEmit = (now - lastEmittedNs) >= 250_000_000L || progress >= 1f
                if (shouldEmit) {
                    lastEmittedNs = now
                    onProgress(read, total, progress.coerceIn(0f, 1f), emaSpeed.toLong().coerceAtLeast(0L))
                }
            }

            if (partAssets.size >= 2) {
                // Two-part flow
                val part01 = partAssets.first { it.name.endsWith(".part01", true) }
                val part02 = partAssets.first { it.name.endsWith(".part02", true) }
                // Use asset sizes to initialize total from the start
                val size1 = (runCatching { (part01.size as? Number)?.toLong() }.getOrNull() ?: 0L)
                val size2 = (runCatching { (part02.size as? Number)?.toLong() }.getOrNull() ?: 0L)
                val knownTotal = (size1 + size2).takeIf { it > 0L }

                val file01 = File(dbDir, part01.name)
                val file02 = File(dbDir, part02.name)

                var readSoFar = 0L
                var total1: Long? = null
                var total2: Long? = null
                downloadFile(part01.browser_download_url, file01) { r, t ->
                    if (t != null) total1 = t
                    readSoFar = r
                    val dynamic = (total1 ?: 0L) + (total2 ?: 0L)
                    report(readSoFar, knownTotal ?: dynamic.takeIf { it > 0L })
                }
                downloadFile(part02.browser_download_url, file02) { r, t ->
                    if (t != null) total2 = t
                    readSoFar = (file01.length()) + r
                    val dynamic = (total1 ?: 0L) + (total2 ?: 0L)
                    report(readSoFar, knownTotal ?: dynamic.takeIf { it > 0L })
                }
                // Finalize download progress
                val finalTotal = knownTotal ?: ((total1 ?: file01.length()) + (total2 ?: file02.length()))
                report(finalTotal, finalTotal)
                // Return path to the first part; extraction step will handle parts
                return@withContext file01.absolutePath
            } else if (singleAsset != null) {
                // Backward-compatible: single .tar.zst
                val tmp = File(dbDir, singleAsset.name)
                val knownTotal = runCatching { (singleAsset.size as? Number)?.toLong() }.getOrNull()?.takeIf { it > 0L }
                var totalLength: Long? = null
                downloadFile(singleAsset.browser_download_url, tmp) { r, t ->
                    if (t != null) totalLength = t
                    report(r, knownTotal ?: totalLength)
                }
                val total = knownTotal ?: totalLength ?: tmp.length()
                onProgress(total, total, 1f, 0L)
                return@withContext tmp.absolutePath
            } else {
                error("No bundle assets found in latest release")
            }
        }

    private suspend fun downloadFile(
        url: String,
        dest: File,
        onBytes: (readSoFar: Long, totalBytes: Long?) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            val connection =
                HttpsConnectionFactory.openConnection(url) {
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
                error("Download failed: $code")
            }
            val totalLength =
                connection.contentLengthLong.takeIf { it > 0 }
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
