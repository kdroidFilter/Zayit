package io.github.kdroidfilter.seforimapp.features.bookcontent

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry

/**
 * Factory methods for creating test data in BookContent tests.
 */
object TestFactories {
    fun createLine(
        id: Long = 1L,
        bookId: Long = 100L,
        lineIndex: Int = 0,
        content: String = "Test content",
        heRef: String? = null,
    ): Line =
        Line(
            id = id,
            bookId = bookId,
            lineIndex = lineIndex,
            content = content,
            heRef = heRef,
        )

    fun createTocEntry(
        id: Long = 1L,
        bookId: Long = 100L,
        parentId: Long? = null,
        textId: Long? = null,
        text: String = "Test TOC Entry",
        level: Int = 0,
        lineId: Long? = null,
        isLastChild: Boolean = false,
        hasChildren: Boolean = false,
    ): TocEntry =
        TocEntry(
            id = id,
            bookId = bookId,
            parentId = parentId,
            textId = textId,
            text = text,
            level = level,
            lineId = lineId,
            isLastChild = isLastChild,
            hasChildren = hasChildren,
        )

    fun createBook(
        id: Long = 100L,
        categoryId: Long = 1L,
        sourceId: Long = 1L,
        title: String = "Test Book",
        heRef: String? = null,
        order: Float = 1f,
        totalLines: Int = 100,
    ): Book =
        Book(
            id = id,
            categoryId = categoryId,
            sourceId = sourceId,
            title = title,
            heRef = heRef,
            order = order,
            totalLines = totalLines,
        )

    /**
     * Creates a list of sequential lines for a given book.
     */
    fun createLines(
        count: Int,
        bookId: Long = 100L,
        startId: Long = 1L,
    ): List<Line> =
        (0 until count).map { index ->
            createLine(
                id = startId + index,
                bookId = bookId,
                lineIndex = index,
                content = "Line content $index",
            )
        }

    /**
     * Creates a list of sequential line IDs.
     */
    fun createLineIds(
        count: Int,
        startId: Long = 1L,
    ): List<Long> = (0 until count).map { startId + it }
}
