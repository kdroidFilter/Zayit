package io.github.kdroidfilter.seforimapp.core.shnayimmikra

import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** The aliyos read of each parsha, by Hebrew year, in the local user DB; [revision] bumps on every write. */
class ShnayimMikraStore(
    database: UserSettingsDb,
) {
    private val queries = database.shnayimMikraQueries

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    suspend fun read(
        year: Long,
        parsha: String,
    ): Set<Int> =
        withContext(Dispatchers.IO) {
            queries
                .selectRead(year, parsha)
                .executeAsList()
                .map { it.toInt() }
                .toSet()
        }

    suspend fun setRead(
        year: Long,
        parsha: String,
        aliya: Int,
        read: Boolean,
    ): Unit =
        withContext(Dispatchers.IO) {
            if (read) {
                queries.markRead(year, parsha, aliya.toLong(), System.currentTimeMillis())
            } else {
                queries.unmarkRead(year, parsha, aliya.toLong())
            }
            _revision.update { it + 1 }
        }
}
