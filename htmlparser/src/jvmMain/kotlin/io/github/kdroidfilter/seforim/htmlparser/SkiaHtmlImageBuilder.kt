package io.github.kdroidfilter.seforim.htmlparser

import androidx.compose.foundation.Image
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.sp
import org.jetbrains.skia.Image as SkiaImage

/**
 * Decodes data-URI image bytes via Skia and exposes them as inline text content for
 * [buildAnnotatedFromHtml].
 *
 * The placeholder uses the image's intrinsic pixel dimensions mapped 1:1 to [sp] so the rendered
 * image matches the source size (and scales proportionally with the user's text-size setting).
 * Explicit `width`/`height` attributes on the originating `<img>` tag override the intrinsic size.
 *
 * Pass [imageColorFilter] to tint or invert images at composition time (e.g. invert black-on-white
 * glyphs when the UI is in dark mode). The lambda is re-invoked on every recomposition of the
 * text, so it can read theme [androidx.compose.runtime.CompositionLocal]s.
 *
 * A bounded LRU cache keyed by content avoids re-decoding the same image on recomposition or when
 * the same HTML is parsed again (each parse yields a new byte array).
 */
object SkiaHtmlImageBuilder {
    /** 4x5 color matrix that inverts RGB while keeping alpha intact. */
    val InvertColorFilter: ColorFilter =
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f,
                    0f,
                    0f,
                    0f,
                    255f,
                    0f,
                    -1f,
                    0f,
                    0f,
                    255f,
                    0f,
                    0f,
                    -1f,
                    0f,
                    255f,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f,
                ),
            ),
        )

    private const val MAX_CACHED_BITMAPS = 64

    /** Content-based key: [ByteArray] equality is by identity. */
    private class ImageKey(
        val bytes: ByteArray,
    ) {
        private val hash = bytes.contentHashCode()

        override fun equals(other: Any?): Boolean = other is ImageKey && other.hash == hash && other.bytes.contentEquals(bytes)

        override fun hashCode(): Int = hash
    }

    private val bitmapCache =
        object : LinkedHashMap<ImageKey, ImageBitmap>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ImageKey, ImageBitmap>): Boolean = size > MAX_CACHED_BITMAPS
        }

    private fun decode(bytes: ByteArray): ImageBitmap? {
        val key = ImageKey(bytes)
        synchronized(bitmapCache) { bitmapCache[key] }?.let { return it }
        val bitmap = runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull() ?: return null
        synchronized(bitmapCache) { bitmapCache[key] = bitmap }
        return bitmap
    }

    fun build(imageColorFilter: @Composable () -> ColorFilter? = { null }): HtmlImageContentBuilder =
        builder@{ element, _ ->
            val bytes = element.imageBytes ?: return@builder null
            val bitmap = decode(bytes) ?: return@builder null

            val widthPx = (element.imageWidth ?: bitmap.width).coerceAtLeast(1)
            val heightPx = (element.imageHeight ?: bitmap.height).coerceAtLeast(1)

            InlineTextContent(
                placeholder =
                    Placeholder(
                        width = widthPx.sp,
                        height = heightPx.sp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                    ),
            ) { _ ->
                Image(
                    painter = BitmapPainter(bitmap),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = imageColorFilter(),
                )
            }
        }
}
