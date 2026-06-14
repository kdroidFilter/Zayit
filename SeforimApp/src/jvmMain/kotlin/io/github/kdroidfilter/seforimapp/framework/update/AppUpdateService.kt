package io.github.kdroidfilter.seforimapp.framework.update

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.nativehttp.NativeHttpClient
import dev.nucleusframework.updater.DownloadProgress
import dev.nucleusframework.updater.NucleusUpdater
import dev.nucleusframework.updater.UpdateInfo
import dev.nucleusframework.updater.UpdateLevel
import dev.nucleusframework.updater.UpdateResult
import dev.nucleusframework.updater.provider.GenericProvider
import dev.nucleusframework.updater.provider.GitHubProvider
import io.github.kdroidfilter.seforimapp.logger.errorln
import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/** How an available update is surfaced to the user. */
enum class UpdateMode {
    /** Downloaded silently and installed on app close, no UI (Win/Mac PATCH only). */
    SILENT_ON_CLOSE,

    /** Surfaced via the title-bar icon + dialog; requires explicit user action. */
    PROMPT,
}

/** UI-facing state of the auto-update flow. */
@Immutable
sealed interface UpdateUiState {
    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    data object UpToDate : UpdateUiState

    data class Available(
        val version: String,
        val level: UpdateLevel,
        val mode: UpdateMode,
        val needsDbWarning: Boolean,
    ) : UpdateUiState

    data class Downloading(
        val version: String,
        val level: UpdateLevel,
        val mode: UpdateMode,
        val needsDbWarning: Boolean,
        val percent: Int,
    ) : UpdateUiState

    data class ReadyToInstall(
        val version: String,
        val level: UpdateLevel,
        val mode: UpdateMode,
        val needsDbWarning: Boolean,
        val file: File,
    ) : UpdateUiState

    data class Error(
        val message: String,
    ) : UpdateUiState
}

/** True when the title-bar update badge should be shown (PROMPT updates only). */
val UpdateUiState.showTitleBarIcon: Boolean
    get() =
        when (this) {
            is UpdateUiState.Available -> mode == UpdateMode.PROMPT
            is UpdateUiState.Downloading -> mode == UpdateMode.PROMPT
            is UpdateUiState.ReadyToInstall -> mode == UpdateMode.PROMPT
            else -> false
        }

/** The available version string, if any, regardless of mode (used by the settings banner). */
val UpdateUiState.availableVersion: String?
    get() =
        when (this) {
            is UpdateUiState.Available -> version
            is UpdateUiState.Downloading -> version
            is UpdateUiState.ReadyToInstall -> version
            else -> null
        }

// --- Pure decision functions (no I/O) — see UpdateDecisionTest ---

/**
 * PATCH (0.0.x) updates install transparently on Windows/macOS; everything else
 * (MINOR/MAJOR, any Linux, pre-release) is surfaced through the prompt UI.
 */
fun resolveUpdateMode(
    level: UpdateLevel,
    os: Platform,
): UpdateMode =
    if (level == UpdateLevel.PATCH && (os == Platform.Windows || os == Platform.MacOS)) {
        UpdateMode.SILENT_ON_CLOSE
    } else {
        UpdateMode.PROMPT
    }

/**
 * MINOR/MAJOR app updates may raise the minimum required database version and trigger the
 * ~3.5 GB database update on next launch, so the user is warned beforehand on every platform.
 */
fun needsDbWarning(level: UpdateLevel): Boolean = level == UpdateLevel.MINOR || level == UpdateLevel.MAJOR

/** PATCH updates are pre-downloaded at startup on every platform so install is instant. */
fun shouldPreDownload(level: UpdateLevel): Boolean = level == UpdateLevel.PATCH

/** Thin abstraction over [NucleusUpdater] so the service can be unit-tested with a fake. */
interface Updater {
    val currentVersion: String

    fun isUpdateSupported(): Boolean

    suspend fun checkForUpdates(): UpdateResult

    fun downloadUpdate(info: UpdateInfo): Flow<DownloadProgress>

    fun installAndRestart(file: File)

    fun installAndQuit(file: File)
}

/** Real [Updater] backed by Nucleus; honours dry-run by logging instead of launching the installer. */
class NucleusUpdaterAdapter(
    private val delegate: NucleusUpdater,
    private val dryRun: Boolean,
) : Updater {
    override val currentVersion: String get() = delegate.currentVersion

    override fun isUpdateSupported(): Boolean = delegate.isUpdateSupported()

    override suspend fun checkForUpdates(): UpdateResult = delegate.checkForUpdates()

    override fun downloadUpdate(info: UpdateInfo): Flow<DownloadProgress> = delegate.downloadUpdate(info)

    override fun installAndRestart(file: File) {
        if (dryRun) {
            infoln { "[update][dry-run] installAndRestart(${file.absolutePath})" }
            return
        }
        delegate.installAndRestart(file)
    }

    override fun installAndQuit(file: File) {
        if (dryRun) {
            infoln { "[update][dry-run] installAndQuit(${file.absolutePath})" }
            return
        }
        delegate.installAndQuit(file)
    }
}

/**
 * Dev/QA overrides resolved from environment variables (falling back to system properties).
 * All are no-ops in normal runs.
 */
data class AppUpdateConfig(
    val forceVersion: String? = null,
    val feedUrl: String? = null,
    val dryRun: Boolean = false,
    val fakeState: String? = null,
    val executableType: String? = null,
) {
    companion object {
        fun fromEnv(): AppUpdateConfig =
            AppUpdateConfig(
                forceVersion = read("SEFORIMAPP_UPDATE_FORCE_VERSION"),
                feedUrl = read("SEFORIMAPP_UPDATE_FEED_URL"),
                dryRun = flag("SEFORIMAPP_UPDATE_DRY_RUN"),
                fakeState = read("SEFORIMAPP_UPDATE_FAKE_STATE")?.lowercase(),
                executableType = read("SEFORIMAPP_UPDATE_EXECUTABLE_TYPE"),
            )

        private fun read(key: String): String? = (System.getenv(key) ?: System.getProperty(key))?.trim()?.takeIf { it.isNotEmpty() }

        private fun flag(key: String): Boolean =
            read(key)?.let { it == "1" || it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) } ?: false
    }
}

/**
 * Orchestrates the semi-automatic auto-update flow on top of Nucleus.
 *
 * Behaviour:
 * - Win/Mac PATCH → pre-download at startup, then [installPendingOnClose] silently on close.
 * - Linux PATCH → pre-download at startup, then prompt (icon + dialog) since `pkexec` needs interaction.
 * - Any MINOR/MAJOR → prompt; download on user action; warns about a possible ~3.5 GB DB update.
 *
 * [checkOnStartup] is suspending (driven by the startup `LaunchedEffect`); [startDownload] launches
 * on the app-lifetime [scope] so a user-triggered download survives the dialog being closed.
 */
@Stable
class AppUpdateService(
    private val updaterProvider: () -> Updater,
    private val config: AppUpdateConfig,
    private val os: Platform,
    // App-lifetime scope so a user-triggered download keeps running after the dialog is closed.
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val updater: Updater by lazy(updaterProvider)

    private val _state = MutableStateFlow(initialFakeState() ?: UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _dialogVisible = MutableStateFlow(false)
    val dialogVisible: StateFlow<Boolean> = _dialogVisible.asStateFlow()

    private var pendingInfo: UpdateInfo? = null
    private var downloadJob: Job? = null

    /** Checks for updates once at startup and pre-downloads PATCH updates. */
    suspend fun checkOnStartup() {
        if (config.fakeState != null) return // state pre-seeded by initialFakeState()
        if (_state.value != UpdateUiState.Idle) return
        _state.value = UpdateUiState.Checking
        when (val result = updater.checkForUpdates()) {
            is UpdateResult.NotAvailable -> _state.value = UpdateUiState.UpToDate
            is UpdateResult.Error -> {
                errorln { "[update] check failed: ${result.exception.message}" }
                _state.value = UpdateUiState.Error(result.exception.message ?: "update check failed")
            }
            is UpdateResult.Available -> {
                pendingInfo = result.info
                val mode = resolveUpdateMode(result.level, os)
                val warn = needsDbWarning(result.level)
                infoln { "[update] available v${result.info.version} level=${result.level} mode=$mode" }
                _state.value = UpdateUiState.Available(result.info.version, result.level, mode, warn)
                if (shouldPreDownload(result.level)) {
                    download(result.info, result.level, mode, warn)
                }
            }
        }
    }

    /**
     * Re-runs the update check on user request (from the settings screen). Resets to [UpdateUiState.Idle]
     * first so [checkOnStartup] runs again; no-op while a check/download/install is already in progress.
     */
    suspend fun recheck() {
        if (config.fakeState != null) return
        when (_state.value) {
            is UpdateUiState.Checking,
            is UpdateUiState.Downloading,
            is UpdateUiState.ReadyToInstall,
            -> return
            else -> {
                _state.value = UpdateUiState.Idle
                checkOnStartup()
            }
        }
    }

    /**
     * Triggers the download for a prompt update (MINOR/MAJOR) when the user confirms in the dialog.
     * Runs on the service scope (not the dialog's), so the download continues in the background even
     * if the user closes the dialog; progress keeps flowing into [state].
     */
    fun startDownload() {
        val current = _state.value
        if (current !is UpdateUiState.Available) return
        if (downloadJob?.isActive == true) return
        val info = pendingInfo ?: return
        downloadJob = launchDownload(scope, info, current.level, current.mode, current.needsDbWarning)
    }

    private fun launchDownload(
        @StructuredScope scope: CoroutineScope,
        info: UpdateInfo,
        level: UpdateLevel,
        mode: UpdateMode,
        warn: Boolean,
    ): Job = scope.launch { download(info, level, mode, warn) }

    private suspend fun download(
        info: UpdateInfo,
        level: UpdateLevel,
        mode: UpdateMode,
        warn: Boolean,
    ) {
        try {
            infoln { "[update] download start v${info.version}" }
            updater.downloadUpdate(info).collect { progress ->
                val file = progress.file
                if (file != null) {
                    infoln { "[update] download ready v${info.version} -> ${file.name}" }
                    _state.value = UpdateUiState.ReadyToInstall(info.version, level, mode, warn, file)
                } else {
                    _state.value = UpdateUiState.Downloading(info.version, level, mode, warn, progress.percent.toInt())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            errorln { "[update] download failed: ${e.message}" }
            _state.value = UpdateUiState.Error(e.message ?: "download failed")
        }
    }

    /** Installs the ready update and relaunches the app (from the dialog button). */
    fun installAndRestart() {
        val current = _state.value
        if (current is UpdateUiState.ReadyToInstall) {
            if (config.fakeState != null) {
                infoln { "[update][fake] installAndRestart(${current.file})" }
                return
            }
            updater.installAndRestart(current.file)
        }
    }

    /**
     * Called from the window close handler. If a silent (Win/Mac PATCH) update is ready, launches
     * the installer (which exits the process) and returns `true`; otherwise a no-op returning `false`.
     */
    fun installPendingOnClose(): Boolean {
        val current = _state.value
        return if (current is UpdateUiState.ReadyToInstall && current.mode == UpdateMode.SILENT_ON_CLOSE) {
            if (config.fakeState != null) {
                infoln { "[update][fake] installAndQuit(${current.file})" }
                return false
            }
            infoln { "[update] applying silent update on close: v${current.version}" }
            updater.installAndQuit(current.file)
            true
        } else {
            false
        }
    }

    fun openDialog() {
        _dialogVisible.value = true
    }

    fun closeDialog() {
        _dialogVisible.value = false
    }

    fun isUpdateSupported(): Boolean = config.fakeState != null || updater.isUpdateSupported()

    private fun initialFakeState(): UpdateUiState? {
        val fake = config.fakeState ?: return null
        val version = config.forceVersion ?: "9.9.9"
        return when (fake) {
            "patch" ->
                UpdateUiState.Available(version, UpdateLevel.PATCH, resolveUpdateMode(UpdateLevel.PATCH, os), false)
            "minor" ->
                UpdateUiState.Available(version, UpdateLevel.MINOR, UpdateMode.PROMPT, true)
            "major" ->
                UpdateUiState.Available(version, UpdateLevel.MAJOR, UpdateMode.PROMPT, true)
            "downloading" ->
                UpdateUiState.Downloading(version, UpdateLevel.MINOR, UpdateMode.PROMPT, true, 42)
            "ready" ->
                UpdateUiState.ReadyToInstall(version, UpdateLevel.MINOR, UpdateMode.PROMPT, true, File("fake-installer"))
            else -> null
        }
    }

    companion object {
        const val DOWNLOAD_URL = "https://kdroidfilter.github.io/Zayit/download"

        private const val GITHUB_OWNER = "kdroidFilter"
        private const val GITHUB_REPO = "Zayit"

        /** Builds the production service wired to GitHub releases through the native SSL client. */
        fun create(
            config: AppUpdateConfig = AppUpdateConfig.fromEnv(),
            os: Platform = Platform.Current,
        ): AppUpdateService =
            AppUpdateService(
                updaterProvider = {
                    // Releases ship several assets per platform; force the auto-update format so the
                    // feed picks the silent installer (NSIS on Windows, .zip on macOS) and not the
                    // user-facing Rust wrapper exe / .dmg. The env override still wins when set.
                    val resolvedExecutableType =
                        config.executableType
                            ?: when (os) {
                                Platform.Windows -> "nsis"
                                Platform.MacOS -> "zip"
                                else -> null
                            }
                    val updater =
                        NucleusUpdater {
                            provider =
                                config.feedUrl
                                    ?.let { GenericProvider(it) }
                                    ?: GitHubProvider(owner = GITHUB_OWNER, repo = GITHUB_REPO)
                            httpClient = NativeHttpClient.create()
                            channel = "latest"
                            config.forceVersion?.let { currentVersion = it }
                            resolvedExecutableType?.let { executableType = it }
                        }
                    NucleusUpdaterAdapter(updater, dryRun = config.dryRun)
                },
                config = config,
                os = os,
            )
    }
}
