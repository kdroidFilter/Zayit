package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.index.LuceneIndex

/**
 * The files of the semantic search's model: the ONNX model, its tokenizer and its static distillation. The desktop's
 * semantic package carries them; a mobile app downloads them with the data and passes their paths.
 */
data class SemanticModels(
    val model: String,
    val tokenizer: String,
    val staticTable: String,
)

/**
 * The semantic search (embedding model + dense search), in Zayit's official builds only (open core): its engine is in
 * addons/semantic/enabled, built with the SeforimEmbedding package; a community build compiles addons/semantic/disabled
 * instead, where [installedSemanticSearch] is null and the search stays lexical.
 */
interface SemanticSearchFeature {
    /** The model the package carries (the desktop's), null where the app provides it (mobile). */
    val bundledModels: SemanticModels?

    /**
     * Fuses [lexical] with dense search over the vectors of [index] (the fused Lucene index), with the model of
     * [models] ([bundledModels] when null); it degrades to [lexical] when the model or the vectors are missing.
     * [resolveLine] turns a dense-only hit into a full [LineHit].
     */
    fun hybrid(
        lexical: SearchEngine,
        index: LuceneIndex,
        models: SemanticModels? = null,
        resolveLine: suspend (lineId: Long, query: String) -> LineHit?,
    ): SearchEngine
}
