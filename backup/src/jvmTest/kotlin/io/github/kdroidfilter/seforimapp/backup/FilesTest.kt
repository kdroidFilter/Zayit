package io.github.kdroidfilter.seforimapp.backup

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FilesTest {
    private val directory = Files.createTempDirectory("files").toFile()

    @Test
    fun replaces_the_file_and_leaves_no_temporary_behind() {
        val file = File(directory, "backup.zip").apply { writeText("old") }

        file.writeBytesAtomically("new".toByteArray())

        assertEquals("new", file.readText())
        assertFalse(File(directory, "backup.zip.tmp").exists())
    }

    @Test
    fun a_failed_write_keeps_the_previous_file() {
        val file = File(directory, "backup.zip").apply { writeText("old") }
        // A directory in the way of the temporary file makes the write fail.
        File(directory, "backup.zip.tmp").mkdir()

        runCatching { file.writeBytesAtomically("new".toByteArray()) }

        assertEquals("old", file.readText())
    }
}
