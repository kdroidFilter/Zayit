package io.github.kdroidfilter.seforimapp.features.onboarding.download

import io.github.kdroidfilter.seforimapp.features.onboarding.extract.BundleManifest
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.BundleParts
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
    private val http = HttpFileDownloader()

    /**
     * Downloads the latest split bundle (.tar.zst split into .part01 … .partNN, any number of parts),
     * verified against the release's bundle manifest when there is one. If only a single .tar.zst
     * exists (no parts), it is downloaded instead. Extraction is a separate step.
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

            // The manifest lists the parts with their checksums; older releases only have the parts
            // (or a single .tar.zst), found by name.
            val assetsByName = latestRelease.assets.associateBy { it.name }
            val manifestText =
                assetsByName[BundleParts.manifestNameFor(BundleParts.BUNDLE_NAME)]?.let { asset ->
                    http.downloadText(asset.browser_download_url)
                }
            val downloads =
                if (manifestText != null) {
                    BundleManifest.parse(manifestText).parts.map { part ->
                        val asset = assetsByName[part.file] ?: error("Release is missing ${part.file}")
                        PlannedDownload(asset.browser_download_url, part.file, part.size, part.sha256)
                    }
                } else {
                    val legacyParts = BundleParts.legacyPartNames(assetsByName.keys).map { assetsByName.getValue(it) }
                    if (legacyParts.isNotEmpty()) BundleParts.requireComplete(legacyParts.map { it.size.toLong() })
                    legacyParts
                        .ifEmpty { listOfNotNull(latestRelease.assets.firstOrNull { it.name.endsWith(".tar.zst", true) }) }
                        .map { PlannedDownload(it.browser_download_url, it.name, it.size.toLong(), sha256 = null) }
                }
            if (downloads.isEmpty()) error("No bundle assets found in latest release")

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

            val total = downloads.sumOf { it.size }
            var doneBytes = 0L
            val files =
                downloads.map { download ->
                    File(dbDir, download.name).also { file ->
                        http.download(download.url, file, download.size, download.sha256) { read -> report(doneBytes + read, total) }
                        doneBytes += download.size
                    }
                }
            report(total, total)
            // Extraction checks the parts against the manifest when it sits next to them
            manifestText?.let { File(dbDir, BundleParts.manifestNameFor(BundleParts.BUNDLE_NAME)).writeText(it) }
            // Return path to the first file; extraction discovers the other parts next to it
            files.first().absolutePath
        }

    /** One file of the bundle, with the size (and, from the manifest, the sha256) it must have. */
    private class PlannedDownload(
        val url: String,
        val name: String,
        val size: Long,
        val sha256: String?,
    )
}
