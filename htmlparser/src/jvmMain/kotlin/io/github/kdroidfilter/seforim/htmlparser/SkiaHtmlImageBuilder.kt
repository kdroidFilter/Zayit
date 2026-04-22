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
import java.util.concurrent.ConcurrentHashMap
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
 * A small identity-keyed cache avoids re-decoding the same byte array on recomposition.
 */
object SkiaHtmlImageBuilder {
    /** 4x5 color matrix that inverts RGB while keeping alpha intact. */
    val InvertColorFilter: ColorFilter =
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )

    private val bitmapCache = ConcurrentHashMap<ByteArray, ImageBitmap>()

    fun build(
        imageColorFilter: @Composable () -> ColorFilter? = { null },
    ): HtmlImageContentBuilder =
        builder@{ element, _ ->
            val bytes = element.imageBytes ?: return@builder null
            val bitmap =
                bitmapCache.getOrPut(bytes) {
                    runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }
                        .getOrNull() ?: return@builder null
                }

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
