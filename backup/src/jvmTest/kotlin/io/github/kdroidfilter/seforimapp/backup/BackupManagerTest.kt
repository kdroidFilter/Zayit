package io.github.kdroidfilter.seforimapp.backup

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupManagerTest {
    private class FakeSource(
        var content: String,
    ) : BackupSource {
        var staged: ByteArray? = null

        override fun snapshot() = BackupSnapshot(fingerprint = content) { content.toByteArray() }

        override fun hasPendingRestore() = staged != null

        override fun stageRestore(backup: ByteArray) {
            staged = backup
        }
    }

    private val file = File(Files.createTempDirectory("backup").toFile(), "backup.zip")

    @Test
    fun backup_then_restore_through_a_local_file() =
        runTest {
            val source = FakeSource("notes")
            val manager = BackupManager(source)

            assertEquals("notes", manager.backup(LocalFileDestination(file)))
            assertTrue(manager.restore(LocalFileDestination(file)))
            assertContentEquals("notes".toByteArray(), source.staged)
        }

    @Test
    fun an_unchanged_snapshot_is_not_written_again() =
        runTest {
            val manager = BackupManager(FakeSource("notes"))

            assertNull(manager.backup(LocalFileDestination(file), unlessUnchangedFrom = "notes"))
            assertFalse(file.exists())
        }

    @Test
    fun nothing_to_restore_from_a_missing_file() =
        runTest {
            val source = FakeSource("notes")

            assertFalse(BackupManager(source).restore(LocalFileDestination(file)))
            assertNull(source.staged)
        }
}
