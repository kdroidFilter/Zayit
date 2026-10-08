package io.github.kdroidfilter.seforimapp.backup

/**
 * What the app backs up, as opaque bytes: the source never knows where they go
 * (see [BackupDestination]), only [BackupManager] links the two.
 */
interface BackupSource {
    /** The current data, read while the app keeps running. */
    fun snapshot(): BackupSnapshot

    /** Whether a staged restore still waits to be applied: backing up now would save the data it replaces. */
    fun hasPendingRestore(): Boolean

    /** Validates [backup] and stages it to replace the local data; throws when it is not a backup. */
    fun stageRestore(backup: ByteArray)
}

class BackupSnapshot(
    // Identifies the data, so an unchanged snapshot is not written again
    val fingerprint: String,
    // Built only when the snapshot is written: an unchanged one is never packed
    content: () -> ByteArray,
) {
    val content: ByteArray by lazy(content)
}
