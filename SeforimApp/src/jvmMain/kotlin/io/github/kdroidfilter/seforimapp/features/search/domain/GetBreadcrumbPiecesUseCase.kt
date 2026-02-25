package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

class GetBreadcrumbPiecesUseCase(
    private val repository: SeforimRepository,
) {
    private val bookCache: MutableMap<Long, io.github.kdroidfilter.seforimlibrary.core.models.Book> = mutableMapOf()
    private val categoryPathCache: MutableMap<Long, List<Category>> = mutableMapOf()
    private val tocPathCache: MutableMap<Long, List<TocEntry>> = mutableMapOf()

    suspend operator fun invoke(result: SearchResult): List<String> {
        val pieces = mutableListOf<String>()

        val book =
            bookCache[result.bookId] ?: repository.getBookCore(result.bookId)?.also {
                bookCache[result.bookId] = it
            } ?: return emptyList()

        val categories =
            categoryPathCache[book.categoryId] ?: buildCategoryPath(book.categoryId).also {
                categoryPathCache[book.categoryId] = it
            }
        pieces += categories.map { it.title }
        pieces += book.title

        val tocEntries =
            tocPathCache[result.lineId] ?: run {
                val tocId = runSuspendCatching { repository.getTocEntryIdForLine(result.lineId) }.getOrNull()
                if (tocId != null) {
                    val path = mutableListOf<TocEntry>()
                    var current: Long? = tocId
                    var guard = 0
                    while (current != null && guard++ < 200) {
                        val entry = repository.getTocEntry(current)
                        if (entry != null) {
                            path.add(0, entry)
                            current = entry.parentId
                        } else {
                            break
                        }
                    }
                    path
                } else {
                    emptyList()
                }
            }.also { path ->
                tocPathCache[result.lineId] = path
            }

        if (tocEntries.isNotEmpty()) {
            val adjusted = if (tocEntries.first().text == book.title) tocEntries.drop(1) else tocEntries
            pieces += adjusted.map { it.text }
        }

        return pieces
    }

    private suspend fun buildCategoryPath(categoryId: Long): List<Category> {
        val path = mutableListOf<Category>()
        var current: Long? = categoryId
        while (current != null) {
            val cat = repository.getCategory(current) ?: break
            path += cat
            current = cat.parentId
        }
        return path.asReversed()
    }
}
