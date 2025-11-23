package io.github.kdroidfilter.seforimapp.features.bookcontent.utils

import io.github.kdroidfilter.seforimlibrary.core.models.Line

object NikudDetector {
    /**
     * Pure Nikkud:
     * - Vowels (05B0-05BB)
     * - Dagesh (05BC)
     * - Shin/Sin Dots (05C1-05C2)
     * - Upper/Lower Dots (05C4-05C5)
     * - Qamats Qatan (05C7)
     *
     * Excludes:
     * - Teamim/Cantillation (0591-05AF)
     * - Meteg (05BD)
     * - Maqaf (05BE)
     * - Rafe (05BF)
     * - Paseq (05C0)
     * - Sof Pasuq (05C3)
     */
    val NIKUD_PATTERN = Regex("[\u05B0-\u05BC\u05C1\u05C2\u05C4\u05C5\u05C7]")

    private const val SAMPLE_SIZE = 100

    fun hasNikud(lines: List<Line>): Boolean {
        val sampleLines = lines.take(SAMPLE_SIZE)
        return sampleLines.any { line ->
            NIKUD_PATTERN.containsMatchIn(line.content)
        }
    }

    suspend fun hasNikud(linesProvider: suspend (Int) -> List<Line>): Boolean {
        val sample = linesProvider(SAMPLE_SIZE)
        return sample.any { line ->
            NIKUD_PATTERN.containsMatchIn(line.content)
        }
    }

    fun stripNikud(text: String): String {
        return buildString(text.length) {
            for (char in text) {
                if (!isNikud(char)) {
                    append(char)
                }
            }
        }
    }

    private fun isNikud(c: Char): Boolean {
        val code = c.code
        return code in 0x05B0..0x05BC || // Vowels and Dagesh
                code == 0x05C1 || code == 0x05C2 || // Shin/Sin Dots
                code == 0x05C4 || code == 0x05C5 || // Upper/Lower Dots
                code == 0x05C7 // Qamats Qatan
    }
}
