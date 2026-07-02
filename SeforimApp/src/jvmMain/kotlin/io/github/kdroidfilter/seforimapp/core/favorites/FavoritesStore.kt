package io.github.kdroidfilter.seforimapp.core.favorites

import androidx.compose.runtime.Stable
import io.github.kdroidfilter.seforimapp.db.Favorite
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** One favorited book position, optionally filed into a user folder (Chrome-like bookmark). */
@Stable
data class FavoriteEntry(
    val key: String,
    val bookId: Long,
    val title: String,
    val folderId: Long?,
    val createdAt: Long,
    val tocEntryId: Long? = null,
    val lineId: Long? = null,
)

@Stable
data class FavoriteFolder(
    val id: Long,
    val name: String,
)

/**
 * Persistent favorites (the Chrome bookmarks equivalent): book positions the user starred,
 * deduplicated by target key and optionally grouped into flat user-created folders.
 *
 * Reads are on-demand SQL queries; [revision] bumps on every write so UI can re-run its
 * current query reactively.
 */
class FavoritesStore(
    database: UserSettingsDb,
) {
    private val queries = database.favoritesQueries

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** Same key scheme as the visit history so a position stars consistently. */
    fun keyFor(
        bookId: Long,
        tocEntryId: Long?,
    ): String = if (tocEntryId != null) "book:$bookId:$tocEntryId" else "book:$bookId"

    suspend fun add(
        bookId: Long,
        title: String,
        timestamp: Long,
        tocEntryId: Long? = null,
        lineId: Long? = null,
        folderId: Long? = null,
    ): Unit =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext
            queries.upsertFavorite(
                key = keyFor(bookId, tocEntryId),
                bookId = bookId,
                title = title,
                folderId = folderId,
                createdAt = timestamp,
                tocEntryId = tocEntryId,
                lineId = lineId,
            )
            _revision.update { it + 1 }
        }

    suspend fun remove(key: String): Unit =
        withContext(Dispatchers.IO) {
            queries.deleteFavoriteByKey(key)
            _revision.update { it + 1 }
        }

    suspend fun isFavorite(key: String): Boolean =
        withContext(Dispatchers.IO) {
            queries.countFavorite(key).executeAsOne() > 0
        }

    suspend fun get(key: String): FavoriteEntry? =
        withContext(Dispatchers.IO) {
            queries.selectFavoriteByKey(key).executeAsOneOrNull()?.toEntry()
        }

    suspend fun setFolder(
        key: String,
        folderId: Long?,
    ): Unit =
        withContext(Dispatchers.IO) {
            queries.setFavoriteFolder(folderId = folderId, key = key)
            _revision.update { it + 1 }
        }

    /** Creates a folder and returns its id. */
    suspend fun createFolder(
        name: String,
        timestamp: Long,
    ): Long =
        withContext(Dispatchers.IO) {
            queries.insertFolder(name = name.trim(), createdAt = timestamp)
            val id = queries.lastInsertedFolderId().executeAsOne()
            _revision.update { it + 1 }
            id
        }

    /** Deletes a folder; its favorites move back to the root. */
    suspend fun deleteFolder(id: Long): Unit =
        withContext(Dispatchers.IO) {
            queries.clearFolderFromFavorites(id)
            queries.deleteFolder(id)
            _revision.update { it + 1 }
        }

    suspend fun folders(): List<FavoriteFolder> =
        withContext(Dispatchers.IO) {
            queries.selectFolders().executeAsList().map { FavoriteFolder(id = it.id, name = it.name) }
        }

    /** All favorites, or those whose title matches [query] when non-blank. */
    suspend fun query(query: String = ""): List<FavoriteEntry> =
        withContext(Dispatchers.IO) {
            val rows =
                if (query.isBlank()) {
                    queries.selectAllFavorites().executeAsList()
                } else {
                    val escaped =
                        query
                            .trim()
                            .replace("\\", "\\\\")
                            .replace("%", "\\%")
                            .replace("_", "\\_")
                    queries.searchFavoritesByTitle(escaped).executeAsList()
                }
            rows.map { it.toEntry() }
        }

    private fun Favorite.toEntry(): FavoriteEntry =
        FavoriteEntry(
            key = key,
            bookId = bookId,
            title = title,
            folderId = folderId,
            createdAt = createdAt,
            tocEntryId = tocEntryId,
            lineId = lineId,
        )
}
