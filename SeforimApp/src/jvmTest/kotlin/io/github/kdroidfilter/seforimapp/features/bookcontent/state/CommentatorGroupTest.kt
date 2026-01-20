package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentatorGroupTest {
    @Test
    fun `CommentatorItem can be created`() {
        val item = CommentatorItem(name = "Rashi", bookId = 123L)

        assertEquals("Rashi", item.name)
        assertEquals(123L, item.bookId)
    }

    @Test
    fun `CommentatorItem equals works correctly`() {
        val item1 = CommentatorItem("Rashi", 123L)
        val item2 = CommentatorItem("Rashi", 123L)
        val item3 = CommentatorItem("Tosafot", 124L)

        assertEquals(item1, item2)
        assertTrue(item1 != item3)
    }

    @Test
    fun `CommentatorGroup can be created with empty list`() {
        val group = CommentatorGroup(label = "Empty Group", commentators = emptyList())

        assertEquals("Empty Group", group.label)
        assertTrue(group.commentators.isEmpty())
    }

    @Test
    fun `CommentatorGroup can be created with commentators`() {
        val commentators =
            listOf(
                CommentatorItem("Rashi", 1L),
                CommentatorItem("Tosafot", 2L),
                CommentatorItem("Ramban", 3L),
            )
        val group = CommentatorGroup(label = "Rishonim", commentators = commentators)

        assertEquals("Rishonim", group.label)
        assertEquals(3, group.commentators.size)
        assertEquals("Rashi", group.commentators[0].name)
        assertEquals("Tosafot", group.commentators[1].name)
        assertEquals("Ramban", group.commentators[2].name)
    }

    @Test
    fun `CommentatorGroup copy works correctly`() {
        val original =
            CommentatorGroup(
                label = "Original",
                commentators = listOf(CommentatorItem("Test", 1L)),
            )
        val modified = original.copy(label = "Modified")

        assertEquals("Modified", modified.label)
        assertEquals(original.commentators, modified.commentators)
    }

    @Test
    fun `CommentatorGroup equals works correctly`() {
        val commentators = listOf(CommentatorItem("Rashi", 1L))
        val group1 = CommentatorGroup("Group", commentators)
        val group2 = CommentatorGroup("Group", commentators)

        assertEquals(group1, group2)
    }
}
