package io.github.kdroidfilter.seforimapp.backup.drive

import io.github.kdroidfilter.seforimapp.backup.BackupManager
import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class DriveSyncState(
    // Null while no account is connected
    val account: DriveSyncAccount? = null,
    val isConnecting: Boolean = false,
    val operation: Operation? = null,
    val error: DriveSyncError? = null,
    // A Drive backup was staged to replace this device's data: the app is restarting
    val restoreStaged: Boolean = false,
) {
    enum class Operation { BackingUp, Restoring }
}

data class DriveSyncAccount(
    val email: String?,
    val lastBackupEpochMs: Long?,
    // Set while a Drive backup made elsewhere waits for the user to restore or replace it
    val pendingRemoteBackupEpochMs: Long?,
)

enum class DriveSyncError { ConnectFailed, PermissionNotGranted, DriveUnavailable, BackupFailed, RestoreFailed, NoBackup, Revoked }

/**
 * The Google Drive account of the user and what is particular to it: sign-in, the choice
 * between a Drive backup and this device's data, and the automatic backup (on quit, and at
 * most once a day while the app runs). The data itself goes through [backupManager], to the
 * hidden app folder ([GoogleDriveDestination]); a restore is only staged, and
 * [DriveSyncConfig.onRestoreStaged] restarts the app to apply it.
 *
 * The operations the user starts run in this app-wide object's own scope, not the caller's:
 * closing the settings window must not cancel a sign-in waiting in the browser.
 */
class GoogleDriveSync(
    httpClient: HttpClient,
    private val backupManager: BackupManager,
    private val config: DriveSyncConfig,
) {
    /** False when the build carries no OAuth client: the sync is then hidden. */
    val isAvailable: Boolean = config.clientId.isNotBlank() && config.clientSecret.isNotBlank()

    // The app's client, with timeouts fit for a backup of a few dozen MB on a slow uplink
    private val http =
        httpClient.config {
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT.inWholeMilliseconds
                socketTimeoutMillis = SOCKET_TIMEOUT.inWholeMilliseconds
            }
        }
    private val oauth = GoogleOAuthClient(http, config.clientId, config.clientSecret)
    private val drive = DriveFilesApi(http)
    private val store by lazy { DriveAccountStore(config.storageDirectory()) }

    // Serializes the operations on the account and on Drive (a manual backup against the automatic one).
    private val mutex = Mutex()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectJob: Job? = null

    // Both quit paths (the app's own and the system's) ask for it: one backup is enough.
    private val quitBackupDone = AtomicBoolean(false)

    // Guarded: the storage directory may not be resolvable outside the app (unit tests).
    private val _state =
        MutableStateFlow(DriveSyncState(account = if (isAvailable) runCatching { store.load()?.toPublic() }.getOrNull() else null))
    val state: StateFlow<DriveSyncState> = _state.asStateFlow()

    /**
     * Signs in through the system browser. Like AndBible, a backup already on Drive is never
     * restored nor replaced without asking: it waits for the user's choice, with the automatic
     * backup paused.
     */
    fun connect() {
        if (connectJob?.isActive == true) return
        connectJob = scope.launch { signIn() }
    }

    fun cancelConnect() {
        connectJob?.cancel()
    }

    fun disconnect() {
        scope.launch {
            mutex.withLock {
                store.load()?.let { oauth.revoke(it.refreshToken) }
                store.clear()
            }
            _state.value = DriveSyncState()
        }
    }

    /** Backs the current data up as the latest Drive backup, replacing whatever Drive holds, and turns the automatic backup on. */
    fun backupNow() {
        scope.launch {
            runOperation(DriveSyncState.Operation.BackingUp, DriveSyncError.BackupFailed) {
                upload(unlessUnchangedFrom = null, expectedLatestEpochMs = null)
            }
        }
    }

    /** Stages the latest Drive backup to replace this device's data, then restarts the app to apply it. */
    fun restore() {
        scope.launch {
            val staged = runOperation(DriveSyncState.Operation.Restoring, DriveSyncError.RestoreFailed) { stageRestore() } ?: false
            if (staged) {
                _state.update { it.copy(restoreStaged = true) }
                config.onRestoreStaged()
            }
        }
    }

    private suspend fun signIn() {
        _state.update { it.copy(isConnecting = true, error = null) }
        try {
            val account = authorize()
            val remote = drive.findLatest(account.accessToken, config.backupFileName)
            mutex.withLock {
                // Signing in again, possibly with another account: the previous access is given back.
                store.load()?.takeIf { it.refreshToken != account.refreshToken }?.let { oauth.revoke(it.refreshToken) }
                save(
                    if (remote == null) {
                        account
                    } else {
                        account.copy(autoBackupArmed = false, pendingRemoteBackupEpochMs = remote.modifiedEpochMs)
                    },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            warnln { "[Drive] Connection failed: ${e.message}" }
            val error =
                when (e) {
                    is DrivePermissionNotGrantedException -> DriveSyncError.PermissionNotGranted
                    is DriveAccessDeniedException -> DriveSyncError.DriveUnavailable
                    else -> DriveSyncError.ConnectFailed
                }
            _state.update { it.copy(error = error) }
        } finally {
            _state.update { it.copy(isConnecting = false) }
        }
    }

    /** @return false when Drive holds no backup. */
    private suspend fun stageRestore(): Boolean {
        val destination = destinationFor(expectedLatestEpochMs = null)
        if (!backupManager.restore(destination)) {
            _state.update { it.copy(error = DriveSyncError.NoBackup) }
            return false
        }
        updateAccount {
            it.copy(
                lastBackupEpochMs = destination.remote?.modifiedEpochMs,
                lastFingerprint = null,
                autoBackupArmed = false,
                armWhenRestored = true,
                pendingRemoteBackupEpochMs = null,
            )
        }
        return true
    }

    /** Checks every hour whether a day passed since the last backup or check; runs until cancelled. */
    suspend fun runAutoBackup() {
        if (!isAvailable) return
        withContext(Dispatchers.IO) {
            delay(AUTO_BACKUP_STARTUP_DELAY)
            while (true) {
                val account = mutex.withLock { store.load() }
                val last = maxOf(account?.lastBackupEpochMs ?: 0L, account?.lastAutoCheckEpochMs ?: 0L)
                if (System.currentTimeMillis() - last >= AUTO_BACKUP_INTERVAL.inWholeMilliseconds) backupIfChanged()
                delay(AUTO_BACKUP_CHECK_INTERVAL)
            }
        }
    }

    /**
     * Backs the session's changes up on the way out, once, waiting at most [QUIT_BACKUP_TIMEOUT]: in a shutdown hook,
     * so the windows close at once and the process only lingers, out of sight, to finish the upload.
     */
    fun backupOnQuit() {
        if (!isAvailable || _state.value.account == null || !quitBackupDone.compareAndSet(false, true)) return
        val backup = { runBlocking(Dispatchers.IO) { withTimeoutOrNull(QUIT_BACKUP_TIMEOUT) { backupIfChanged() } } }
        try {
            Runtime.getRuntime().addShutdownHook(Thread({ backup() }, "drive-quit-backup"))
        } catch (_: IllegalStateException) {
            // Already shutting down (a system quit): back up now
            backup()
        }
    }

    private suspend fun backupIfChanged() {
        runCatching {
            mutex.withLock {
                var account = store.load() ?: return@withLock
                if (account.armWhenRestored) {
                    // Not applied yet (the app did not restart, or applying failed and will be retried).
                    if (backupManager.hasPendingRestore()) return@withLock
                    account = account.copy(autoBackupArmed = true, armWhenRestored = false).also(::save)
                }
                if (!account.autoBackupArmed) return@withLock
                try {
                    upload(unlessUnchangedFrom = account.lastFingerprint, expectedLatestEpochMs = account.lastBackupEpochMs ?: 0L)
                } catch (e: RemoteBackupChangedException) {
                    // Another device backed up meanwhile: like on sign-in, the user chooses.
                    updateAccount { it.copy(autoBackupArmed = false, pendingRemoteBackupEpochMs = e.remoteEpochMs) }
                }
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            warnln { "[Drive] Automatic backup failed: ${e.message}" }
            if (e !is AuthorizationRevokedException) _state.update { it.copy(error = DriveSyncError.BackupFailed) }
        }
    }

    /** Runs [block] on the connected account, publishing its progress and its failure as [error]. */
    private suspend fun <T> runOperation(
        operation: DriveSyncState.Operation,
        error: DriveSyncError,
        block: suspend () -> T,
    ): T? {
        if (_state.value.operation != null) return null
        _state.update { it.copy(operation = operation, error = null) }
        return try {
            withContext(Dispatchers.IO) {
                mutex.withLock { if (store.load() == null) null else block() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            warnln { "[Drive] $operation failed: ${e.message}" }
            if (e !is AuthorizationRevokedException) _state.update { it.copy(error = error) }
            null
        } finally {
            _state.update { it.copy(operation = null) }
        }
    }

    /**
     * Backs the data up, unless it still has the fingerprint [unlessUnchangedFrom], and unless Drive
     * holds a backup newer than [expectedLatestEpochMs] (null: replace it anyway). Called under [mutex].
     */
    private suspend fun upload(
        unlessUnchangedFrom: String?,
        expectedLatestEpochMs: Long?,
    ) {
        if (backupManager.hasPendingRestore()) {
            warnln { "[Drive] A restore waits for the restart: nothing is backed up until then" }
            return
        }
        val destination = destinationFor(expectedLatestEpochMs)
        val fingerprint = backupManager.backup(destination, unlessUnchangedFrom)
        val now = System.currentTimeMillis()
        val remote = destination.remote
        updateAccount {
            if (fingerprint == null || remote == null) {
                it.copy(lastAutoCheckEpochMs = now)
            } else {
                it.copy(
                    lastBackupEpochMs = remote.modifiedEpochMs,
                    lastAutoCheckEpochMs = now,
                    lastFingerprint = fingerprint,
                    autoBackupArmed = true,
                    armWhenRestored = false,
                    pendingRemoteBackupEpochMs = null,
                )
            }
        }
        if (fingerprint != null) infoln { "[Drive] Backed up" }
    }

    // The token is refreshed only when the destination actually calls Drive.
    private fun destinationFor(expectedLatestEpochMs: Long?) =
        GoogleDriveDestination(drive, ::freshAccessToken, config.backupFileName, expectedLatestEpochMs)

    private suspend fun freshAccessToken(): String = refreshed(store.load() ?: error("No Drive account")).accessToken

    private fun updateAccount(transform: (DriveAccount) -> DriveAccount) {
        store.load()?.let(transform)?.let(::save)
    }

    /** [account] with a valid access token; drops the account when Google revoked it. */
    private suspend fun refreshed(account: DriveAccount): DriveAccount {
        val now = System.currentTimeMillis()
        if (now < account.expiresAtEpochMs) return account
        val tokens =
            try {
                oauth.refresh(account.refreshToken)
            } catch (e: AuthorizationRevokedException) {
                store.clear()
                _state.value = DriveSyncState(error = DriveSyncError.Revoked)
                throw e
            }
        return account
            .copy(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken ?: account.refreshToken,
                expiresAtEpochMs = now + tokens.expiresIn * 1000 - TOKEN_EXPIRY_MARGIN_MS,
            ).also(::save)
    }

    private suspend fun authorize(): DriveAccount {
        val verifier = Pkce.newVerifier()
        val oauthState = Pkce.newVerifier().take(OAUTH_STATE_LENGTH)
        return LoopbackCodeReceiver(config.signInPage).use { receiver ->
            config.openUrl(oauth.authorizationUrl(receiver.redirectUri, oauthState, Pkce.challenge(verifier)))
            val code = withTimeoutOrNull(SIGN_IN_TIMEOUT) { receiver.awaitCode(oauthState) } ?: error("Sign-in timed out")
            val tokens = oauth.exchangeCode(code, verifier, receiver.redirectUri)
            if (!tokens.grantsDriveAppData) {
                oauth.revoke(tokens.refreshToken ?: tokens.accessToken)
                throw DrivePermissionNotGrantedException()
            }
            DriveAccount(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken ?: error("Google returned no refresh token"),
                expiresAtEpochMs = System.currentTimeMillis() + tokens.expiresIn * 1000 - TOKEN_EXPIRY_MARGIN_MS,
                email = oauth.fetchEmail(tokens.accessToken),
            )
        }
    }

    private fun save(account: DriveAccount) {
        store.save(account)
        _state.update { it.copy(account = account.toPublic()) }
    }

    private fun DriveAccount.toPublic() = DriveSyncAccount(email, lastBackupEpochMs, pendingRemoteBackupEpochMs)

    private companion object {
        const val OAUTH_STATE_LENGTH = 32
        const val TOKEN_EXPIRY_MARGIN_MS = 60_000L
        val SIGN_IN_TIMEOUT = 5.minutes
        val QUIT_BACKUP_TIMEOUT = 10.seconds
        val AUTO_BACKUP_STARTUP_DELAY = 2.minutes
        val AUTO_BACKUP_CHECK_INTERVAL = 1.hours
        val AUTO_BACKUP_INTERVAL = 1.days
        val REQUEST_TIMEOUT = 10.minutes
        val SOCKET_TIMEOUT = 1.minutes
    }
}

/** The user unchecked the Drive permission on Google's consent screen. */
internal class DrivePermissionNotGrantedException : IllegalStateException("The Drive permission was not granted")
