package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import java.nio.file.Path

/** An official build: the semantic search, its model inside the SeforimEmbedding package. */
val installedSemanticSearch: SemanticSearchFeature? =
    object : SemanticSearchFeature {
        override fun hybrid(
            lexical: SearchEngine,
            indexDir: Path,
            resolveLine: suspend (lineId: Long, query: String) -> LineHit?,
        ): SearchEngine = HybridSearchEngine(lexical, indexDir, resolveLine)
    }
