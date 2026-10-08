package io.github.kdroidfilter.seforimapp.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Moves the data of [source] to and from any [BackupDestination]: the only code that knows both. */
class BackupManager(
    private val source: BackupSource,
) {
    fun hasPendingRestore(): Boolean = source.hasPendingRestore()

    /**
     * Writes a snapshot of the source to [destination], unless its content still has the
     * fingerprint [unlessUnchangedFrom].
     *
     * @return the fingerprint written, or null when nothing changed.
     */
    suspend fun backup(
        destination: BackupDestination,
        unlessUnchangedFrom: String? = null,
    ): String? {
        val snapshot = withContext(Dispatchers.IO) { source.snapshot() }
        if (snapshot.fingerprint == unlessUnchangedFrom) return null
        destination.write(snapshot.content)
        return snapshot.fingerprint
    }

    /**
     * Stages the latest backup of [destination] to replace the local data; the app applies it
     * when it restarts.
     *
     * @return false when [destination] holds no backup.
     */
    suspend fun restore(destination: BackupDestination): Boolean {
        val backup = destination.readLatest() ?: return false
        withContext(Dispatchers.IO) { source.stageRestore(backup) }
        return true
    }
}
