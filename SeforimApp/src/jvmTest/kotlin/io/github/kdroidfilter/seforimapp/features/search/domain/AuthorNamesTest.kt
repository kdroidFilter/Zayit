package io.github.kdroidfilter.seforimapp.features.search.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AuthorNamesTest {
    @Test
    fun bareNamesGetHaRav() {
        assertEquals("הרב יוסף קארו", AuthorNames.display("יוסף קארו"))
        assertEquals("הרב שרירא גאון", AuthorNames.display("שרירא גאון"))
    }

    @Test
    fun acronymsStayAsTheyAre() {
        assertEquals("רמב\"ם", AuthorNames.display("רמב\"ם"))
        assertEquals("רש״י", AuthorNames.display("רש״י"))
    }

    @Test
    fun namesWithATitleStayAsTheyAre() {
        assertEquals("רבי ישראל מאיר הכהן", AuthorNames.display("רבי ישראל מאיר הכהן"))
        assertEquals("חכם יצחק עטייה", AuthorNames.display("חכם יצחק עטייה"))
        assertEquals("רב סעדיה גאון", AuthorNames.display("רב סעדיה גאון"))
    }

    @Test
    fun aQuoteLaterInTheNameIsNoAcronym() {
        assertEquals("הרב יוסף בן משה באב\"ד", AuthorNames.display("יוסף בן משה באב\"ד"))
        assertEquals("מהר\"ם פדובה", AuthorNames.display("מהר\"ם פדובה"))
    }

    @Test
    fun nikudBidiMarksAndSpacesAreDropped() {
        assertEquals("הרב עקיבא איגר", AuthorNames.display("עֲקִיבָא  אֵיגֶר"))
        assertEquals("הרב ישראל ליפשיץ", AuthorNames.display("ישראל ליפשיץ\u200E\u200E"))
    }

    @Test
    fun catalogNamesAreReadInOrder() {
        assertEquals("הרב יחזקאל בן הילל אריה ליב ליבשיץ", AuthorNames.display("ליבשיץ, יחזקאל בן הילל אריה ליב"))
        assertEquals("הרב דוד בן משה הכהן סקלי", AuthorNames.display("סקלי, דוד בן משה הכהן"))
    }

    @Test
    fun rabbinicFirstNamesAreNotTitles() {
        // רבקה, רבן... must not be read as רב
        assertEquals("הרב רבינוביץ", AuthorNames.display("רבינוביץ"))
    }

    @Test
    fun queriesDropTheDisplayHonorific() {
        assertEquals("יוסף קארו", AuthorNames.withoutHonorific("הרב יוסף קארו"))
        assertEquals("הרבני", AuthorNames.withoutHonorific("הרבני"))
        assertEquals("רמב\"ם", AuthorNames.withoutHonorific("רמב\"ם"))
    }

    @Test
    fun aQueryTypedAsAnAliasNamesIt() {
        val aliases = listOf("חפץ חיים")
        assertEquals("חפץ חיים", AuthorNames.matchedAlias("חפץ חיים", "רבי ישראל מאיר הכהן", aliases))
        assertEquals("חפץ חיים", AuthorNames.matchedAlias("חפץ ח", "רבי ישראל מאיר הכהן", aliases))
        assertEquals(null, AuthorNames.matchedAlias("ישראל מאיר", "רבי ישראל מאיר הכהן", aliases))
        assertEquals(null, AuthorNames.matchedAlias("הרב ישראל", "ישראל מאיר הכהן", aliases))
    }
}
