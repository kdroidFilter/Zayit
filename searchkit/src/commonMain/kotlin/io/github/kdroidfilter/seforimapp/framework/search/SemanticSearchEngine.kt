package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimapp.features.search.semantic.SemanticModels
import io.github.kdroidfilter.seforimapp.features.search.semantic.installedSemanticSearch
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.index.LuceneIndex

/**
 * Fuses [lexical] with dense semantic search in the official builds ([installedSemanticSearch], open core), whose
 * vectors live in the same Lucene [index]; returns [lexical] as is in a community build. Shared by the app's search
 * engine, `zayit cli` and the mobile apps, so all search the same way. [models] is the semantic model (null: the one the
 * package carries, on the desktop).
 */
fun withSemanticSearch(
    lexical: SearchEngine,
    index: LuceneIndex,
    repository: SeforimRepository,
    models: SemanticModels? = null,
): SearchEngine {
    val semantic = installedSemanticSearch ?: return lexical
    return semantic.hybrid(lexical, index, models) { lineId, query ->
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
