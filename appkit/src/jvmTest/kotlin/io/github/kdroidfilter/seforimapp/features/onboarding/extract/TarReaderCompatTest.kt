package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import okio.Buffer
import okio.buffer
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The archives commons-compress writes as the database bundle's packager does (POSIX long names and sizes). */
class TarReaderCompatTest {
    @Test
    fun `reads what the bundle packager writes`() {
        val longName = "seforim.db.lucene/" + "a".repeat(120) + ".cfs"
        val files = linkedMapOf("seforim.db" to ByteArray(70_000) { (it % 251).toByte() }, longName to "long".toByteArray())
        val bytes =
            ByteArrayOutputStream().also { out ->
                TarArchiveOutputStream(out).use { tar ->
                    tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                    tar.putArchiveEntry(TarArchiveEntry("seforim.db.lucene/"))
                    tar.closeArchiveEntry()
                    files.forEach { (name, content) ->
                        tar.putArchiveEntry(TarArchiveEntry(name).apply { size = content.size.toLong() })
                        tar.write(content)
                        tar.closeArchiveEntry()
                    }
                    tar.finish()
                }
            }
        val reader = TarReader(Buffer().write(bytes.toByteArray()))

        val dir = reader.nextEntry()!!
        assertEquals("seforim.db.lucene/", dir.name)
        assertEquals(TarEntry.Type.DIRECTORY, dir.type)
        files.forEach { (name, content) ->
            val entry = reader.nextEntry()!!
            assertEquals(name, entry.name)
            assertEquals(TarEntry.Type.FILE, entry.type)
            assertEquals(
                content.toList(),
                reader.content
                    .buffer()
                    .readByteArray()
                    .toList(),
            )
        }
        assertNull(reader.nextEntry())
    }
}
