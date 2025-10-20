package io.github.kdroidfilter.seforimapp.features.onboarding

import com.github.luben.zstd.ZstdInputStream
import io.github.kdroidfilter.platformtools.releasefetcher.github.GitHubReleaseFetcher
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.network.HttpsConnectionFactory
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream

/**
 * Use case for onboarding database setup/import.
 * Encapsulates data operations (network, filesystem, persistence).
 */
class OnBoardingUseCase(
    private val settings: AppSettings,
    private val gitHubReleaseFetcher: GitHubReleaseFetcher,
) {

    fun getDatabasePath(): String? = settings.getDatabasePath()

    /**
     * Downloads latest DB .zst from GitHub, extracts it, cleans up, and persists DB path.
     * Reports progress via callbacks.
     */
    suspend fun setupDatabase(
        onDownloadProgress: (readSoFar: Long, totalBytes: Long?, progress: Float, speedBytesPerSec: Long) -> Unit,
        onExtractProgress: (progress: Float) -> Unit,
    ) = withContext(Dispatchers.Default) {
        // 1) Fetch latest release and find .zst asset
        val latestRelease = withContext(Dispatchers.IO) { gitHubReleaseFetcher.getLatestRelease() }
            ?: error("No release found")
        val asset = latestRelease.assets.firstOrNull { it.name.endsWith(".zst", ignoreCase = true) }
            ?: error("No .zst database asset found in latest release")

        val downloadUrl = asset.browser_download_url
        val dbDirV = FileKit.databasesDir
        val dbDir = File(dbDirV.path).apply { mkdirs() }
        val zstFile = File(dbDir, asset.name)
        val dbFile = File(dbDir, asset.name.removeSuffix(".zst").let { name -> if (name.endsWith(".db")) name else "$name.db" })

        // 2) Download with progress and speed calculation
        var lastBytes = 0L
        var lastTimeNs = System.nanoTime()
        downloadFile(downloadUrl, zstFile) { read, total ->
            val now = System.nanoTime()
            val dt = now - lastTimeNs
            var speed = 0L
            if (dt >= 200_000_000L) { // update speed ~5 times per second
                val delta = read - lastBytes
                if (delta >= 0) {
                    speed = (delta.toDouble() * 1_000_000_000.0 / dt.toDouble()).toLong()
                    lastBytes = read
                    lastTimeNs = now
                }
            }
            val progress = if (total != null && total > 0) (read.toDouble() / total.toDouble()).toFloat() else 0f
            onDownloadProgress(read, total, progress.coerceIn(0f, 1f), speed)
        }
        // Ensure final callback shows completion
        onDownloadProgress(zstFile.length(), zstFile.length().takeIf { it > 0L }, 1f, 0L)

        // 3) Extract with progress using zstd-jni
        onExtractProgress(0f)
        withContext(Dispatchers.IO) {
            extractZst(zstFile, dbFile) { p ->
                onExtractProgress(p.coerceIn(0f, 1f))
            }
        }
        onExtractProgress(1f)

        // 4) Clean up and persist path
        runCatching { zstFile.delete() }
        settings.setDatabasePath(dbFile.absolutePath)
    }

    /**
     * Imports a user-provided .zst database file, extracts to databases dir, and persists DB path.
     */
    suspend fun importFromZst(
        sourcePath: String,
        onExtractProgress: (progress: Float) -> Unit,
    ) = withContext(Dispatchers.Default) {
        val dbDirV = FileKit.databasesDir
        val dbDir = File(dbDirV.path).apply { mkdirs() }
        val sourceZst = File(sourcePath)
        require(sourceZst.exists()) { "Selected file not found" }
        val baseName = sourceZst.name.removeSuffix(".zst")
        val targetName = if (baseName.endsWith(".db")) baseName else "$baseName.db"
        val targetDb = File(dbDir, targetName)

        onExtractProgress(0f)
        withContext(Dispatchers.IO) {
            extractZst(sourceZst, targetDb) { p ->
                onExtractProgress(p.coerceIn(0f, 1f))
            }
        }
        onExtractProgress(1f)
        settings.setDatabasePath(targetDb.absolutePath)
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

    private fun extractZst(sourceZst: File, targetDb: File, onProgress: (Float) -> Unit) {
        class CountingInputStream(`in`: FileInputStream) : FilterInputStream(`in`) {
            var count: Long = 0
                private set
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val r = super.read(b, off, len)
                if (r > 0) count += r
                return r
            }
            override fun read(): Int {
                val r = super.read()
                if (r >= 0) count += 1
                return r
            }
        }

        val totalCompressed = sourceZst.length().coerceAtLeast(1L)
        FileInputStream(sourceZst).use { fis ->
            val cis = CountingInputStream(fis)
            ZstdInputStream(cis).use { zin ->
                FileOutputStream(targetDb).use { out ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val read = zin.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        onProgress(cis.count.toFloat() / totalCompressed.toFloat())
                    }
                    out.fd.sync()
                }
            }
        }
        onProgress(1f)
    }
}
