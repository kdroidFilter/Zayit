package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import okio.Buffer
import okio.IOException
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TarReaderTest {
    private fun TarReader.readContent(): String = content.buffer().readUtf8()

    @Test
    fun `files and directories in order with their content`() {
        val tar =
            TarWriter().apply {
                entry("index/", type = '5')
                entry("index/a.txt", "alpha".encodeToByteArray())
                entry("seforim.db", ByteArray(1000) { 7 })
                end()
            }
        val reader = TarReader(tar.buffer)

        val dir = reader.nextEntry()!!
        assertEquals("index/", dir.name)
        assertEquals(TarEntry.Type.DIRECTORY, dir.type)
        val file = reader.nextEntry()!!
        assertEquals("index/a.txt", file.name)
        assertEquals(TarEntry.Type.FILE, file.type)
        assertEquals("alpha", reader.readContent())
        val db = reader.nextEntry()!!
        assertEquals(1000L, db.size)
        assertEquals(
            1000L,
            reader.content
                .buffer()
                .readByteArray()
                .size
                .toLong(),
        )
        assertNull(reader.nextEntry())
    }

    @Test
    fun `content left unread is skipped by the next entry`() {
        val tar =
            TarWriter().apply {
                entry("big.bin", ByteArray(3000) { 1 })
                entry("next.txt", "after".encodeToByteArray())
                end()
            }
        val reader = TarReader(tar.buffer)

        reader.nextEntry()
        assertEquals("next.txt", reader.nextEntry()!!.name)
        assertEquals("after", reader.readContent())
    }

    @Test
    fun `a POSIX extended header gives the next entry its path and size`() {
        val longName = "seforim.db.lucene/" + "segment_".repeat(20) + "1.cfs"
        val tar =
            TarWriter().apply {
                pax("path" to longName, "size" to "5")
                entry(longName.take(100), "12345".encodeToByteArray())
                entry("short.txt", "x".encodeToByteArray())
                end()
            }
        val reader = TarReader(tar.buffer)

        val entry = reader.nextEntry()!!
        assertEquals(longName, entry.name)
        assertEquals(5L, entry.size)
        assertEquals("12345", reader.readContent())
        // The records only apply to the entry right after them
        assertEquals("short.txt", reader.nextEntry()!!.name)
    }

    @Test
    fun `a GNU long name and a ustar prefix make the full path`() {
        val longName = "dir/" + "n".repeat(150)
        val tar =
            TarWriter().apply {
                entry("././@LongLink", (longName + "\u0000").encodeToByteArray(), type = 'L')
                entry(longName.take(100), "a".encodeToByteArray())
                entry("file.txt", "b".encodeToByteArray(), prefix = "some/prefix")
                end()
            }
        val reader = TarReader(tar.buffer)

        assertEquals(longName, reader.nextEntry()!!.name)
        assertEquals("some/prefix/file.txt", reader.nextEntry()!!.name)
    }

    @Test
    fun `a size in base-256 is read whole`() {
        val size = ByteArray(12)
        size[0] = 0x80.toByte()
        size[11] = 4
        val tar =
            TarWriter().apply {
                entry("four.bin", byteArrayOf(1, 2, 3, 4), sizeField = size)
                end()
            }
        val reader = TarReader(tar.buffer)

        assertEquals(4L, reader.nextEntry()!!.size)
    }

    @Test
    fun `an archive cut after its last entry ends cleanly`() {
        val tar = TarWriter().apply { entry("only.txt", "z".encodeToByteArray()) }
        val reader = TarReader(tar.buffer)

        reader.nextEntry()
        assertNull(reader.nextEntry())
    }

    @Test
    fun `a corrupted header is refused`() {
        val tar = TarWriter().apply { entry("a.txt", "a".encodeToByteArray()) }
        val bytes = tar.buffer.readByteArray()
        bytes[10] = 'X'.code.toByte()
        val reader = TarReader(Buffer().write(bytes))

        assertFailsWith<IOException> { reader.nextEntry() }
    }
}
