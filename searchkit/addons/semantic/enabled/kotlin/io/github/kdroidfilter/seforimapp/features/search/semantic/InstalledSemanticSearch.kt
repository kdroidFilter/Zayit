package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.index.LuceneIndex

/** An official build: the semantic search of the SeforimEmbedding package. */
val installedSemanticSearch: SemanticSearchFeature? =
    object : SemanticSearchFeature {
        override val bundledModels: SemanticModels? get() = bundledSemanticModels()?.value

        override fun hybrid(
            lexical: SearchEngine,
            index: LuceneIndex,
            models: SemanticModels?,
            resolveLine: suspend (lineId: Long, query: String) -> LineHit?,
        ): SearchEngine = HybridSearchEngine(lexical, index, models?.let { lazyOf(it) } ?: bundledSemanticModels(), resolveLine)
    }

/** The model the package carries on this platform (the desktop's jar, extracted on first use), null on mobile. */
internal expect fun bundledSemanticModels(): Lazy<SemanticModels>?
