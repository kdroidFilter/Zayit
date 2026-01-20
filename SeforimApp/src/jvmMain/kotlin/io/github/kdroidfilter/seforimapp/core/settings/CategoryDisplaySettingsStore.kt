package io.github.kdroidfilter.seforimapp.core.settings

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

    suspend fun getShowDiacritics(categoryId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val value =
                database.categoryDisplaySettingsQueries
                    .selectShowDiacritics(categoryId)
                    .executeAsOneOrNull()
            (value ?: 1L) != 0L
        }

    suspend fun setShowDiacritics(
        categoryId: Long,
        enabled: Boolean,
    ) = withContext(Dispatchers.IO) {
        database.categoryDisplaySettingsQueries.upsertShowDiacritics(
            categoryId = categoryId,
            showDiacritics = if (enabled) 1L else 0L,
        )
        _categoryChanges.tryEmit(categoryId)
    }

    suspend fun toggleShowDiacritics(categoryId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val current =
                database.categoryDisplaySettingsQueries
                    .selectShowDiacritics(categoryId)
                    .executeAsOneOrNull()
            val next = (current ?: 1L) == 0L
            database.categoryDisplaySettingsQueries.upsertShowDiacritics(
                categoryId = categoryId,
                showDiacritics = if (next) 1L else 0L,
            )
            _categoryChanges.tryEmit(categoryId)
            next
        }
}
