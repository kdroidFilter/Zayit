package io.github.kdroidfilter.seforimapp.core.presentation.text

import kotlin.test.Test
import kotlin.test.assertEquals

class DiacriticsModeTest {
    private val verse = "נָשׁ֖וּב לְעַמֵּֽךְ׃"

    @Test
    fun all_keeps_the_text() {
        assertEquals(verse, DiacriticsMode.All.apply(verse))
    }

    @Test
    fun nikud_only_drops_teamim_and_meteg() {
        assertEquals("נָשׁוּב לְעַמֵּךְ׃", DiacriticsMode.NikudOnly.apply(verse))
    }

    @Test
    fun none_drops_nikud_and_teamim() {
        assertEquals("נשוב לעמך׃", DiacriticsMode.None.apply(verse))
    }

    @Test
    fun cycles_through_nikud_only_when_the_book_has_teamim() {
        assertEquals(DiacriticsMode.NikudOnly, DiacriticsMode.All.next(hasTeamim = true))
        assertEquals(DiacriticsMode.None, DiacriticsMode.NikudOnly.next(hasTeamim = true))
        assertEquals(DiacriticsMode.All, DiacriticsMode.None.next(hasTeamim = true))
    }

    @Test
    fun skips_nikud_only_when_the_book_has_no_teamim() {
        assertEquals(DiacriticsMode.None, DiacriticsMode.All.next(hasTeamim = false))
        assertEquals(DiacriticsMode.All, DiacriticsMode.None.next(hasTeamim = false))
    }

    @Test
    fun stripped_map_follows_the_mode() {
        val (text, map) = stripNikudTeamimWithMap(verse, DiacriticsMode.NikudOnly)
        assertEquals(DiacriticsMode.NikudOnly.apply(verse), text)
        text.indices.forEach { assertEquals(text[it], verse[map[it]]) }
    }
}
