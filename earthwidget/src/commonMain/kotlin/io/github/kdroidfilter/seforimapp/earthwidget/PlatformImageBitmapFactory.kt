package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Creates an ImageBitmap from an ARGB pixel array.
 *
 * Platform implementations convert the ARGB array to the native
 * bitmap format (BGRA for Skia/JVM, ARGB for Android).
 *
 * @param argb Pixel data in ARGB format (alpha in high byte).
 * @param width Bitmap width in pixels.
 * @param height Bitmap height in pixels.
 * @return Compose ImageBitmap ready for rendering.
 */
internal expect fun imageBitmapFromArgb(argb: IntArray, width: Int, height: Int): ImageBitmap

/**
 * Extracts texture data from an ImageBitmap for sphere rendering.
 *
 * Platform implementations read pixel data from the native bitmap
 * format and convert to ARGB for the rendering engine.
 *
 * @param image Source ImageBitmap to extract pixels from.
 * @return EarthTexture containing ARGB pixel data.
 */
internal expect fun earthTextureFromImageBitmap(image: ImageBitmap): EarthTexture
