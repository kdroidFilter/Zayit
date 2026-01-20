package io.github.kdroidfilter.seforimapp.earthwidget

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Android implementation using native Android graphics.
 *
 * Android Bitmap uses ARGB_8888 format which directly matches
 * our internal pixel format, so no byte reordering is needed.
 */

/**
 * Creates an ImageBitmap from ARGB pixel data.
 *
 * Uses Android's native Bitmap API with ARGB_8888 configuration.
 * This format directly matches our internal ARGB representation.
 *
 * @param argb Source pixels in ARGB format.
 * @param width Image width.
 * @param height Image height.
 * @return Compose ImageBitmap backed by Android Bitmap.
 */
internal actual fun imageBitmapFromArgb(
    argb: IntArray,
    width: Int,
    height: Int,
): ImageBitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(argb, 0, width, 0, 0, width, height)
    return bitmap.asImageBitmap()
}

/**
 * Extracts texture data from an ImageBitmap.
 *
 * Uses Android Bitmap's getPixels() which directly returns
 * ARGB integers matching our internal format.
 *
 * @param image Source Compose ImageBitmap.
 * @return EarthTexture with ARGB pixel data.
 */
internal actual fun earthTextureFromImageBitmap(image: ImageBitmap): EarthTexture {
    val bitmap = image.asAndroidBitmap()
    val width = bitmap.width
    val height = bitmap.height
    val argb = IntArray(width * height)

    bitmap.getPixels(argb, 0, width, 0, 0, width, height)

    return EarthTexture(argb = argb, width = width, height = height)
}
