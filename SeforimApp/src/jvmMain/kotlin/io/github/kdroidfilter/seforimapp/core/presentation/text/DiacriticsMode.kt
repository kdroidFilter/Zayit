package io.github.kdroidfilter.seforimapp.core.presentation.text

/** Which Hebrew diacritics the reader displays; the diacritics button cycles through them. */
enum class DiacriticsMode {
    /** Nikud and teamim. */
    All,

    /** Nikud only: teamim are hidden, and so is the meteg, which mostly marks the silluq. */
    NikudOnly,

    /** Neither nikud nor teamim. */
    None,
    ;

    /** The mode the button moves to; [NikudOnly] is skipped when [hasTeamim] is false, as it would show no change. */
    fun next(hasTeamim: Boolean = true): DiacriticsMode {
        val next = entries[(ordinal + 1) % entries.size]
        return if (next == NikudOnly && !hasTeamim) next.next() else next
    }

    /** True when this mode drops [c] from the displayed text. */
    fun hides(c: Char): Boolean =
        when (this) {
            All -> false
            NikudOnly -> isTeamim(c) || c == METEG
            None -> isNikudOrTeamim(c)
        }

    /** [text] as displayed in this mode. */
    fun apply(text: String): String = if (this == All) text else text.filterNot(::hides)
}

private const val METEG = '\u05BD'

private fun isTeamim(c: Char): Boolean = c.code in 0x0591..0x05AF

/** True for nikud (vowel points, meteg included) and ta'amim (cantillation) characters. */
internal fun isNikudOrTeamim(c: Char): Boolean =
    isTeamim(c) ||
        (c.code in 0x05B0..0x05BD) ||
        (c == '\u05C1') ||
        (c == '\u05C2') ||
        (c == '\u05C7')
