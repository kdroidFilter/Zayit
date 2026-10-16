package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimlibrary.search.index.LuceneIndex
import io.github.kdroidfilter.seforimlibrary.search.index.LuceneIndexBackend
import java.io.File

// Lucene on ART, in process (Android 15+; lucene-core patched for it, see SeforimLibrary's lucene-android)
actual fun openLuceneIndex(
    indexDir: String,
    cacheQueries: Boolean,
): LuceneIndex = LuceneIndexBackend(File(indexDir).toPath(), cacheQueries = cacheQueries)
