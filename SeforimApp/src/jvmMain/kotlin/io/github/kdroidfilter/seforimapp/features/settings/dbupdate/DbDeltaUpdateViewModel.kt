package io.github.kdroidfilter.seforimapp.features.settings.dbupdate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.framework.update.DbDeltaUpdateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the "Database delta update" settings panel.
 *
 * Wraps [DbDeltaUpdateService] in a ViewModel so the Compose layer can
 * observe a [DbDeltaUpdateState] flow, fire [DbDeltaUpdateEvents], and
 * never block the UI thread.
 *
 * The HTTP call + apply transaction are confined to [Dispatchers.IO];
 * progress callbacks marshal back to the ViewModel scope through a
 * [MutableStateFlow] so Compose can read the latest message on every
 * frame.
 */
@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
class DbDeltaUpdateViewModel(
    private val deltaService: DbDeltaUpdateService,
) : ViewModel() {

    private val mutableState = MutableStateFlow(DbDeltaUpdateState())
    val state = mutableState.asStateFlow().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DbDeltaUpdateState(),
    )

    fun onEvent(event: DbDeltaUpdateEvents) {
        when (event) {
            DbDeltaUpdateEvents.CheckAndApplyClicked -> startCheckAndApply()
            DbDeltaUpdateEvents.ClearMessage ->
                mutableState.value = mutableState.value.copy(message = "", errorMessage = null)
        }
    }

    private fun startCheckAndApply() {
        val current = mutableState.value
        if (current.phase != null) return // already running

        viewModelScope.launch {
            mutableState.value = DbDeltaUpdateState(
                phase = DbDeltaUpdateState.Phase.CheckingForUpdates,
                message = "Checking server for new database delta…",
            )
            try {
                val outcome = withContext(Dispatchers.IO) {
                    deltaService.checkAndApply { _, _, status ->
                        // The orchestrator pumps statuses like
                        // "downloading patch files", "applying sqlite delta",
                        // "updating lucene", "updating catalog", "done".
                        val phase = when {
                            "download" in status -> DbDeltaUpdateState.Phase.Downloading
                            "sqlite" in status -> DbDeltaUpdateState.Phase.Applying
                            "lucene" in status || "catalog" in status ->
                                DbDeltaUpdateState.Phase.UpdatingIndex
                            else -> mutableState.value.phase
                        }
                        mutableState.value = mutableState.value.copy(
                            phase = phase,
                            message = status,
                        )
                    }
                }
                mutableState.value = when (outcome) {
                    DbDeltaUpdateService.Outcome.UpToDate -> DbDeltaUpdateState(
                        message = "Database is up to date.",
                    )
                    is DbDeltaUpdateService.Outcome.Applied -> DbDeltaUpdateState(
                        message = "Applied ${outcome.deltaCount} delta(s).",
                        lastAppliedCount = outcome.deltaCount,
                    )
                    DbDeltaUpdateService.Outcome.NeedsFullBundle -> DbDeltaUpdateState(
                        message = "Your local database is too old for an incremental update — please download the full bundle.",
                        needsFullBundle = true,
                    )
                }
            } catch (t: Throwable) {
                mutableState.value = DbDeltaUpdateState(
                    errorMessage = "Update failed: ${t.message ?: t.javaClass.simpleName}",
                )
            }
        }
    }
}
