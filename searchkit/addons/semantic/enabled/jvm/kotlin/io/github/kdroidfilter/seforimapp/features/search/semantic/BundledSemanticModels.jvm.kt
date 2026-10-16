package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforim.semantic.bundledModelFiles

// The desktop's semantic jar carries the model, extracted on first use
private val bundled = lazy { bundledModelFiles().let { SemanticModels(it.model, it.tokenizer, it.staticTable) } }

internal actual fun bundledSemanticModels(): Lazy<SemanticModels>? = bundled
