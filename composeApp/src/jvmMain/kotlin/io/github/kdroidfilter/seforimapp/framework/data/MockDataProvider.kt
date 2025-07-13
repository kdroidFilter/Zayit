package io.github.kdroidfilter.seforimapp.framework.data

import io.github.kdroidfilter.seforimlibrary.core.models.*

/**
 * Provides mock data that mimics the structure of the real database data.
 */
object MockDataProvider {
    
    /**
     * Get a list of root categories.
     */
    fun getRootCategories(): List<Category> {
        return listOf(
            Category(id = 1, title = "Torah", level = 0),
            Category(id = 2, title = "Nevi'im", level = 0),
            Category(id = 3, title = "Ketuvim", level = 0),
            Category(id = 4, title = "Mishnah", level = 0),
            Category(id = 5, title = "Talmud", level = 0)
        )
    }
    
    /**
     * Get a list of child categories for a parent category.
     */
    fun getCategoryChildren(parentId: Long): List<Category> {
        return when (parentId) {
            1L -> listOf( // Torah
                Category(id = 11, parentId = 1, title = "Bereshit", level = 1),
                Category(id = 12, parentId = 1, title = "Shemot", level = 1),
                Category(id = 13, parentId = 1, title = "Vayikra", level = 1),
                Category(id = 14, parentId = 1, title = "Bamidbar", level = 1),
                Category(id = 15, parentId = 1, title = "Devarim", level = 1)
            )
            2L -> listOf( // Nevi'im
                Category(id = 21, parentId = 2, title = "Early Prophets", level = 1),
                Category(id = 22, parentId = 2, title = "Later Prophets", level = 1)
            )
            3L -> listOf( // Ketuvim
                Category(id = 31, parentId = 3, title = "Psalms", level = 1),
                Category(id = 32, parentId = 3, title = "Proverbs", level = 1),
                Category(id = 33, parentId = 3, title = "Job", level = 1)
            )
            4L -> listOf( // Mishnah
                Category(id = 41, parentId = 4, title = "Zeraim", level = 1),
                Category(id = 42, parentId = 4, title = "Moed", level = 1),
                Category(id = 43, parentId = 4, title = "Nashim", level = 1),
                Category(id = 44, parentId = 4, title = "Nezikin", level = 1),
                Category(id = 45, parentId = 4, title = "Kodashim", level = 1),
                Category(id = 46, parentId = 4, title = "Taharot", level = 1)
            )
            5L -> listOf( // Talmud
                Category(id = 51, parentId = 5, title = "Bavli", level = 1),
                Category(id = 52, parentId = 5, title = "Yerushalmi", level = 1)
            )
            else -> emptyList()
        }
    }
    
    /**
     * Get a list of books for a category.
     */
    fun getBooksByCategory(categoryId: Long): List<Book> {
        return when (categoryId) {
            11L -> listOf( // Bereshit
                Book(id = 111, categoryId = 11, title = "Genesis", authors = listOf(Author(id = 1, name = "Moses")), totalLines = 1533)
            )
            12L -> listOf( // Shemot
                Book(id = 121, categoryId = 12, title = "Exodus", authors = listOf(Author(id = 1, name = "Moses")), totalLines = 1209)
            )
            13L -> listOf( // Vayikra
                Book(id = 131, categoryId = 13, title = "Leviticus", authors = listOf(Author(id = 1, name = "Moses")), totalLines = 859)
            )
            14L -> listOf( // Bamidbar
                Book(id = 141, categoryId = 14, title = "Numbers", authors = listOf(Author(id = 1, name = "Moses")), totalLines = 1288)
            )
            15L -> listOf( // Devarim
                Book(id = 151, categoryId = 15, title = "Deuteronomy", authors = listOf(Author(id = 1, name = "Moses")), totalLines = 955)
            )
            21L -> listOf( // Early Prophets
                Book(id = 211, categoryId = 21, title = "Joshua", authors = listOf(Author(id = 2, name = "Joshua")), totalLines = 658),
                Book(id = 212, categoryId = 21, title = "Judges", authors = listOf(Author(id = 3, name = "Samuel")), totalLines = 618),
                Book(id = 213, categoryId = 21, title = "Samuel I", authors = listOf(Author(id = 3, name = "Samuel")), totalLines = 810),
                Book(id = 214, categoryId = 21, title = "Samuel II", authors = listOf(Author(id = 3, name = "Samuel")), totalLines = 695),
                Book(id = 215, categoryId = 21, title = "Kings I", authors = listOf(Author(id = 4, name = "Jeremiah")), totalLines = 817),
                Book(id = 216, categoryId = 21, title = "Kings II", authors = listOf(Author(id = 4, name = "Jeremiah")), totalLines = 719)
            )
            else -> emptyList()
        }
    }
    
    /**
     * Get a book by its ID.
     */
    fun getBook(id: Long): Book? {
        return when (id) {
            111L -> Book(id = 111, categoryId = 11, title = "Genesis", authors = listOf(Author(id = 1, name = "Moses")), totalLines = 1533)
            121L -> Book(id = 121, categoryId = 12, title = "Exodus", authors = listOf(Author(id = 1, name = "Moses")), totalLines = 1209)
            131L -> Book(id = 131, categoryId = 13, title = "Leviticus", authors = listOf(Author(id = 1, name = "Moses")), totalLines = 859)
            141L -> Book(id = 141, categoryId = 14, title = "Numbers", authors = listOf(Author(id = 1, name = "Moses")), totalLines = 1288)
            151L -> Book(id = 151, categoryId = 15, title = "Deuteronomy", authors = listOf(Author(id = 1, name = "Moses")), totalLines = 955)
            211L -> Book(id = 211, categoryId = 21, title = "Joshua", authors = listOf(Author(id = 2, name = "Joshua")), totalLines = 658)
            212L -> Book(id = 212, categoryId = 21, title = "Judges", authors = listOf(Author(id = 3, name = "Samuel")), totalLines = 618)
            213L -> Book(id = 213, categoryId = 21, title = "Samuel I", authors = listOf(Author(id = 3, name = "Samuel")), totalLines = 810)
            214L -> Book(id = 214, categoryId = 21, title = "Samuel II", authors = listOf(Author(id = 3, name = "Samuel")), totalLines = 695)
            215L -> Book(id = 215, categoryId = 21, title = "Kings I", authors = listOf(Author(id = 4, name = "Jeremiah")), totalLines = 817)
            216L -> Book(id = 216, categoryId = 21, title = "Kings II", authors = listOf(Author(id = 4, name = "Jeremiah")), totalLines = 719)
            else -> null
        }
    }
    
    /**
     * Get a list of lines for a book.
     */
    fun getLines(bookId: Long, startIndex: Int, endIndex: Int): List<Line> {
        val lines = mutableListOf<Line>()
        for (i in startIndex..endIndex) {
            lines.add(
                Line(
                    id = bookId * 10000 + i.toLong(),
                    bookId = bookId,
                    lineIndex = i,
                    content = "<p>This is line $i of book $bookId.</p>",
                    plainText = "This is line $i of book $bookId."
                )
            )
        }
        return lines
    }
    
    /**
     * Get a list of TOC entries for a book.
     */
    fun getBookToc(bookId: Long): List<TocEntry> {
        val entries = mutableListOf<TocEntry>()
        for (i in 1..10) {
            entries.add(
                TocEntry(
                    id = bookId * 100 + i.toLong(),
                    bookId = bookId,
                    text = "Chapter $i",
                    level = 1,
                    lineId = bookId * 10000 + (i * 10).toLong()
                )
            )
        }
        return entries
    }
    
    /**
     * Get a list of child TOC entries for a parent TOC entry.
     */
    fun getTocChildren(parentId: Long): List<TocEntry> {
        val bookId = parentId / 100
        val chapterId = parentId % 100
        val entries = mutableListOf<TocEntry>()
        for (i in 1..5) {
            entries.add(
                TocEntry(
                    id = parentId * 10 + i.toLong(),
                    bookId = bookId,
                    parentId = parentId,
                    text = "Section $chapterId.$i",
                    level = 2,
                    lineId = bookId * 10000 + (chapterId * 10 + i).toLong()
                )
            )
        }
        return entries
    }
}