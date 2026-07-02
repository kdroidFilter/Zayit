package io.github.kdroidfilter.seforimapp.core.history

import androidx.compose.runtime.Stable
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.db.Visit_history
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** One deduplicated history entry (Chrome-like: last visit wins, count accumulates). */
@Stable
data class VisitEntry(
    val key: String,
    val kind: VisitKind,
    val bookId: Long?,
    val searchQuery: String?,
    val title: String,
    val visitedAt: Long,
    val visitCount: Long,
    val tocEntryId: Long? = null,
    val lineId: Long? = null,
)

enum class VisitKind { BOOK, SEARCH }

/**
 * Persistent full visit history (the chrome://history equivalent): every opened book and every
 * executed search, unbounded, stored in the local user database. Rows are deduplicated by target
 * key; a revisit bumps `visitedAt`/`visitCount`.
 *
 * Reads are on-demand SQL queries (recency-ordered, LIKE search); [revision] bumps on every
 * write so UI can re-run its current query reactively.
 */
class HistoryStore(
    database: UserSettingsDb,
) {
    private val queries = database.visitHistoryQueries

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /**
     * Records a book visit at an optional TOC position (Chrome records precise URLs, we record
     * book + chapter): one history row per (book, tocEntry), with the line to reopen at.
     */
    suspend fun recordBookVisit(
        bookId: Long,
        title: String,
        timestamp: Long,
        tocEntryId: Long? = null,
        lineId: Long? = null,
    ): Unit =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext
            queries.upsertVisit(
                key = if (tocEntryId != null) "book:$bookId:$tocEntryId" else "book:$bookId",
                kind = KIND_BOOK,
                bookId = bookId,
                searchQuery = null,
                title = title,
                visitedAt = timestamp,
                tocEntryId = tocEntryId,
                lineId = lineId,
            )
            _revision.update { it + 1 }
        }

    suspend fun recordSearchVisit(
        query: String,
        timestamp: Long,
    ): Unit =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isBlank()) return@withContext
            queries.upsertVisit(
                key = "search:$q",
                kind = KIND_SEARCH,
                bookId = null,
                searchQuery = q,
                title = q,
                visitedAt = timestamp,
                tocEntryId = null,
                lineId = null,
            )
            _revision.update { it + 1 }
        }

    /** Most recent entries, or entries whose title matches [query] when non-blank. */
    suspend fun query(
        query: String,
        limit: Int,
    ): List<VisitEntry> =
        withContext(Dispatchers.IO) {
            val rows =
                if (query.isBlank()) {
                    queries.selectRecent(limit.toLong()).executeAsList()
                } else {
                    queries.searchByTitle(query = query.trim(), limitCount = limit.toLong()).executeAsList()
                }
            rows.map { it.toEntry() }
        }

    suspend fun delete(key: String): Unit =
        withContext(Dispatchers.IO) {
            queries.deleteByKey(key)
            _revision.update { it + 1 }
        }

    suspend fun clearAll(): Unit =
        withContext(Dispatchers.IO) {
            queries.deleteAll()
            _revision.update { it + 1 }
        }

    private fun Visit_history.toEntry(): VisitEntry =
        VisitEntry(
            key = key,
            kind = if (kind == KIND_SEARCH) VisitKind.SEARCH else VisitKind.BOOK,
            bookId = bookId,
            searchQuery = searchQuery,
            title = title,
            visitedAt = visitedAt,
            visitCount = visitCount,
            tocEntryId = tocEntryId,
            lineId = lineId,
        )

    private companion object {
        const val KIND_BOOK = "book"
        const val KIND_SEARCH = "search"
    }
}
