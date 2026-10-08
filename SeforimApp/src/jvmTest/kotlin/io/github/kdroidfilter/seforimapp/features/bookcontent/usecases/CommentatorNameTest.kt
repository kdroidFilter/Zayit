package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import kotlin.test.Test
import kotlin.test.assertEquals

class CommentatorNameTest {
    @Test
    fun `drops the current book's title`() {
        assertEquals("רש״י", sanitizeCommentatorName("רש״י על בראשית", "בראשית"))
        assertEquals("רמב״ן", sanitizeCommentatorName("רמב״ן על ספר בראשית", "בראשית"))
        assertEquals("מלבי״ם", sanitizeCommentatorName("מלבי״ם על שיר השירים", "שיר השירים"))
    }

    @Test
    fun `keeps what follows the title`() {
        assertEquals("אבן עזרא; פירוש הקצר", sanitizeCommentatorName("אבן עזרא על שמות; פירוש הקצר", "שמות"))
        assertEquals("אבן עזרא (מהדורה)", sanitizeCommentatorName("אבן עזרא על שמות (מהדורה)", "שמות"))
        assertEquals("רש״י", sanitizeCommentatorName("רש״י על שמות;", "שמות"))
    }

    @Test
    fun `leaves a longer title, another book, alone`() {
        assertEquals("מתנות כהונה על שמות רבה", sanitizeCommentatorName("מתנות כהונה על שמות רבה", "שמות"))
        assertEquals("רש״י על שמותיהם", sanitizeCommentatorName("רש״י על שמותיהם", "שמות"))
    }
}
