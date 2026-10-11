package io.github.kdroidfilter.seforimapp.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Where a backup is kept. It only sees opaque bytes, whatever their source. */
interface BackupDestination {
    suspend fun write(backup: ByteArray)

    /** The latest backup, or null when there is none. */
    suspend fun readLatest(): ByteArray?
}

/** A backup file the user picked. */
class LocalFileDestination(
    private val file: File,
) : BackupDestination {
    override suspend fun write(backup: ByteArray) =
        withContext(Dispatchers.IO) {
            file.writeBytesAtomically(backup)
        }

    override suspend fun readLatest(): ByteArray? = withContext(Dispatchers.IO) { file.takeIf { it.isFile }?.readBytes() }
}
