package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LineConnectionsSnapshotTest {
    @Test
    fun `default snapshot has empty collections`() {
        val snapshot = LineConnectionsSnapshot()

        assertTrue(snapshot.commentatorGroups.isEmpty())
        assertTrue(snapshot.targumSources.isEmpty())
    }

    @Test
    fun `snapshot can be created with commentator groups`() {
        val groups =
            listOf(
                CommentatorGroup("Group1", listOf(CommentatorItem("Rashi", 1L))),
                CommentatorGroup("Group2", listOf(CommentatorItem("Tosafot", 2L))),
            )
        val snapshot = LineConnectionsSnapshot(commentatorGroups = groups)

        assertEquals(2, snapshot.commentatorGroups.size)
        assertEquals("Group1", snapshot.commentatorGroups[0].label)
    }

    @Test
    fun `snapshot can be created with targum sources`() {
        val targumSources =
            mapOf(
                "Onkelos" to 100L,
                "Yonatan" to 101L,
            )
        val snapshot = LineConnectionsSnapshot(targumSources = targumSources)

        assertEquals(2, snapshot.targumSources.size)
        assertEquals(100L, snapshot.targumSources["Onkelos"])
        assertEquals(101L, snapshot.targumSources["Yonatan"])
    }

    @Test
    fun `snapshot can be created with all data`() {
        val groups = listOf(CommentatorGroup("G1", emptyList()))
        val targum = mapOf("T1" to 1L)

        val snapshot =
            LineConnectionsSnapshot(
                commentatorGroups = groups,
                targumSources = targum,
            )

        assertEquals(1, snapshot.commentatorGroups.size)
        assertEquals(1, snapshot.targumSources.size)
    }

    @Test
    fun `snapshot copy works correctly`() {
        val original =
            LineConnectionsSnapshot(
                targumSources = mapOf("Original" to 1L),
            )
        val groups = listOf(CommentatorGroup("New", emptyList()))
        val modified = original.copy(commentatorGroups = groups)

        assertEquals(mapOf("Original" to 1L), modified.targumSources)
        assertEquals(groups, modified.commentatorGroups)
    }

    @Test
    fun `snapshot equals works correctly`() {
        val snapshot1 = LineConnectionsSnapshot(targumSources = mapOf("A" to 1L))
        val snapshot2 = LineConnectionsSnapshot(targumSources = mapOf("A" to 1L))

        assertEquals(snapshot1, snapshot2)
    }
}
