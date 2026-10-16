package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforim.semantic.DenseHit
import io.github.kdroidfilter.seforim.semantic.VectorIndex
import io.github.kdroidfilter.seforimlibrary.search.index.BoolBuilder
import io.github.kdroidfilter.seforimlibrary.search.index.IndexQuery
import io.github.kdroidfilter.seforimlibrary.search.index.KnnRequest
import io.github.kdroidfilter.seforimlibrary.search.index.LuceneIndex
import io.github.kdroidfilter.seforimlibrary.search.index.Occur
import io.github.kdroidfilter.seforimlibrary.search.index.VectorEncoding

/**
 * The vectors of the app's fused Lucene index for the semantic search: its KNN runs through [index], in process on the
 * JVM and Android, in the Java runtime of the iOS app. Not closed with the semantic search: the lexical one shares it.
 */
internal class LuceneVectorIndex(
    private val index: LuceneIndex,
) : VectorIndex {
    override fun encoding(field: String): VectorIndex.Encoding? =
        when (index.vectorEncoding(field)) {
            VectorEncoding.BYTE -> VectorIndex.Encoding.BYTE
            VectorEncoding.FLOAT32 -> VectorIndex.Encoding.FLOAT32
            null -> null
        }

    override fun knn(
        field: String,
        k: Int,
        floatVector: FloatArray?,
        byteVector: ByteArray?,
        baseBookOnly: Boolean,
        bookIds: Collection<Long>?,
        oversample: Int,
    ): List<DenseHit> =
        index
            .knn(
                KnnRequest(
                    field = field,
                    k = k,
                    fields = setOf(FIELD_LINE_ID, FIELD_BOOK_ID),
                    floatVector = floatVector,
                    byteVector = byteVector,
                    filter = filter(baseBookOnly, bookIds),
                    oversample = oversample,
                ),
            ).map { DenseHit(lineId = it.longs.getValue(FIELD_LINE_ID), bookId = it.longs.getValue(FIELD_BOOK_ID), score = it.score) }

    // Base books and specific books, as a pre-filter: the KNN only considers eligible lines
    private fun filter(
        baseBookOnly: Boolean,
        bookIds: Collection<Long>?,
    ): IndexQuery? {
        if (!baseBookOnly && bookIds.isNullOrEmpty()) return null
        val builder = BoolBuilder()
        if (baseBookOnly) builder.add(IndexQuery.IntExact(FIELD_IS_BASE_BOOK, 1), Occur.FILTER)
        if (!bookIds.isNullOrEmpty()) builder.add(IndexQuery.IntSet(FIELD_BOOK_ID, bookIds.map { it.toInt() }.toIntArray()), Occur.FILTER)
        return builder.build()
    }

    override fun close() = Unit

    private companion object {
        const val FIELD_LINE_ID = "line_id"
        const val FIELD_BOOK_ID = "book_id"
        const val FIELD_IS_BASE_BOOK = "is_base_book"
    }
}
