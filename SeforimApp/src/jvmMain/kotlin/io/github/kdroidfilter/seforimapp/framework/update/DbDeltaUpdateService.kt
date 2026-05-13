package io.github.kdroidfilter.seforimapp.framework.update

import io.github.kdroidfilter.seforimlibrary.deltaupdater.DeltaUpdaterClient
import io.github.kdroidfilter.seforimlibrary.deltaupdater.LuceneUpdater
import io.github.kdroidfilter.seforimlibrary.deltaupdater.UpdatePath
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * SeforimApp's facade over the seforim.db delta-update flow.
 *
 *  - At app startup, call [recoverIfNeeded] BEFORE opening the SQLDelight
 *    repository: if the previous launch crashed mid-apply, the backup is
 *    restored before any client code touches the DB.
 *  - Periodically (or on user demand), call [checkAndApply]; if a chain
 *    of deltas is available, it's downloaded and applied. The live
 *    Lucene index is informed of the upserted / deleted line ids via
 *    caller-supplied sinks so the app stays in lock-step.
 *
 * Constructed once at app boot. Thread-safe to call from a background
 * coroutine.
 */
class DbDeltaUpdateService(
    private val seforimDb: Path,
    private val catalogPb: Path,
    private val workDir: Path,
    private val releaseMetaUrl: String,
    private val localDbVersionProvider: () -> Int,
    private val luceneSinksProvider: () -> Pair<LuceneUpdater.DeleteSink, LuceneUpdater.UpsertSink> =
        { LuceneUpdater.DeleteSink {} to LuceneUpdater.UpsertSink {} },
) {

    private val log = LoggerFactory.getLogger(DbDeltaUpdateService::class.java)

    private val client by lazy {
        DeltaUpdaterClient(
            seforimDb = seforimDb,
            catalogPb = catalogPb,
            workDir = workDir,
            releaseMetaUrl = releaseMetaUrl,
            indexSinks = luceneSinksProvider,
            localVersionProvider = localDbVersionProvider,
        )
    }

    /**
     * Must be called BEFORE opening the SQLDelight repository at app
     * startup. If a marker file is present alongside seforim.db, the
     * file-level backup is restored.
     *
     * @return `true` if recovery happened, `false` if no marker was present.
     */
    fun recoverIfNeeded(): Boolean {
        val recovered = client.recoverIfNeeded()
        if (recovered) log.warn("Recovered from a half-applied delta update: live DB rolled back to backup.")
        return recovered
    }

    /**
     * Polls the release server and applies any available chain. Reports
     * progress via [onProgress] as `current/total: status`.
     */
    suspend fun checkAndApply(
        onProgress: (current: Int, total: Int, status: String) -> Unit = { _, _, _ -> },
    ): Outcome {
        return when (val path = client.checkForUpdate()) {
            UpdatePath.UpToDate -> Outcome.UpToDate
            is UpdatePath.FullBundle -> Outcome.NeedsFullBundle
            is UpdatePath.Chain -> {
                client.applyChain(path.deltas, onProgress)
                Outcome.Applied(path.deltas.size)
            }
        }
    }

    sealed interface Outcome {
        data object UpToDate : Outcome
        data class Applied(val deltaCount: Int) : Outcome
        data object NeedsFullBundle : Outcome
    }
}
