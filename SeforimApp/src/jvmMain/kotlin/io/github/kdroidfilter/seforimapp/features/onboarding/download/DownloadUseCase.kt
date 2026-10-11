package io.github.kdroidfilter.seforimapp.features.onboarding.download

import io.github.kdroidfilter.seforimapp.features.onboarding.extract.BundleParts
import io.github.kdroidfilter.seforimapp.network.HttpsConnectionFactory
import io.github.kdroidfilter.seforimapp.releasefetcher.github.GitHubReleaseFetcher
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
     * Downloads the latest split bundle (.tar.zst split into .part01 … .partNN, any number of parts).
     * If only a single .tar.zst exists (no parts), it is downloaded instead. Extraction is a separate step.
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
                    .mapNotNull { asset -> BundleParts.partNumber(asset.name)?.let { it to asset } }
                    .sortedBy { it.first }
                    .also { parts -> if (parts.isNotEmpty()) BundleParts.requireContiguous(parts.map { it.first }) }
                    .map { it.second }
            val singleAsset = allAssets.firstOrNull { it.name.endsWith(".tar.zst", true) }

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

            if (partAssets.isNotEmpty()) {
                // Use asset sizes to initialize total from the start
                val knownTotal =
                    partAssets
                        .sumOf { runCatching { (it.size as? Number)?.toLong() }.getOrNull() ?: 0L }
                        .takeIf { it > 0L }
                val files = partAssets.map { File(dbDir, it.name) }
                val totals = arrayOfNulls<Long>(files.size)

                var doneBytes = 0L
                partAssets.forEachIndexed { index, asset ->
                    downloadFile(asset.browser_download_url, files[index]) { r, t ->
                        if (t != null) totals[index] = t
                        val dynamic = totals.sumOf { it ?: 0L }
                        report(doneBytes + r, knownTotal ?: dynamic.takeIf { it > 0L })
                    }
                    doneBytes += files[index].length()
                }
                // Finalize download progress
                val finalTotal = knownTotal ?: doneBytes
                report(finalTotal, finalTotal)
                // Return path to the first part; extraction step discovers the others next to it
                return@withContext files.first().absolutePath
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
