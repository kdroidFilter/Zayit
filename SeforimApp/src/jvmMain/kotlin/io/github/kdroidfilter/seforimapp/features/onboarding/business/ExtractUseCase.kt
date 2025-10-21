package io.github.kdroidfilter.seforimapp.features.onboarding.business

import com.github.luben.zstd.ZstdInputStream
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
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
 * Encapsulates extraction of a .zst database file into the app databases directory.
 */
class ExtractUseCase {

    /**
     * Extracts the given .zst file to a .db path in the app databases directory and persists the path.
     * Reports progress via [onProgress]. Returns the absolute path to the created DB file.
     */
    suspend fun extractToDatabase(
        sourcePath: String,
        onProgress: (Float) -> Unit
    ): String = withContext(Dispatchers.Default) {
        val dbDirV = FileKit.databasesDir
        val dbDir = File(dbDirV.path).apply { mkdirs() }
        val sourceZst = File(sourcePath)
        require(sourceZst.exists()) { "Selected file not found" }
        val baseName = sourceZst.name.removeSuffix(".zst")
        val targetName = if (baseName.endsWith(".db")) baseName else "$baseName.db"
        val targetDb = File(dbDir, targetName)

        onProgress(0f)
        withContext(Dispatchers.IO) {
            extractZst(sourceZst, targetDb) { p -> onProgress(p.coerceIn(0f, 1f)) }
        }
        onProgress(1f)
        AppSettings.setDatabasePath(targetDb.absolutePath)
        return@withContext targetDb.absolutePath
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
