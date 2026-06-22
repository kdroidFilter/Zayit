package io.github.kdroidfilter.seforimapp.features.settings.dbupdate

/**
 * State surface for the "Database delta update" settings panel.
 *
 * The whole machinery lives in the framework — this is just what the UI
 * needs to render the panel.
 */
data class DbDeltaUpdateState(
    /** Active op or `null` when idle. */
    val phase: Phase? = null,
    /** Most recent message (download progress, apply phase, error, success). */
    val message: String = "",
    /** Filled once the apply succeeds: number of deltas applied in the last run. */
    val lastAppliedCount: Int? = null,
    /** Filled when the server reports the local version too old for incremental updates. */
    val needsFullBundle: Boolean = false,
    /** Set when an exception escaped — the UI can show a "retry" affordance. */
    val errorMessage: String? = null,
) {
    enum class Phase { CheckingForUpdates, Downloading, Applying, UpdatingIndex }
}
