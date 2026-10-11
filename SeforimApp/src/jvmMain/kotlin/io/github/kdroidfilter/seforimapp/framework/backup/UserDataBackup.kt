package io.github.kdroidfilter.seforimapp.framework.backup

import app.cash.sqldelight.db.SqlDriver
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.backup.BackupSnapshot
import io.github.kdroidfilter.seforimapp.backup.BackupSource
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.database.USER_SETTINGS_DRIVER
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The user's data as one archive: the user database (notes, highlights, favorites, history…)
 * and the preferences that follow the user to another machine. Restoring one is staged and
 * applied at the next launch by [PendingUserDataRestore], before the database is opened.
 */
@Inject
@SingleIn(AppScope::class)
class UserDataBackup(
    @Named(USER_SETTINGS_DRIVER) private val driver: SqlDriver,
    private val appSettings: AppSettings,
) : BackupSource {
    override fun snapshot(): BackupSnapshot = capture().let { BackupSnapshot(it.fingerprint, it::toArchive) }

    /** A consistent copy of the user's data, taken while the app keeps using its database. */
    fun capture(): Snapshot {
        val copy = File.createTempFile("user_settings", ".db").apply { delete() }
        try {
            // VACUUM INTO writes a consistent, compacted copy through the live connection.
            driver.execute(null, "VACUUM INTO ?", 1) { bindString(0, copy.absolutePath) }
            return Snapshot(database = copy.readBytes(), preferences = appSettings.exportPortablePreferences())
        } finally {
            copy.delete()
        }
    }

    override fun hasPendingRestore(): Boolean = PendingUserDataRestore.isPending()

    /** Validates [backup] and stages it for the next launch; throws when it is not a backup. */
    override fun stageRestore(backup: ByteArray) {
        val archive = upgradeLegacyBackup(backup)
        val (database, manifest) = readArchive(archive)
        require(manifest.format <= FORMAT_VERSION) { "Backup format ${manifest.format} is newer than this app" }
        require(database.startsWith(SQLITE_HEADER)) { "The backup's database is not a SQLite file" }
        PendingUserDataRestore.stage(archive)
    }

    /**
     * [backup] as an archive. The exports made before the archive format are a bare copy of the
     * user database: they restore the database alone and keep this device's preferences.
     */
    internal fun upgradeLegacyBackup(backup: ByteArray): ByteArray =
        if (backup.startsWith(SQLITE_HEADER)) Snapshot(backup, appSettings.exportPortablePreferences()).toArchive() else backup

    class Snapshot(
        val database: ByteArray,
        val preferences: Map<String, String>,
    ) {
        /** Identifies the content, so an unchanged snapshot is not uploaded again. */
        val fingerprint: String by lazy {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(database)
            preferences.toSortedMap().forEach { (key, value) -> digest.update("$key=$value\n".toByteArray()) }
            HexFormat.of().formatHex(digest.digest())
        }

        fun toArchive(): ByteArray {
            val manifest = BackupManifest(FORMAT_VERSION, System.currentTimeMillis(), preferences)
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
                zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
                zip.write(database)
            }
            return out.toByteArray()
        }
    }

    internal companion object {
        private const val FORMAT_VERSION = 1
        private const val MANIFEST_ENTRY = "manifest.json"
        private const val DATABASE_ENTRY = "user_settings.db"
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray()
        private val json = Json { ignoreUnknownKeys = true }

        /** The database and the manifest of [archive]; throws when either is missing. */
        fun readArchive(archive: ByteArray): Pair<ByteArray, BackupManifest> {
            var database: ByteArray? = null
            var manifest: BackupManifest? = null
            ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    when (entry.name) {
                        DATABASE_ENTRY -> database = zip.readBytes()
                        MANIFEST_ENTRY -> manifest = json.decodeFromString(BackupManifest.serializer(), zip.readBytes().decodeToString())
                    }
                }
            }
            return requireNotNull(database) { "The backup has no database" } to
                requireNotNull(manifest) { "The backup has no manifest" }
        }

        private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
    }
}

@Serializable
internal data class BackupManifest(
    val format: Int,
    val createdAtEpochMs: Long,
    val preferences: Map<String, String>,
)
