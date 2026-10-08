package io.github.kdroidfilter.seforimapp.framework.backup

import com.russhwolf.settings.Settings
import io.github.kdroidfilter.seforimapp.backup.writeBytesAtomically
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.database.getUserSettingsDatabasePath
import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A backup waiting to replace the user's data. The running app keeps its database open, so a
 * restore is staged here and [runOnce] applies it at the next launch, before the graph opens
 * the database and reads the preferences.
 */
object PendingUserDataRestore {
    private const val PENDING_NAME = "pending-user-restore.zip"

    // The data a restore replaced, kept until the next one as a way back.
    private const val PREVIOUS_SUFFIX = ".before-restore"

    private fun pendingFile(): File = File(FileKit.databasesDir.path, PENDING_NAME)

    internal fun stage(archive: ByteArray) {
        pendingFile().writeBytesAtomically(archive)
    }

    fun isPending(): Boolean = runCatching { pendingFile().exists() }.getOrDefault(false)

    fun runOnce() {
        val pending = runCatching { pendingFile() }.getOrNull()?.takeIf { it.exists() } ?: return
        val (database, manifest) =
            runCatching { UserDataBackup.readArchive(pending.readBytes()) }.getOrElse {
                // Validated when staged, so only a damaged file lands here: retrying would not help.
                warnln { "[PendingUserDataRestore] Dropping an unreadable backup: ${it.message}" }
                pending.delete()
                return
            }
        runCatching {
            val dbFile = File(getUserSettingsDatabasePath())
            val previous = File(dbFile.path + PREVIOUS_SUFFIX)
            // Once per staged restore: a retry after a failed attempt would copy the half-restored database.
            if (dbFile.exists() && previous.lastModified() < pending.lastModified()) {
                Files.copy(dbFile.toPath(), previous.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            File(dbFile.path + "-journal").delete()
            dbFile.writeBytesAtomically(database)
            AppSettings.importPortablePreferences(Settings(), manifest.preferences)
            pending.delete()
            infoln { "[PendingUserDataRestore] Restored the backup of ${manifest.createdAtEpochMs}" }
        }.onFailure {
            // Kept for the next launch (a locked or full disk passes), the Drive backup paused until then.
            warnln { "[PendingUserDataRestore] Could not restore the backup, will retry: ${it.message}" }
        }
    }
}
