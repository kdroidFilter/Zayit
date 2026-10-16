package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimapp.features.search.semantic.SemanticModels
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.HebrewTextUtils
import io.github.kdroidfilter.seforimlibrary.search.LexicalSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.MagicDictionaryIndex
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.index.LuceneIndex

/**
 * The Lucene index at [indexDir], read on a JVM through Nucleus JVM Access (SeforimLibrary's search-facade): the
 * desktop's own, the one the mobile apps embed (in the app's process on Android and iOS). [cacheQueries]: cache any
 * query seen often (the small lookup index).
 */
fun openLuceneIndex(
    indexDir: String,
    cacheQueries: Boolean = false,
): LuceneIndex = LuceneIndex(indexDir, cacheQueries)

/**
 * The search of the database at [databasePath] and of the indexes beside it, as the desktop and the mobile apps set it
 * up: the search engine (lexical, fused with the semantic search in the official builds) and the lookup (books,
 * authors, tables of contents). Everything opens lazily, on first use; [semanticModels] is the semantic model of a
 * mobile app (the desktop's package carries its own).
 */
class SearchServices(
    private val databasePath: String,
    private val repository: SeforimRepository,
    private val semanticModels: SemanticModels? = null,
) : AutoCloseable {
    /** The full-text index: seforim.db.lucene beside seforim.db. */
    val indexPath: String get() = if (databasePath.endsWith(".db")) "$databasePath.lucene" else "$databasePath.luceneindex"

    /** The lookup index: seforim.db.lookup.lucene beside seforim.db. */
    val lookupIndexPath: String get() = if (databasePath.endsWith(".db")) "$databasePath.lookup.lucene" else "$databasePath.lookupindex"

    /** The lexical dictionary beside the index. */
    val dictionaryPath: String get() = indexPath.substringBeforeLast('/', ".") + "/lexical.db"

    private val textIndex = lazy { openLuceneIndex(indexPath) }

    /** The search engine. */
    val engine: SearchEngine by lazy {
        val lexical =
            LexicalSearchEngine(
                index = textIndex.value,
                snippetProvider = RepositorySnippetSourceProvider(repository),
                dictionary = lazy { MagicDictionaryIndex.load(HebrewTextUtils::normalizeHebrew, dictionaryPath) },
            )
        withSemanticSearch(lexical, textIndex.value, repository, semanticModels)
    }

    /** The acronyms of the books, for the lookup's ranking. */
    val acronyms: AcronymFrequencyCache by lazy { AcronymFrequencyCache { databasePath } }

    /** The lookup of books, authors and tables of contents. */
    val lookup: LuceneLookupSearchService by lazy {
        LuceneLookupSearchService(openLuceneIndex(lookupIndexPath, cacheQueries = true), acronyms)
    }

    override fun close() {
        if (textIndex.isInitialized()) runCatching { engine.close() }
        runCatching { lookup.close() }
        runCatching { acronyms.close() }
    }
}
