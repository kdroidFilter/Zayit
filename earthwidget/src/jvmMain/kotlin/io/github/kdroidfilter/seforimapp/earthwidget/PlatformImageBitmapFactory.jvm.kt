package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Bitmap

/**
 * JVM/Desktop implementation using Skia graphics library.
 *
 * Skia uses BGRA byte order (native little-endian), so we must
 * convert from ARGB integer format during bitmap creation.
 */

/**
 * Creates an ImageBitmap from ARGB pixel data.
 *
 * Converts ARGB integers to BGRA byte array for Skia.
 * Each pixel is stored as 4 bytes: Blue, Green, Red, Alpha.
 *
 * @param argb Source pixels in ARGB format.
 * @param width Image width.
 * @param height Image height.
 * @return Compose ImageBitmap backed by Skia.
 */
internal actual fun imageBitmapFromArgb(argb: IntArray, width: Int, height: Int): ImageBitmap {
    val pixelCount = width * height
    val bytes = ByteArray(pixelCount * 4)

    var byteIndex = 0
    for (color in argb) {
        // Convert ARGB to BGRA byte order
        bytes[byteIndex] = (color and 0xFF).toByte()         // Blue
        bytes[byteIndex + 1] = ((color ushr 8) and 0xFF).toByte()  // Green
        bytes[byteIndex + 2] = ((color ushr 16) and 0xFF).toByte() // Red
        bytes[byteIndex + 3] = ((color ushr 24) and 0xFF).toByte() // Alpha
        byteIndex += 4
    }

    val bitmap = Bitmap().apply {
        allocN32Pixels(width, height, false)
        installPixels(bytes)
    }

    return bitmap.asComposeImageBitmap()
}

/**
 * Extracts texture data from an ImageBitmap.
 *
 * Reads BGRA bytes from Skia pixmap and converts to ARGB integers.
 * Handles row stride (rowBytes) which may include padding.
 *
 * @param image Source Compose ImageBitmap.
 * @return EarthTexture with ARGB pixel data.
 */
internal actual fun earthTextureFromImageBitmap(image: ImageBitmap): EarthTexture {
    val bitmap = image.asSkiaBitmap()
    val pixmap = requireNotNull(bitmap.peekPixels()) {
        "Failed to access pixel data from Skia bitmap"
    }

    val bytes = pixmap.buffer.bytes
    val width = pixmap.info.width
    val height = pixmap.info.height
    val rowBytes = pixmap.rowBytes

    val argb = IntArray(width * height)
    var outIndex = 0
    var rowStart = 0

    for (y in 0 until height) {
        var byteIndex = rowStart
        for (x in 0 until width) {
            // Convert BGRA to ARGB
            val b = bytes[byteIndex].toInt() and 0xFF
            val g = bytes[byteIndex + 1].toInt() and 0xFF
            val r = bytes[byteIndex + 2].toInt() and 0xFF
            val a = bytes[byteIndex + 3].toInt() and 0xFF

            argb[outIndex++] = (a shl 24) or (r shl 16) or (g shl 8) or b
            byteIndex += 4
        }
        rowStart += rowBytes
    }

    return EarthTexture(argb = argb, width = width, height = height)
}
