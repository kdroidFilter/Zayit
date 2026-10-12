package io.github.kdroidfilter.seforimapp.framework.search

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypedAsWordsOfTest {
    // The index is opened lazily: no lookup here
    private val lookup = LuceneLookupSearchService(Paths.get("unused"))

    private fun matches(
        typed: String,
        name: String,
    ) = lookup.typedAsWordsOf(typed, name)

    @Test
    fun eachWordBeginsAWord() {
        assertTrue(matches("בבא ב", "בבא בתרא"))
        assertTrue(matches("חפץ ח", "חפץ חיים"))
        // abbreviated, reordered or apart, as the index matches them
        assertTrue(matches("משנה תורה הל' מכירה", "משנה תורה, הלכות מכירה"))
        assertTrue(matches("סוכה ירושלמי", "תלמוד ירושלמי סוכה"))
        assertTrue(matches("רשי בראשית", "רש\"י על בראשית"))
    }

    @Test
    fun noWordReadTwice() {
        // the index matched these, ב on the same word as בבא
        assertFalse(matches("בבא ב", "תוספתא בבא קמא"))
        assertFalse(matches("בבא ב", "שלמה טנא (טנוביצקי) רב בבאר שבע (תרע\"ד- תשמ\"ו)"))
        // a short word does not take the only word the longer one begins
        assertTrue(matches("ב בבא", "בבא בתרא"))
    }

    @Test
    fun comparedAsTheIndexNormalizesThem() {
        // quotes, dots, final letters
        assertTrue(matches("שו\"ע או\"ח", "שוע אוח"))
        assertTrue(matches("פ.ע.", "פע"))
        assertTrue(matches("חו\"ם", "שו\"ע חו\"מ"))
    }
}
