package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import okio.Buffer

/** Writes tar archives block by block, as a packager would. */
internal class TarWriter {
    val buffer = Buffer()

    fun entry(
        name: String,
        content: ByteArray = ByteArray(0),
        type: Char = '0',
        prefix: String = "",
        sizeField: ByteArray? = null,
    ) {
        val header = ByteArray(512)
        name.encodeToByteArray().copyInto(header, 0)
        octal(content.size.toLong(), 12).copyInto(header, 124)
        sizeField?.copyInto(header, 124)
        header[156] = type.code.toByte()
        "ustar\u000000".encodeToByteArray().copyInto(header, 257)
        prefix.encodeToByteArray().copyInto(header, 345)
        " ".repeat(8).encodeToByteArray().copyInto(header, 148)
        val checksum = header.sumOf { it.toLong() and 0xFF }
        octal(checksum, 7).copyInto(header, 148)
        buffer.write(header)
        buffer.write(content)
        buffer.write(ByteArray(((512 - content.size % 512) % 512)))
    }

    fun pax(vararg records: Pair<String, String>) {
        val body =
            records.joinToString("") { (key, value) ->
                val rest = " $key=$value\n"
                var length = rest.length + 1
                while ("$length$rest".encodeToByteArray().size != length) length++
                "$length$rest"
            }
        entry("PaxHeader", body.encodeToByteArray(), type = 'x')
    }

    fun end() {
        buffer.write(ByteArray(1024))
    }

    private fun octal(
        value: Long,
        digits: Int,
    ): ByteArray = (value.toString(8).padStart(digits - 1, '0') + "\u0000").encodeToByteArray()
}
