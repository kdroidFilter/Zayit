package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import com.github.luben.zstd.ZstdInputStream
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.SequenceInputStream

class ExtractUseCase(
    private val appSettings: AppSettings,
) {
    suspend fun extractToDatabase(
        sourcePath: String,
        onProgress: (Float) -> Unit,
    ): String =
        withContext(Dispatchers.Default) {
            val dbDirV = FileKit.databasesDir
            val dbDir = File(dbDirV.path).apply { mkdirs() }
            val source = File(sourcePath)
            require(source.exists()) { "Selected file not found" }

            onProgress(0f)
            val dbFile: File =
                withContext(Dispatchers.IO) {
                    val lower = source.name.lowercase()
                    when {
                        BundleParts.isPart(source.name) -> {
                            val parts = BundleParts.resolve(source)
                            val result =
                                extractTarZstFromPartsStreaming(parts, dbDir) { mapped ->
                                    onProgress(mapped)
                                }
                            // Only cleanup if parts live in our databases directory (downloaded by the app)
                            val canDelete =
                                runCatching {
                                    val base = dbDir.canonicalFile
                                    parts.all { it.parentFile?.canonicalFile == base }
                                }.getOrDefault(false)
                            if (canDelete) BundleParts.bundleFiles(source).forEach { runCatching { it.delete() } }
                            result
                        }
                        lower.endsWith(".tar.zst") -> {
                            val result = extractTarZstFile(source, dbDir) { p -> onProgress(p.coerceIn(0f, 1f)) }
                            runCatching { maybeCleanupSources(source, dbDir) }
                            result
                        }
                        lower.endsWith(".zst") -> {
                            val baseName = source.name.removeSuffix(".zst")
                            val targetName = if (baseName.endsWith(".db")) baseName else "$baseName.db"
                            val targetDb = File(dbDir, targetName)
                            val result = extractDbZst(source, targetDb) { p -> onProgress(p.coerceIn(0f, 1f)) }
                            runCatching { maybeCleanupSources(source, dbDir) }
                            result
                        }
                        else -> error("Unsupported file type: ${source.name}")
                    }
                }
            onProgress(1f)
            appSettings.setDatabasePath(dbFile.absolutePath)
            runCatching { maybeCleanupSources(source, dbDir) }
            return@withContext dbFile.absolutePath
        }

    private fun extractDbZst(
        sourceZst: File,
        targetDb: File,
        onProgress: (Float) -> Unit,
    ): File {
        class CountingInputStream(
            `in`: FileInputStream,
        ) : FilterInputStream(`in`) {
            var count: Long = 0
                private set

            override fun read(
                b: ByteArray,
                off: Int,
                len: Int,
            ): Int {
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
        return targetDb
    }

    private fun extractTarZstFile(
        zstFile: File,
        destDir: File,
        onProgress: (Float) -> Unit,
    ): File {
        class CountingInputStream(
            `in`: InputStream,
        ) : FilterInputStream(`in`) {
            var count: Long = 0
                private set

            override fun read(
                b: ByteArray,
                off: Int,
                len: Int,
            ): Int {
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

        val totalCompressed = zstFile.length().coerceAtLeast(1L)
        var extractedDb: File? = null
        FileInputStream(zstFile).use { fis ->
            CountingInputStream(BufferedInputStream(fis, 1 shl 20)).use { cis ->
                ZstdInputStream(cis).use { zIn ->
                    TarArchiveInputStream(zIn).use { tar ->
                        while (true) {
                            val entry = tar.nextEntry ?: break
                            val name = entry.name
                            val outFile = File(destDir, name)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { out ->
                                    val buffer = ByteArray(1024 * 1024)
                                    var remaining = entry.size
                                    while (remaining > 0) {
                                        val toRead = if (remaining >= buffer.size) buffer.size else remaining.toInt()
                                        val read = tar.read(buffer, 0, toRead)
                                        if (read <= 0) break
                                        out.write(buffer, 0, read)
                                        remaining -= read
                                        onProgress(cis.count.toFloat() / totalCompressed.toFloat())
                                    }
                                    out.fd.sync()
                                }
                                if (name.endsWith(".db", ignoreCase = true)) {
                                    extractedDb = outFile
                                }
                            }
                            onProgress(cis.count.toFloat() / totalCompressed.toFloat())
                        }
                    }
                }
            }
        }
        return extractedDb ?: error("No .db file found in archive")
    }

    private fun extractTarZstFromPartsStreaming(
        parts: List<File>,
        destDir: File,
        onUiProgress: (Float) -> Unit,
    ): File {
        require(parts.isNotEmpty())

        // Counting wrapper to report compressed bytes consumed
        class CountingInputStream(
            `in`: InputStream,
        ) : FilterInputStream(`in`) {
            var count: Long = 0
                private set

            override fun read(
                b: ByteArray,
                off: Int,
                len: Int,
            ): Int {
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

        val totalCompressed = parts.sumOf { it.length().coerceAtLeast(0L) }.coerceAtLeast(1L)
        val quarter = (totalCompressed / 4L).coerceAtLeast(1L)

        fun mapProgress(consumed: Long): Float {
            val c = consumed.coerceIn(0, totalCompressed)
            return if (c <= quarter) {
                (c.toFloat() / quarter.toFloat()) * 0.25f
            } else {
                val rest = (totalCompressed - quarter).coerceAtLeast(1L)
                val p = ((c - quarter).toFloat() / rest.toFloat()).coerceIn(0f, 1f)
                0.25f + 0.75f * p
            }
        }

        val ins = parts.map { BufferedInputStream(FileInputStream(it), 1 shl 20) }
        val seq = SequenceInputStream(ins.toEnumeration())

        var extractedDb: File? = null
        CountingInputStream(seq).use { cis ->
            ZstdInputStream(cis).use { zIn ->
                TarArchiveInputStream(zIn).use { tar ->
                    while (true) {
                        val entry = tar.nextEntry ?: break
                        val name = entry.name
                        val outFile = File(destDir, name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                val buffer = ByteArray(1024 * 1024)
                                var remaining = entry.size
                                while (remaining > 0) {
                                    val toRead = if (remaining >= buffer.size) buffer.size else remaining.toInt()
                                    val read = tar.read(buffer, 0, toRead)
                                    if (read <= 0) break
                                    out.write(buffer, 0, read)
                                    remaining -= read
                                    onUiProgress(mapProgress(cis.count))
                                }
                                out.fd.sync()
                            }
                            if (name.endsWith(".db", ignoreCase = true)) {
                                extractedDb = outFile
                            }
                        }
                        onUiProgress(mapProgress(cis.count))
                    }
                }
            }
        }
        onUiProgress(1f)
        return extractedDb ?: error("No .db file found in archive")
    }

    private fun <T> List<T>.toEnumeration(): java.util.Enumeration<T> =
        object : java.util.Enumeration<T> {
            private var index = 0

            override fun hasMoreElements(): Boolean = index < this@toEnumeration.size

            override fun nextElement(): T = this@toEnumeration[index++]
        }

    private fun maybeCleanupSources(
        source: File,
        dbDir: File,
    ) {
        if (source.parentFile?.canonicalFile == dbDir.canonicalFile) {
            if (source.name.endsWith(".tar.zst", true)) {
                runCatching { source.delete() }
            } else if (BundleParts.isPart(source.name)) {
                BundleParts.bundleFiles(source).forEach { runCatching { it.delete() } }
            }
        }
    }
}
