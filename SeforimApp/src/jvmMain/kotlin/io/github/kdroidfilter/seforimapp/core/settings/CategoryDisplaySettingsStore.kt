package io.github.kdroidfilter.seforimapp.core.settings

import io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

class CategoryDisplaySettingsStore(
    private val database: UserSettingsDb,
) {
    private val _categoryChanges = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val categoryChanges: SharedFlow<Long> = _categoryChanges.asSharedFlow()

    suspend fun getDiacritics(categoryId: Long): DiacriticsMode = withContext(Dispatchers.IO) { readDiacritics(categoryId) }

    /** Moves the category to the next [DiacriticsMode] (see [DiacriticsMode.next]) and returns it. */
    suspend fun cycleDiacritics(
        categoryId: Long,
        hasTeamim: Boolean,
    ): DiacriticsMode =
        withContext(Dispatchers.IO) {
            val next = readDiacritics(categoryId).next(hasTeamim)
            database.categoryDisplaySettingsQueries.upsertShowDiacritics(
                categoryId = categoryId,
                showDiacritics = next.toStored(),
            )
            _categoryChanges.tryEmit(categoryId)
            next
        }

    private fun readDiacritics(categoryId: Long): DiacriticsMode =
        database.categoryDisplaySettingsQueries
            .selectShowDiacritics(categoryId)
            .executeAsOneOrNull()
            .toDiacriticsMode()
}

// The column predates the nikud-only mode: 1 (all) and 0 (none) keep their meaning.
private const val STORED_NONE = 0L
private const val STORED_ALL = 1L
private const val STORED_NIKUD_ONLY = 2L

private fun Long?.toDiacriticsMode(): DiacriticsMode =
    when (this) {
        STORED_NONE -> DiacriticsMode.None
        STORED_NIKUD_ONLY -> DiacriticsMode.NikudOnly
        else -> DiacriticsMode.All
    }

private fun DiacriticsMode.toStored(): Long =
    when (this) {
        DiacriticsMode.All -> STORED_ALL
        DiacriticsMode.NikudOnly -> STORED_NIKUD_ONLY
        DiacriticsMode.None -> STORED_NONE
    }
