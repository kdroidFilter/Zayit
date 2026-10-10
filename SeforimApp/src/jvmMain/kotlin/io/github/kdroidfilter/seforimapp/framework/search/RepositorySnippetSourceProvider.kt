package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.LineSnippetInfo
import io.github.kdroidfilter.seforimlibrary.search.SnippetProvider
import io.github.kdroidfilter.seforimlibrary.search.SnippetSource
import io.github.kdroidfilter.seforimlibrary.search.SnippetSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [SnippetProvider] over the app's database: the lines come from [repository]. */
class RepositorySnippetSourceProvider(
    private val repository: SeforimRepository,
) : SnippetProvider {
    override suspend fun getSnippetSources(lines: List<LineSnippetInfo>): Map<Long, SnippetSource> {
        if (lines.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            SnippetSources.build(lines) { bookId, from, to ->
                repository.getLines(bookId, from, to).associate { it.lineIndex to it.content }
            }
        }
    }
}
