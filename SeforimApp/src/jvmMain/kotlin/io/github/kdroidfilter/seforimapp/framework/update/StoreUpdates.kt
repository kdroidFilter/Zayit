package io.github.kdroidfilter.seforimapp.framework.update

import dev.nucleusframework.core.runtime.NucleusApp
import dev.nucleusframework.store.microsoft.MicrosoftStore
import dev.nucleusframework.store.microsoft.StoreUpdateCheck
import dev.nucleusframework.store.microsoft.StoreUpdateProgress
import dev.nucleusframework.updater.UpdateLevel
import dev.nucleusframework.updater.Version
import io.github.kdroidfilter.seforimapp.logger.infoln
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The updates of the Microsoft Store install (AppX): the Store, not the GitHub feed, has its new versions,
 * and installs them itself. Thin seam over [MicrosoftStore.updates] so [AppUpdateService] can be tested with a fake.
 */
interface StoreUpdates {
    val currentVersion: String

    /** Whether the Store has a newer version; silent. */
    suspend fun check(): StoreUpdateCheck

    /** Downloads and installs it behind the Store's dialog; Windows closes the app to install, then relaunches it. */
    fun install(): Flow<StoreUpdateProgress>
}

/** Real [StoreUpdates] backed by Nucleus' Microsoft Store module; honours dry-run by logging instead of installing. */
class MicrosoftStoreUpdatesAdapter(
    private val dryRun: Boolean,
) : StoreUpdates {
    override val currentVersion: String get() = NucleusApp.version.orEmpty()

    override suspend fun check(): StoreUpdateCheck = MicrosoftStore.updates.check()

    override fun install(): Flow<StoreUpdateProgress> {
        if (dryRun) {
            infoln { "[update][dry-run] Microsoft Store install" }
            return emptyFlow()
        }
        return MicrosoftStore.updates.install()
    }
}

/**
 * The level of the Store's [storeVersion] over [currentVersion], to warn about the database update a MINOR/MAJOR
 * may need. The Store gives `major.minor.build.revision`, kept to its first three parts; a version it does not give
 * counts as MINOR, so the warning shows rather than not.
 */
fun storeUpdateLevel(
    currentVersion: String,
    storeVersion: String?,
): UpdateLevel {
    if (storeVersion == null) return UpdateLevel.MINOR
    return Version.fromString(storeSemanticVersion(storeVersion)).levelFrom(Version.fromString(currentVersion))
}

/** The Store's `major.minor.build.revision` as the app's `major.minor.patch`: the revision is always 0. */
fun storeSemanticVersion(storeVersion: String): String = storeVersion.split('.').take(3).joinToString(".")
