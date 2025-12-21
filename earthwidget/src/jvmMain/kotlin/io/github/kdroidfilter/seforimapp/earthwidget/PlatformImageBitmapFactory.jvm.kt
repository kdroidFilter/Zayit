package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Bitmap

internal actual fun imageBitmapFromArgb(argb: IntArray, width: Int, height: Int): ImageBitmap {
    val bytes = ByteArray(width * height * 4)
    var byteIndex = 0
    for (color in argb) {
        bytes[byteIndex] = (color and 0xFF).toByte() // B
        bytes[byteIndex + 1] = ((color ushr 8) and 0xFF).toByte() // G
        bytes[byteIndex + 2] = ((color ushr 16) and 0xFF).toByte() // R
        bytes[byteIndex + 3] = ((color ushr 24) and 0xFF).toByte() // A
        byteIndex += 4
    }

    val bitmap = Bitmap()
    bitmap.allocN32Pixels(width, height, false)
    bitmap.installPixels(bytes)
    return bitmap.asComposeImageBitmap()
}

internal actual fun earthTextureFromImageBitmap(image: ImageBitmap): EarthTexture {
    val bitmap = image.asSkiaBitmap()
    val pixmap = requireNotNull(bitmap.peekPixels())
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
