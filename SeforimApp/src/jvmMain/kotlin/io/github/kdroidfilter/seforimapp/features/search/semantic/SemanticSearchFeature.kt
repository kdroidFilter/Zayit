package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import java.nio.file.Path

/**
 * The semantic search (embedding model + dense search), in Zayit's official builds only (open core): its engine is in
 * addons/semantic/enabled, built with the SeforimEmbedding package; a community build compiles addons/semantic/disabled
 * instead, where [installedSemanticSearch] is null and the search stays lexical.
 */
interface SemanticSearchFeature {
    /**
     * Fuses [lexical] with dense search over the vectors of the index at [indexDir]; it degrades to [lexical] when the
     * model or the vectors are missing. [resolveLine] turns a dense-only hit into a full [LineHit].
     */
    fun hybrid(
        lexical: SearchEngine,
        indexDir: Path,
        resolveLine: suspend (lineId: Long, query: String) -> LineHit?,
    ): SearchEngine
}
