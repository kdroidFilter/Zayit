package io.github.kdroidfilter.seforimapp.framework.backup

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.PropertiesSettings
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import java.io.File
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UserDataBackupTest {
    private lateinit var dbFile: File
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var settings: PropertiesSettings
    private lateinit var backup: UserDataBackup

    @BeforeTest
    fun setUp() {
        dbFile = File.createTempFile("user_settings", ".db")
        driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}").also { UserSettingsDb.Schema.create(it) }
        settings = PropertiesSettings(Properties())
        backup = UserDataBackup(driver, AppSettings(settings))
    }

    @AfterTest
    fun tearDown() {
        driver.close()
        dbFile.delete()
    }

    @Test
    fun only_portable_preferences_are_backed_up() {
        settings.putFloat("text_size", 22f)
        settings.putString("home_widget_options_luach", "compact")
        settings.putString("database_path", "/somewhere/seforim.db")
        settings.putBoolean("onboarding_finished", true)

        val preferences = backup.capture().preferences

        assertEquals(mapOf("text_size" to "22.0", "home_widget_options_luach" to "compact"), preferences)
    }

    @Test
    fun imported_preferences_keep_their_type_and_spare_machine_keys() {
        settings.putString("database_path", "/here/seforim.db")
        settings.putString("theme_mode", "DARK")

        AppSettings.importPortablePreferences(settings, mapOf("text_size" to "22.0", "database_path" to "/elsewhere"))

        assertEquals(22f, settings.getFloat("text_size", 0f))
        assertEquals("/here/seforim.db", settings.getString("database_path", ""))
        // A portable key absent from the backup goes back to its default.
        assertFalse(settings.hasKey("theme_mode"))
    }

    @Test
    fun archive_round_trips_and_stages_as_a_valid_backup() {
        driver.execute(null, "INSERT INTO favorite_folder(name, createdAt) VALUES ('Talmud', 0)", 0)
        settings.putString("accent_color", "BLUE")
        val snapshot = backup.capture()

        val (database, manifest) = UserDataBackup.readArchive(snapshot.toArchive())

        assertTrue(database.contentEquals(snapshot.database))
        assertEquals(mapOf("accent_color" to "BLUE"), manifest.preferences)
    }

    @Test
    fun fingerprint_follows_the_content() {
        val before = backup.capture()
        assertEquals(before.fingerprint, backup.capture().fingerprint)

        settings.putString("accent_color", "BLUE")
        assertNotEquals(before.fingerprint, backup.capture().fingerprint)
    }

    @Test
    fun a_legacy_database_export_restores_with_this_device_preferences() {
        settings.putString("theme_mode", "DARK")
        val legacyExport = backup.capture().database

        val (database, manifest) = UserDataBackup.readArchive(backup.upgradeLegacyBackup(legacyExport))

        assertTrue(database.contentEquals(legacyExport))
        assertEquals(mapOf("theme_mode" to "DARK"), manifest.preferences)
    }

    @Test
    fun an_archive_is_not_upgraded() {
        val archive = backup.snapshot().content
        assertTrue(backup.upgradeLegacyBackup(archive).contentEquals(archive))
    }
}
