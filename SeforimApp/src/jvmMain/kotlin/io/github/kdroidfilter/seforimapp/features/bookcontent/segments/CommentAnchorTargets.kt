package io.github.kdroidfilter.seforimapp.features.bookcontent.segments

import io.github.kdroidfilter.seforim.htmlparser.CommentAnchor
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.util.concurrent.ConcurrentHashMap

/**
 * Where the commentary anchors of one base book lead: for each of its lines, each anchor (commentator as the anchor
 * names it, number) with the commentary lines it explains. Resolved offline against the DB (`CommentAnchorTargetsGen`)
 * and bundled under [RESOURCE_DIR], until the DB carries it.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
class BookAnchorTargets(
    // The commentators as the anchors name them, and the commentary book each leads to
    @ProtoNumber(1) val commentators: List<String> = emptyList(),
    @ProtoNumber(2) val commentatorBooks: List<Long> = emptyList(),
    @ProtoNumber(3) val lines: List<LineAnchorTargets> = emptyList(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
class LineAnchorTargets(
    @ProtoNumber(1) val lineId: Long,
    @ProtoNumber(2) val anchors: List<AnchorTargets> = emptyList(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
class AnchorTargets(
    // Index in BookAnchorTargets.commentators
    @ProtoNumber(1) val commentator: Int,
    @ProtoNumber(2) val order: Int,
    @ProtoNumber(3) val targetLineIds: List<Long> = emptyList(),
)

/** One anchor's commentary lines, and the commentary book they belong to. */
class AnchorTarget(
    val bookId: Long,
    val lineIds: List<Long>,
)

/** The resolved anchors of one base book, by line. */
class CommentAnchorIndex(
    private val byLine: Map<Long, Map<CommentAnchor, AnchorTarget>>,
    /** The commentators named by this book's resolved anchors, for the parser to keep them only. */
    val commentators: Set<String>,
) {
    fun of(lineId: Long): Map<CommentAnchor, AnchorTarget> = byLine[lineId].orEmpty()

    companion object {
        val EMPTY = CommentAnchorIndex(emptyMap(), emptySet())
    }
}

internal const val RESOURCE_DIR = "/comment-anchors"

private val indexes = ConcurrentHashMap<Long, CommentAnchorIndex>()

/** The resolved anchors of [bookId], read once from its bundled file; [CommentAnchorIndex.EMPTY] for a book without. */
@OptIn(ExperimentalSerializationApi::class)
fun commentAnchorIndex(bookId: Long): CommentAnchorIndex =
    indexes.getOrPut(bookId) {
        val bytes =
            BookAnchorTargets::class.java.getResourceAsStream("$RESOURCE_DIR/$bookId.pb")?.use { it.readBytes() }
                ?: return@getOrPut CommentAnchorIndex.EMPTY
        val table = ProtoBuf.decodeFromByteArray(BookAnchorTargets.serializer(), bytes)
        CommentAnchorIndex(
            byLine =
                table.lines.associate { line ->
                    line.lineId to
                        line.anchors.associate {
                            CommentAnchor(table.commentators[it.commentator], it.order) to
                                AnchorTarget(table.commentatorBooks[it.commentator], it.targetLineIds)
                        }
                },
            commentators = table.commentators.toSet(),
        )
    }
