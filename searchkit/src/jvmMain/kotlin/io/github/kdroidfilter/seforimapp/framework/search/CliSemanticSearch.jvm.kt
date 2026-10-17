package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import java.nio.file.Path

/** [withSemanticSearch] for the engine `zayit cli` builds (see `CliHost`): the semantic search reads its index. */
fun withSemanticSearch(
    lexical: LuceneSearchEngine,
    @Suppress("UNUSED_PARAMETER") indexDir: Path,
    repository: SeforimRepository,
): SearchEngine = withSemanticSearch(lexical, lexical.index, repository)
