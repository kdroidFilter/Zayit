package io.github.kdroidfilter.seforimapp.core.presentation.text

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownTextTest {
    @Test
    fun plain_text_is_unchanged() {
        assertEquals("הערה פשוטה", markdownToPlainText("הערה פשוטה"))
    }

    @Test
    fun blank_input_gives_empty_string() {
        assertEquals("", markdownToPlainText("   \n\n "))
    }

    @Test
    fun headings_lose_their_hashes() {
        assertEquals("כותרת", markdownToPlainText("## כותרת"))
        assertEquals("Title", markdownToPlainText("# Title #"))
        assertEquals("#hashtag", markdownToPlainText("#hashtag"))
    }

    @Test
    fun emphasis_markers_are_removed() {
        assertEquals("bold and italic and both", markdownToPlainText("**bold** and *italic* and ***both***"))
        assertEquals("strong and em", markdownToPlainText("__strong__ and _em_"))
        assertEquals("gone", markdownToPlainText("~~gone~~"))
    }

    @Test
    fun underscores_inside_words_are_kept() {
        assertEquals("snake_case_name", markdownToPlainText("snake_case_name"))
    }

    @Test
    fun lone_asterisks_are_kept() {
        assertEquals("2 * 3 * 4", markdownToPlainText("2 * 3 * 4"))
    }

    @Test
    fun inline_code_keeps_its_literal_content() {
        assertEquals("run a*b*c now", markdownToPlainText("run `a*b*c` now"))
        assertEquals("x `y` z", markdownToPlainText("x `` `y` `` z"))
    }

    @Test
    fun links_and_images_keep_their_text() {
        assertEquals("see the site", markdownToPlainText("see [the site](https://example.com)"))
        assertEquals("logo here", markdownToPlainText("![logo](a.png) here"))
        assertEquals("https://example.com", markdownToPlainText("<https://example.com>"))
    }

    @Test
    fun list_markers_and_task_boxes_are_removed() {
        assertEquals("one two three done", markdownToPlainText("- one\n* two\n1. three\n- [x] done"))
    }

    @Test
    fun blockquotes_are_removed() {
        assertEquals("quoted nested item", markdownToPlainText("> quoted\n> > nested\n> - item"))
    }

    @Test
    fun fences_and_rules_are_dropped_and_lines_joined() {
        assertEquals("before code after", markdownToPlainText("before\n```kotlin\ncode\n```\n---\nafter"))
    }

    @Test
    fun escapes_resolve_to_literal_characters() {
        assertEquals("*not italic* and # not heading", markdownToPlainText("\\*not italic\\* and \\# not heading"))
        assertEquals("a\\b", markdownToPlainText("a\\\\b"))
    }

    @Test
    fun hebrew_markdown_is_flattened() {
        assertEquals(
            "עיין רש\"י שם וראה גם תוספות",
            markdownToPlainText("### עיין **רש\"י** שם\n\n- וראה גם [תוספות](https://example.com)"),
        )
    }
}
