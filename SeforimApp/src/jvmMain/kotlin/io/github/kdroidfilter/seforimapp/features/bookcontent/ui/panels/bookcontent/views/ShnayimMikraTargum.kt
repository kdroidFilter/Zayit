package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

private const val TARGUM_SCALE = 0.9f

private val CHUMASH_TITLES = setOf("בראשית", "שמות", "ויקרא", "במדבר", "דברים")

/** The five books of the Chumash: the only ones shnayim mikra reads. */
internal val Book.isChumash get() = title in CHUMASH_TITLES

private val SPACES = Regex("""\s+""")

private fun normalized(ref: String) = ref.replace(SPACES, " ").trim()

/**
 * The book's first default targum (Onkelos on the Chumash), verse by verse: its lines by the book's references
 * ("בראשית א, א" for "תרגום אונקלוס על בראשית א, א"). Loaded whole when shnayim mikra is on, so verses never wait for
 * theirs as they scroll in; empty while loading, off, or without a targum.
 */
@Composable
internal fun rememberShnayimMikraTargum(
    bookId: Long,
    enabled: Boolean,
): Map<String, String> {
    val repository = LocalAppGraph.current.repository
    val targum by produceState(emptyMap<String, String>(), bookId, enabled) {
        if (!enabled) {
            value = emptyMap()
            return@produceState
        }
        val book = repository.getBook(bookId) ?: return@produceState
        val targumBook = repository.getDefaultTargumIdsForBook(bookId).firstOrNull()?.let { repository.getBook(it) } ?: return@produceState
        val prefix = "${book.title} "
        // ponytail: the targum's every line (≈1,500 for a Chumash book), mapped once per tab
        value =
            repository
                .getLines(targumBook.id, 0, targumBook.totalLines - 1)
                .mapNotNull { line ->
                    val ref = line.heRef?.let(::normalized) ?: return@mapNotNull null
                    val start = ref.indexOf(prefix).takeIf { it >= 0 } ?: return@mapNotNull null
                    ref.substring(start) to line.content
                }.toMap()
    }
    return targum
}

internal fun Map<String, String>.targumOf(heRef: String?) = heRef?.let { get(normalized(it)) }

/** A verse's targum, under the verse read twice. */
@Composable
internal fun ShnayimMikraTargum(
    html: String,
    fontFamily: FontFamily,
    textSize: Float,
    lineHeight: Float,
    diacritics: DiacriticsMode,
) {
    val size = textSize * TARGUM_SCALE
    val annotated =
        remember(html, size, diacritics) {
            buildAnnotatedFromHtml(diacritics.apply(html), size)
        }
    Text(
        annotated,
        fontFamily = fontFamily,
        fontSize = size.sp,
        lineHeight = (size * lineHeight).sp,
        color = JewelTheme.globalColors.text.info,
        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
    )
}
