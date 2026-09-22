package io.github.kdroidfilter.seforimapp.features.database.update

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DatabaseCleanupUseCaseTest {
    @AfterTest
    fun restoreSettings() {
        AppSettings.initialize(Settings())
    }

    @Test
    fun `cleanup removes only database artifacts at custom and default locations`() = runTest {
        AppSettings.initialize(MapSettings())
        val root = Files.createTempDirectory("zayit-cleanup-").toFile()
        try {
            val default = root.resolve("default").apply { mkdir() }
            val selected = root.resolve("user").apply { mkdir() }
            val custom = selected.resolve("Zayit").apply { mkdir() }
            val database = custom.resolve("seforim.db").apply { writeText("database") }
            custom.resolve("catalog.pb").writeText("catalog")
            custom.resolve("lexical.db").writeText("dictionary")
            custom.resolve("seforim.db.backup").writeText("backup")
            custom.resolve("seforim.db.applying").writeText("marker")
            custom.resolve("seforim.db.lucene").apply { mkdir(); resolve("segments").writeText("index") }
            custom.resolve("zayit-download.tar.zst.part01").writeText("incomplete")
            default.resolve("seforim.db").writeText("old")
            val unrelatedInSelected = selected.resolve("notes.txt").apply { writeText("keep") }
            val unrelatedInCustom = custom.resolve("other.db").apply { writeText("keep") }
            val unrelatedInDefault = default.resolve("other.db").apply { writeText("keep") }
            val nextDestination = root.resolve("next").apply { mkdir() }
            nextDestination.resolve("zayit-download.tar.zst.part01").writeText("partial")
            val unrelatedInNext = nextDestination.resolve("family-photo.jpg").apply { writeText("keep") }
            AppSettings.setDatabasePath(database.absolutePath)

            assertIs<DatabaseCleanupUseCase.CleanupResult.Success>(
                DatabaseCleanupUseCase(default).cleanupDatabaseFiles(nextDestination),
            )
            assertFalse(database.exists())
            assertFalse(custom.resolve("catalog.pb").exists())
            assertFalse(custom.resolve("seforim.db.backup").exists())
            assertFalse(custom.resolve("seforim.db.applying").exists())
            assertFalse(custom.resolve("seforim.db.lucene").exists())
            assertFalse(default.resolve("seforim.db").exists())
            assertFalse(nextDestination.resolve("zayit-download.tar.zst.part01").exists())
            assertTrue(unrelatedInSelected.exists())
            assertTrue(unrelatedInCustom.exists())
            assertTrue(unrelatedInDefault.exists())
            assertTrue(unrelatedInNext.exists())
            assertEquals(database.absolutePath, AppSettings.getDatabasePath())
        } finally {
            root.deleteRecursively()
        }
    }
}
