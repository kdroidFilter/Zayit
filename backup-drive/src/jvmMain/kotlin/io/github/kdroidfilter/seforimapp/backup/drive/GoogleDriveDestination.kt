package io.github.kdroidfilter.seforimapp.backup.drive

import io.github.kdroidfilter.seforimapp.backup.BackupDestination
import io.github.kdroidfilter.seforimapp.logger.warnln

/**
 * The backups in the app's hidden Drive folder, for one operation of [GoogleDriveSync]. Like
 * IntelliJ's settings sync, a backup never overwrites the previous one: each is a new file, the
 * latest wins, and only the [keep] most recent stay, so a bad backup cannot destroy a good one.
 */
internal class GoogleDriveDestination(
    private val api: DriveFilesApi,
    // Asked for only when Drive is actually called (it may refresh the token over the network)
    private val accessToken: suspend () -> String,
    private val fileName: String,
    // The date of the latest Drive backup this device knows of; a newer one was made elsewhere and
    // is not overwritten (null when the user chose to replace whatever Drive holds).
    private val expectedLatestEpochMs: Long? = null,
    private val keep: Int = KEPT_BACKUPS,
) : BackupDestination {
    /** The Drive file this destination last wrote or read. */
    var remote: DriveFilesApi.RemoteFile? = null
        private set

    override suspend fun write(backup: ByteArray) {
        val token = accessToken()
        if (expectedLatestEpochMs != null) {
            val latest = api.findLatest(token, fileName)
            if (latest != null && latest.modifiedEpochMs > expectedLatestEpochMs) throw RemoteBackupChangedException(latest.modifiedEpochMs)
        }
        remote = api.create(token, fileName, backup)
        // Best effort: an old backup left over is only a little Drive space.
        runCatching {
            api.listLatestFirst(token, fileName).drop(keep).forEach { api.delete(token, it.id) }
        }.onFailure { warnln { "[Drive] Could not prune old backups: ${it.message}" } }
    }

    override suspend fun readLatest(): ByteArray? {
        val token = accessToken()
        val latest = api.findLatest(token, fileName) ?: return null
        return api.download(token, latest.id).also { remote = latest }
    }

    private companion object {
        const val KEPT_BACKUPS = 10
    }
}

/** Another device backed up since this one last saw Drive: its backup waits for the user's choice. */
internal class RemoteBackupChangedException(
    val remoteEpochMs: Long,
) : IllegalStateException("A newer backup from another device is on Drive")
