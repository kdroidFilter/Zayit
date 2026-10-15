package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimapp.features.search.semantic.installedSemanticSearch
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import java.nio.file.Path

/**
 * Fuses [lexical] with dense semantic search in the official builds ([installedSemanticSearch], open core), whose
 * vectors live in the same Lucene index at [indexPath]; returns [lexical] as is in a community build. Shared by the
 * app's search engine and `zayit cli`, so both search the same way.
 */
fun withSemanticSearch(
    lexical: LuceneSearchEngine,
    indexPath: Path,
    repository: SeforimRepository,
): SearchEngine {
    val semantic = installedSemanticSearch ?: return lexical
    return semantic.hybrid(lexical, indexPath) { lineId, query ->
        repository.getLine(lineId)?.let { line ->
            LineHit(
                bookId = line.bookId,
                bookTitle = repository.getBook(line.bookId)?.title.orEmpty(),
                lineId = lineId,
                lineIndex = line.lineIndex,
                snippet = lexical.buildSnippet(line.content, query, 5),
                score = 0f,
                rawText = line.content,
            )
        }
    }
}
