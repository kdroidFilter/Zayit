package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import okio.Buffer
import okio.BufferedSource
import okio.EOFException
import okio.IOException
import okio.Source
import okio.Timeout

/** An entry of a tar archive: its [name] (a relative path, `/`-separated) and the [size] of its content. */
class TarEntry(
    val name: String,
    val size: Long,
    val type: Type,
) {
    enum class Type { FILE, DIRECTORY, OTHER }
}

/**
 * Reads a tar archive as a stream: ustar entries, with POSIX (PAX) extended headers for long paths and sizes over 8 GiB
 * (what the database bundle's packager writes) and GNU long names. Call [nextEntry], then read its content from
 * [content]; what isn't read is skipped by the next call. Links and special files come as [TarEntry.Type.OTHER].
 */
class TarReader(
    private val source: BufferedSource,
) {
    // What is left of the current entry's content, then the padding to the next 512-byte block
    private var remaining = 0L
    private var padding = 0L

    /** The current entry's content: at most its remaining bytes, then the end of the stream. */
    val content: Source =
        object : Source {
            override fun read(
                sink: Buffer,
                byteCount: Long,
            ): Long {
                if (remaining == 0L) return -1L
                val read = source.read(sink, minOf(byteCount, remaining))
                if (read == -1L) throw EOFException("Truncated tar entry")
                remaining -= read
                return read
            }

            override fun timeout(): Timeout = source.timeout()

            override fun close() = Unit
        }

    /** The next entry, or null at the end of the archive. */
    fun nextEntry(): TarEntry? {
        var paxPath: String? = null
        var paxSize: Long? = null
        var gnuLongName: String? = null
        while (true) {
            source.skip(remaining + padding)
            remaining = 0L
            padding = 0L
            // An archive ends with zero blocks; tolerate one cut right after its last entry
            if (source.exhausted()) return null
            val header = source.readByteArray(BLOCK_SIZE.toLong())
            if (header.all { it == ZERO }) return null
            requireValidChecksum(header)

            val size = parseNumber(header, SIZE_OFFSET, SIZE_LENGTH)
            remaining = size
            padding = (BLOCK_SIZE - size % BLOCK_SIZE) % BLOCK_SIZE
            when (val typeFlag = header[TYPE_OFFSET].toInt().toChar()) {
                // A POSIX extended header: records for the next entry
                PAX_HEADER -> {
                    val records = parsePaxRecords(readContent(size))
                    records["path"]?.let { paxPath = it }
                    records["size"]?.let { paxSize = it.toLongOrNull() ?: throw IOException("Invalid tar size: $it") }
                }
                GNU_LONG_NAME -> gnuLongName = readContent(size).decodeToString().trimEnd(NUL)
                // Global extended headers and GNU long link names change nothing we read
                PAX_GLOBAL_HEADER, GNU_LONG_LINK -> Unit
                else -> {
                    val entrySize = paxSize ?: size
                    remaining = entrySize
                    padding = (BLOCK_SIZE - entrySize % BLOCK_SIZE) % BLOCK_SIZE
                    val name = paxPath ?: gnuLongName ?: headerName(header)
                    val type =
                        when {
                            typeFlag == DIRECTORY || (typeFlag == REGULAR_OLD && name.endsWith('/')) -> TarEntry.Type.DIRECTORY
                            typeFlag == REGULAR || typeFlag == REGULAR_OLD || typeFlag == CONTIGUOUS -> TarEntry.Type.FILE
                            else -> TarEntry.Type.OTHER
                        }
                    return TarEntry(name, entrySize, type)
                }
            }
        }
    }

    // The content of an extended header, which is small
    private fun readContent(size: Long): ByteArray {
        if (size > MAX_EXTENDED_HEADER) throw IOException("Tar extended header too large: $size")
        val bytes = source.readByteArray(size)
        remaining = 0L
        return bytes
    }

    private companion object {
        const val BLOCK_SIZE = 512
        const val ZERO: Byte = 0
        const val NUL = '\u0000'
        const val MAX_EXTENDED_HEADER = 1L shl 20

        const val NAME_OFFSET = 0
        const val NAME_LENGTH = 100
        const val SIZE_OFFSET = 124
        const val SIZE_LENGTH = 12
        const val CHECKSUM_OFFSET = 148
        const val CHECKSUM_LENGTH = 8
        const val TYPE_OFFSET = 156
        const val MAGIC_OFFSET = 257
        const val PREFIX_OFFSET = 345
        const val PREFIX_LENGTH = 155

        const val REGULAR = '0'
        const val REGULAR_OLD = NUL
        const val DIRECTORY = '5'
        const val CONTIGUOUS = '7'
        const val PAX_HEADER = 'x'
        const val PAX_GLOBAL_HEADER = 'g'
        const val GNU_LONG_NAME = 'L'
        const val GNU_LONG_LINK = 'K'

        /** The header's name, behind its ustar prefix when it has one. */
        fun headerName(header: ByteArray): String {
            val name = string(header, NAME_OFFSET, NAME_LENGTH)
            val isUstar = string(header, MAGIC_OFFSET, 5) == "ustar"
            val prefix = if (isUstar) string(header, PREFIX_OFFSET, PREFIX_LENGTH) else ""
            return if (prefix.isEmpty()) name else "$prefix/$name"
        }

        fun string(
            header: ByteArray,
            offset: Int,
            length: Int,
        ): String {
            var end = offset
            while (end < offset + length && header[end] != ZERO) end++
            return header.decodeToString(offset, end)
        }

        /** A numeric field: octal digits, or big-endian base-256 when its first byte has the high bit (GNU). */
        fun parseNumber(
            header: ByteArray,
            offset: Int,
            length: Int,
        ): Long {
            val first = header[offset].toInt()
            if (first and 0x80 != 0) {
                var value = (first and 0x7F).toLong()
                for (i in offset + 1 until offset + length) value = (value shl 8) or (header[i].toLong() and 0xFF)
                return value
            }
            val digits = string(header, offset, length).trim()
            if (digits.isEmpty()) return 0L
            return digits.toLongOrNull(8) ?: throw IOException("Invalid tar number: $digits")
        }

        /** The header's checksum: the sum of its bytes, the checksum field counted as spaces. */
        fun requireValidChecksum(header: ByteArray) {
            val expected = parseNumber(header, CHECKSUM_OFFSET, CHECKSUM_LENGTH)
            var sum = 0L
            for (i in header.indices) {
                val inChecksum = i >= CHECKSUM_OFFSET && i < CHECKSUM_OFFSET + CHECKSUM_LENGTH
                sum += if (inChecksum) ' '.code.toLong() else (header[i].toLong() and 0xFF)
            }
            if (sum != expected) throw IOException("Corrupted tar header (checksum)")
        }

        /** POSIX records: `<length> <key>=<value>\n`, the length counting the whole record in bytes. */
        fun parsePaxRecords(bytes: ByteArray): Map<String, String> {
            val records = HashMap<String, String>()
            var position = 0
            while (position < bytes.size) {
                var space = position
                while (space < bytes.size && bytes[space] != ' '.code.toByte()) space++
                val length = bytes.decodeToString(position, space).toIntOrNull() ?: break
                val end = position + length
                if (length <= 0 || end > bytes.size) break
                val record = bytes.decodeToString(space + 1, end - 1)
                val equals = record.indexOf('=')
                if (equals > 0) records[record.substring(0, equals)] = record.substring(equals + 1)
                position = end
            }
            return records
        }
    }
}
