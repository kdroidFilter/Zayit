package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import com.github.luben.zstd.ZstdOutputStream
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.framework.database.resetDatabasePathCache
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtractUseCaseTest {
    @BeforeTest
    fun initializeFileKit() {
        FileKit.init("io.github.kdroidfilter.seforimapp")
    }

    @AfterTest
    fun restoreSettings() {
        AppSettings.initialize(Settings())
        resetDatabasePathCache()
    }

    @Test
    fun `split bundle installs database and sidecars at selected destination and survives reinitialization`() = runTest {
        val settings = MapSettings()
        AppSettings.initialize(settings)
        AppSettings.setDatabaseInstallInProgress(true)
        val root = Files.createTempDirectory("zayit-extract-").toFile()
        try {
            val bundle = File(root, "bundle.tar.zst")
            ZstdOutputStream(bundle.outputStream()).use { zstd ->
                TarArchiveOutputStream(zstd).use { tar ->
                    fun add(name: String, contents: String) {
                        val bytes = contents.toByteArray()
                        tar.putArchiveEntry(TarArchiveEntry(name).apply { size = bytes.size.toLong() })
                        tar.write(bytes)
                        tar.closeArchiveEntry()
                    }
                    add("seforim.db", "database")
                    add("catalog.pb", "catalog")
                    add("release_info.txt", "release")
                    add("seforim.db.lucene/segments", "index")
                    add("lexical.db", "dictionary") // Last DB in archive must not become the active DB.
                }
            }
            val compressed = bundle.readBytes()
            val middle = compressed.size / 2
            val part01 = root.resolve("bundle.tar.zst.part01").apply { writeBytes(compressed.copyOfRange(0, middle)) }
            root.resolve("bundle.tar.zst.part02").writeBytes(compressed.copyOfRange(middle, compressed.size))
            val destination = root.resolve("selected").resolve("Zayit")
            destination.mkdirs()

            val installed = ExtractUseCase().extractToDatabase(part01.absolutePath, destination) {}
            assertEquals(destination.resolve("seforim.db").absolutePath, installed)
            assertEquals(installed, AppSettings.getDatabasePath())
            assertTrue(!AppSettings.isDatabaseInstallInProgress())
            assertTrue(destination.resolve("lexical.db").exists())
            assertTrue(destination.resolve("catalog.pb").exists())
            assertTrue(destination.resolve("release_info.txt").exists())
            assertTrue(destination.resolve("seforim.db.lucene/segments").exists())
            assertTrue(part01.exists())
            assertTrue(root.resolve("bundle.tar.zst.part02").exists())

            AppSettings.initialize(settings)
            resetDatabasePathCache()
            assertEquals(installed, getDatabasePath())
            assertTrue(!AppSettings.isDatabaseInstallInProgress())
        } finally {
            root.deleteRecursively()
        }
    }
}
