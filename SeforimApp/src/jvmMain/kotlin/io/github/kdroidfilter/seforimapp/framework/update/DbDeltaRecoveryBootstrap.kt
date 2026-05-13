package io.github.kdroidfilter.seforimapp.framework.update

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import io.github.kdroidfilter.seforimlibrary.deltaupdater.DeltaApplierClient
import java.io.File
import java.nio.file.Path

/**
 * Boot-time hook that runs BEFORE the SQLDelight repository opens
 * `seforim.db`. If the previous run crashed between the SQLite COMMIT and
 * the orchestrator's finalize step, a `.backup` and `.applying` marker
 * file pair will be sitting next to the DB; the recovery rolls the live
 * DB back to the backup so the next launch sees a coherent state.
 *
 * Designed to be cheap and side-effect-free when nothing is to recover:
 * just a stat() of the marker file. Always returns silently — failures
 * are logged but never thrown so the app keeps booting even if the
 * recovery itself misbehaves.
 *
 * Call this exactly once, from `main()`, before any DB consumer runs.
 */
object DbDeltaRecoveryBootstrap {

    /** Returns `true` if a half-applied delta was rolled back. */
    fun runOnce(): Boolean {
        val dbPath = resolveDbPathOrNull() ?: return false
        if (!File(dbPath).exists()) return false
        return try {
            val recovered = DeltaApplierClient().recoverIfNeeded(Path.of(dbPath))
            if (recovered) {
                warnln { "[DbDeltaRecoveryBootstrap] Recovered from a half-applied delta update on $dbPath" }
            } else {
                infoln { "[DbDeltaRecoveryBootstrap] No delta-update recovery needed for $dbPath" }
            }
            recovered
        } catch (t: Throwable) {
            warnln { "[DbDeltaRecoveryBootstrap] Recovery check failed: ${t.message}" }
            false
        }
    }

    /**
     * Mirrors `DatabaseUtils.getDatabasePath` but never throws when the
     * file is missing. Returns `null` when no candidate path is resolvable —
     * in which case there's nothing to recover.
     */
    private fun resolveDbPathOrNull(): String? {
        val env = System.getenv("SEFORIMAPP_DATABASE_PATH")?.takeIf { it.isNotBlank() }
        if (env != null) return env
        val settings = runCatching { AppSettings.getDatabasePath() }.getOrNull()
            ?.takeIf { it.isNotBlank() && !it.endsWith("lexical.db", ignoreCase = true) }
        if (settings != null) return settings
        return runCatching { File(FileKit.databasesDir.path, "seforim.db").absolutePath }
            .getOrNull()
    }
}
