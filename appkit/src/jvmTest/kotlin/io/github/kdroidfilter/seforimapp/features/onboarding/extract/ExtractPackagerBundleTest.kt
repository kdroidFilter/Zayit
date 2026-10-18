package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import com.github.luben.zstd.ZstdOutputStream
import com.russhwolf.settings.MapSettings
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

/** A bundle written as SeforimLibrary's packager does (zstd-jni with its checksum and workers, POSIX tar). */
class ExtractPackagerBundleTest {
    private val dir = Files.createTempDirectory("packager-bundle").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun `extracts a bundle written by the packager`() =
        runTest {
            val database = Random(7).nextBytes(2_000_000)
            val longName = "seforim.db.lucene/" + "b".repeat(130) + ".cfs"
            val segment = Random(8).nextBytes(10_000)
            val bundle = File(dir, BundleParts.BUNDLE_NAME)
            ZstdOutputStream(bundle.outputStream(), 3).use { zstd ->
                zstd.setChecksum(true)
                zstd.setWorkers(2)
                TarArchiveOutputStream(zstd).use { tar ->
                    tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                    tar.putArchiveEntry(TarArchiveEntry("seforim.db.lucene/"))
                    tar.closeArchiveEntry()
                    listOf(longName to segment, "seforim.db" to database).forEach { (name, content) ->
                        tar.putArchiveEntry(TarArchiveEntry(name).apply { size = content.size.toLong() })
                        tar.write(content)
                        tar.closeArchiveEntry()
                    }
                    tar.finish()
                }
            }
            val dbDir = File(dir, "databases")
            val useCase = ExtractUseCase(AppSettings(MapSettings())) { dbDir.toOkioPath() }

            val dbPath = useCase.extractToDatabase(bundle.path) {}

            assertContentEquals(database, File(dbPath).readBytes())
            assertContentEquals(segment, File(dbDir, longName).readBytes())
        }
}
