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

private const val PLACEHOLDER_CHARS_PER_LINE = 72
private const val MAX_PLACEHOLDER_LINES = 32

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
    private val cache: MutableMap<HtmlAnnotationCacheKey, LineAnnotation>,
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
    val cached = annotatedCache.get(cacheKey)
    val imageContentBuilder = remember(imageColorFilter) { SkiaHtmlImageBuilder.build(imageColorFilter) }
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
