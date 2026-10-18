package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import com.squareup.zstd.okio.zstdDecompress
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import okio.ForwardingSource
import okio.HashingSource
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.Source
import okio.Timeout
import okio.blackholeSink
import okio.buffer
import okio.use

class ExtractUseCase(
    private val appSettings: AppSettings,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val databasesDir: () -> Path = { FileKit.databasesDir.path.toPath() },
) {
    suspend fun extractToDatabase(
        sourcePath: String,
        onProgress: (Float) -> Unit,
    ): String =
        withContext(Dispatchers.Default) {
            val dbDir = databasesDir().also(fileSystem::createDirectories)
            val source = sourcePath.toPath()
            require(fileSystem.exists(source)) { "Selected file not found" }

            onProgress(0f)
            val dbFile: Path =
                withContext(Dispatchers.IO) {
                    val lower = source.name.lowercase()
                    when {
                        BundleParts.isPart(source.name) -> {
                            val bundle = BundleParts.resolve(source, fileSystem)
                            val parts = bundle.parts
                            val result =
                                extractTarZstFromPartsStreaming(parts, dbDir, bundle.sha256) { mapped ->
                                    onProgress(mapped)
                                }
                            // Only cleanup if parts live in our databases directory (downloaded by the app)
                            val canDelete =
                                runCatching {
                                    val base = fileSystem.canonicalize(dbDir)
                                    parts.all { fileSystem.canonicalize(it).parent == base }
                                }.getOrDefault(false)
                            if (canDelete) BundleParts.bundleFiles(source, fileSystem).forEach { runCatching { fileSystem.delete(it) } }
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
                            val targetDb = dbDir / targetName
                            val result = extractDbZst(source, targetDb) { p -> onProgress(p.coerceIn(0f, 1f)) }
                            runCatching { maybeCleanupSources(source, dbDir) }
                            result
                        }
                        else -> error("Unsupported file type: ${source.name}")
                    }
                }
            onProgress(1f)
            appSettings.setDatabasePath(dbFile.toString())
            runCatching { maybeCleanupSources(source, dbDir) }
            return@withContext dbFile.toString()
        }

    private fun extractDbZst(
        sourceZst: Path,
        targetDb: Path,
        onProgress: (Float) -> Unit,
    ): Path {
        val totalCompressed = fileSystem.sizeOf(sourceZst).coerceAtLeast(1L)
        val counting = CountingSource(fileSystem.source(sourceZst))
        counting.zstdDecompress().use { decompressed ->
            writeFile(targetDb, decompressed) { onProgress(counting.count.toFloat() / totalCompressed.toFloat()) }
        }
        onProgress(1f)
        return targetDb
    }

    private fun extractTarZstFile(
        zstFile: Path,
        destDir: Path,
        onProgress: (Float) -> Unit,
    ): Path {
        val totalCompressed = fileSystem.sizeOf(zstFile).coerceAtLeast(1L)
        val counting = CountingSource(fileSystem.source(zstFile))
        return counting.zstdDecompress().use { decompressed ->
            extractTar(decompressed, destDir) { onProgress(counting.count.toFloat() / totalCompressed.toFloat()) }
        }
    }

    /** Extracts the concatenated [parts]; when [expectedSha256] is known, the compressed stream must match it. */
    private fun extractTarZstFromPartsStreaming(
        parts: List<Path>,
        destDir: Path,
        expectedSha256: String?,
        onUiProgress: (Float) -> Unit,
    ): Path {
        require(parts.isNotEmpty())

        val totalCompressed = parts.sumOf { fileSystem.sizeOf(it).coerceAtLeast(0L) }.coerceAtLeast(1L)
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

        val counting = CountingSource(ConcatenatedSource(parts.map(fileSystem::source)))
        // Hashing the bytes already read for extraction costs no extra I/O
        val hashing = expectedSha256?.let { HashingSource.sha256(counting) }
        val compressed: Source = hashing ?: counting

        val extractedDb =
            compressed.zstdDecompress().use { decompressed ->
                val db = extractTar(decompressed, destDir) { onUiProgress(mapProgress(counting.count)) }
                if (hashing != null) {
                    // The tar ends before the compressed stream does; hash the rest too
                    blackholeSink().buffer().use { it.writeAll(hashing) }
                    if (!hashing.hash.hex().equals(expectedSha256, ignoreCase = true)) {
                        fileSystem.delete(db, mustExist = false)
                        error("The database bundle is corrupted (sha256 mismatch)")
                    }
                }
                db
            }
        onUiProgress(1f)
        return extractedDb
    }

    /** Writes the tar read from [archive] under [destDir]; returns the database it holds. */
    private fun extractTar(
        archive: Source,
        destDir: Path,
        onChunk: () -> Unit,
    ): Path {
        var extractedDb: Path? = null
        val tar = TarReader(archive.buffer())
        while (true) {
            val entry = tar.nextEntry() ?: break
            val outFile = destDir.resolve(safeEntryName(entry.name), normalize = true)
            when (entry.type) {
                TarEntry.Type.DIRECTORY -> fileSystem.createDirectories(outFile)
                TarEntry.Type.FILE -> {
                    outFile.parent?.let(fileSystem::createDirectories)
                    writeFile(outFile, tar.content, onChunk)
                    if (entry.name.endsWith(".db", ignoreCase = true)) extractedDb = outFile
                }
                // The bundle holds no links nor special files
                TarEntry.Type.OTHER -> Unit
            }
            onChunk()
        }
        return extractedDb ?: error("No .db file found in archive")
    }

    /** [content] into the file at [path], synced to the disk; [onChunk] after each chunk. */
    private fun writeFile(
        path: Path,
        content: Source,
        onChunk: () -> Unit,
    ) {
        fileSystem.openReadWrite(path).use { handle ->
            handle.resize(0L)
            handle.sink().use { out ->
                val buffer = Buffer()
                while (true) {
                    val read = content.read(buffer, CHUNK_BYTES)
                    if (read == -1L) break
                    out.write(buffer, read)
                    onChunk()
                }
            }
            handle.flush()
        }
    }

    private fun maybeCleanupSources(
        source: Path,
        dbDir: Path,
    ) {
        if (fileSystem.canonicalize(source).parent == fileSystem.canonicalize(dbDir)) {
            if (source.name.endsWith(".tar.zst", true)) {
                runCatching { fileSystem.delete(source) }
            } else if (BundleParts.isPart(source.name)) {
                BundleParts.bundleFiles(source, fileSystem).forEach { runCatching { fileSystem.delete(it) } }
            }
        }
    }

    private companion object {
        const val CHUNK_BYTES = 1L shl 20

        /** An entry's name as a path inside the destination: never absolute, never climbing out of it. */
        fun safeEntryName(name: String): String {
            require(!name.startsWith("/") && name.split('/', '\\').none { it == ".." }) {
                "Unsafe path in the database bundle: $name"
            }
            return name
        }
    }
}

/** Counts the bytes read through it: the compressed bytes consumed, for the progress. */
private class CountingSource(
    delegate: Source,
) : ForwardingSource(delegate) {
    var count: Long = 0
        private set

    override fun read(
        sink: Buffer,
        byteCount: Long,
    ): Long {
        val read = super.read(sink, byteCount)
        if (read > 0) count += read
        return read
    }
}

/** [sources] read one after the other, as the parts of a split file. */
private class ConcatenatedSource(
    private val sources: List<Source>,
) : Source {
    private var index = 0

    override fun read(
        sink: Buffer,
        byteCount: Long,
    ): Long {
        while (index < sources.size) {
            val read = sources[index].read(sink, byteCount)
            if (read != -1L) return read
            index++
        }
        return -1L
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() = sources.forEach { runCatching { it.close() } }
}
