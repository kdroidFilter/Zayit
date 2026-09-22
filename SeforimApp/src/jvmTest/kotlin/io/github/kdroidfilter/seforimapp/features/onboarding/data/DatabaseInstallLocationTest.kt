package io.github.kdroidfilter.seforimapp.features.onboarding.data

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.onboarding.diskspace.AvailableDiskSpaceUseCase
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.framework.database.resetDatabasePathCache
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseInstallLocationTest {
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
    fun `custom target stays separate from active path and uses selected filesystem`() = runTest {
        AppSettings.initialize(MapSettings())
        val selected = Files.createTempDirectory("zayit-destination-").toFile()
        try {
            val destination = DatabaseInstallLocation.customDirectory(selected)
            assertEquals(selected.resolve("Zayit"), destination)
            assertEquals(DatabaseInstallLocation.defaultDirectory(), DatabaseInstallLocation.currentDirectoryOrDefault())
            DatabaseInstallLocation.prepareDirectory(destination)
            assertTrue(destination.isDirectory)
            assertEquals(null, AppSettings.getDatabasePath())

            val expected = Files.getFileStore(destination.toPath())
            val disk = AvailableDiskSpaceUseCase().getDiskSpaceInfo(destination)
            assertEquals(expected.totalSpace, disk.totalBytes)
            assertTrue(disk.availableBytes <= expected.totalSpace)
        } finally {
            selected.deleteRecursively()
        }
    }

    @Test
    fun `missing existing database location is rejected without changing settings`() {
        val settings = MapSettings()
        AppSettings.initialize(settings)
        val selected = Files.createTempDirectory("zayit-missing-").toFile()
        try {
            val destination = selected.resolve("Zayit")
            val database = destination.resolve("seforim.db")
            AppSettings.setDatabasePath(database.absolutePath)
            AppSettings.setDatabaseInstallInProgress(true)
            AppSettings.initialize(settings)
            assertTrue(AppSettings.isDatabaseInstallInProgress())
            assertFailsWith<IllegalArgumentException> { DatabaseInstallLocation.prepareDirectory(destination) }
            resetDatabasePathCache()
            assertFailsWith<IllegalStateException> { getDatabasePath() }
            assertEquals(database.absolutePath, AppSettings.getDatabasePath())
            assertTrue(!destination.exists())
        } finally {
            selected.deleteRecursively()
        }
    }
}
