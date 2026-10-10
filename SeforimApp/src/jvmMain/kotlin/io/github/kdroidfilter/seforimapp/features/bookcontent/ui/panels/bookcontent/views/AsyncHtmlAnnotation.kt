package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.AnnotatedString
import io.github.kdroidfilter.seforim.htmlparser.HtmlImageContentBuilder
import io.github.kdroidfilter.seforim.htmlparser.SkiaHtmlImageBuilder
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private const val PLACEHOLDER_CHARS_PER_LINE = 72
private const val MAX_PLACEHOLDER_LINES = 32

// Up to this length the HTML is annotated in composition: parsing costs ~30-100 µs per 1000 chars, far
// less than the frame a background build and its placeholder cost. Longer texts are built off the main thread.
private const val SYNC_ANNOTATION_MAX_CHARS = 8_000

@Stable
internal data class LineAnnotation(
    val annotated: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
)

internal data class HtmlAnnotationCacheKey(
    val itemId: Long,
    val contentHash: Int,
    val contentLength: Int,
    val baseTextSize: Float,
    val boldScale: Float,
    val footnoteMarkerColor: Color,
    val invertImages: Boolean,
)

@Stable
internal class StableAnnotatedCache(
    // Not snapshot state: composition writes into it (see rememberAsyncHtmlAnnotation), and a background
    // prefetcher may too.
    private val cache: MutableMap<HtmlAnnotationCacheKey, LineAnnotation> = ConcurrentHashMap(),
) {
    fun get(key: HtmlAnnotationCacheKey): LineAnnotation? = cache[key]

    fun put(
        key: HtmlAnnotationCacheKey,
        value: LineAnnotation,
    ) {
        cache[key] = value
    }
}

@Composable
internal fun rememberAsyncHtmlAnnotation(
    cacheKey: HtmlAnnotationCacheKey,
    html: String,
    baseTextSize: Float,
    boldScale: Float,
    footnoteMarkerColor: Color,
    imageColorFilter: @Composable () -> ColorFilter?,
    annotatedCache: StableAnnotatedCache,
): LineAnnotation? {
    val imageContentBuilder = remember(imageColorFilter) { SkiaHtmlImageBuilder.build(imageColorFilter) }
    val cached =
        annotatedCache.get(cacheKey)
            ?: if (html.length <= SYNC_ANNOTATION_MAX_CHARS) {
                buildLineAnnotation(html, baseTextSize, boldScale, footnoteMarkerColor, imageContentBuilder)
                    .also { annotatedCache.put(cacheKey, it) }
            } else {
                null
            }
    val annotation by produceState<LineAnnotation?>(
        initialValue = cached,
        cacheKey,
        html,
        imageContentBuilder,
        annotatedCache,
    ) {
        value = cached
        if (cached != null) return@produceState

        val built =
            withContext(Dispatchers.Default) {
                buildLineAnnotation(
                    html = html,
                    baseTextSize = baseTextSize,
                    boldScale = boldScale,
                    footnoteMarkerColor = footnoteMarkerColor,
                    imageContentBuilder = imageContentBuilder,
                )
            }
        annotatedCache.put(cacheKey, built)
        value = built
    }
    return annotation
}

/**
 * Builds the cache key for an HTML line annotation. Shared by [LineItem] composition and the
 * off-screen prefetcher so both produce byte-identical keys (a mismatch would defeat the cache).
 */
internal fun htmlAnnotationCacheKey(
    lineId: Long,
    processedContent: String,
    baseTextSize: Float,
    boldScale: Float,
    footnoteMarkerColor: Color,
    invertImages: Boolean,
): HtmlAnnotationCacheKey =
    HtmlAnnotationCacheKey(
        itemId = lineId,
        contentHash = processedContent.hashCode(),
        contentLength = processedContent.length,
        baseTextSize = baseTextSize,
        boldScale = boldScale,
        footnoteMarkerColor = footnoteMarkerColor,
        invertImages = invertImages,
    )

internal fun htmlAnnotationPlaceholderText(contentLength: Int): String {
    val lineCount =
        ((contentLength + PLACEHOLDER_CHARS_PER_LINE - 1) / PLACEHOLDER_CHARS_PER_LINE)
            .coerceIn(1, MAX_PLACEHOLDER_LINES)
    return buildString {
        repeat(lineCount) { index ->
            append(' ')
            if (index < lineCount - 1) append('\n')
        }
    }
}

internal fun buildLineAnnotation(
    html: String,
    baseTextSize: Float,
    boldScale: Float,
    footnoteMarkerColor: Color,
    imageContentBuilder: HtmlImageContentBuilder,
): LineAnnotation {
    val inline = mutableMapOf<String, InlineTextContent>()
    val annotated =
        buildAnnotatedFromHtml(
            html,
            baseTextSize,
            boldScale = if (boldScale < 1f) 1f else boldScale,
            footnoteMarkerColor = footnoteMarkerColor,
            inlineContent = inline,
            imageContentBuilder = imageContentBuilder,
        )
    return LineAnnotation(annotated, inline.toMap())
}
