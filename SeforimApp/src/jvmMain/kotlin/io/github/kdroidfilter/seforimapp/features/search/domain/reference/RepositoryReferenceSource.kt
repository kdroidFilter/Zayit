package io.github.kdroidfilter.seforimapp.features.search.domain.reference

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

/** [ReferenceSource] over the seforim database. */
class RepositoryReferenceSource(
    private val repository: SeforimRepository,
) : ReferenceSource {
    override suspend fun booksNamed(normalizedName: String): List<Book> {
        // A LIKE pattern without wildcards: every book carrying exactly this title, whatever its source
        val byTitle = repository.findBooksByTitleLikeCore(normalizedName, limit = MAX_BOOKS)
        val byAcronym = repository.findBookIdsByAcronym(normalizedName).mapNotNull { repository.getBookCore(it) }
        return (byTitle + byAcronym).distinctBy { it.id }
    }

    override suspend fun toc(bookId: Long): List<TocEntry> = repository.getBookToc(bookId)

    override suspend fun linesOf(tocEntryId: Long): List<Line> =
        repository.getLinesByIds(repository.getLineIdsForTocEntry(tocEntryId)).sortedBy { it.lineIndex }

    private companion object {
        const val MAX_BOOKS = 10
    }
}
