package io.github.kdroidfilter.seforimapp.framework.update

import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.updater.DownloadProgress
import dev.nucleusframework.updater.UpdateFile
import dev.nucleusframework.updater.UpdateInfo
import dev.nucleusframework.updater.UpdateLevel
import dev.nucleusframework.updater.UpdateResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** N2 — service orchestration with a fake updater (no network, no installer). */
class AppUpdateServiceTest {
    private val installerFile = File("/tmp/zayit-installer")

    private fun updateInfo(version: String): UpdateInfo {
        val file = UpdateFile(url = "http://x/$version", sha512 = "abc", size = 100, fileName = "zayit-$version")
        return UpdateInfo(version = version, releaseDate = "", files = listOf(file), currentFile = file)
    }

    private inner class FakeUpdater(
        private val result: UpdateResult,
    ) : Updater {
        var restartCalls = 0
        var quitCalls = 0

        override val currentVersion = "1.0.0"

        override fun isUpdateSupported() = true

        override suspend fun checkForUpdates(): UpdateResult = result

        override fun downloadUpdate(info: UpdateInfo): Flow<DownloadProgress> =
            flow {
                emit(DownloadProgress(50, 100, 50.0))
                emit(DownloadProgress(100, 100, 100.0, installerFile))
            }

        override fun installAndRestart(file: File) {
            restartCalls++
        }

        override fun installAndQuit(file: File) {
            quitCalls++
        }
    }

    private fun service(
        result: UpdateResult,
        os: Platform,
        fake: FakeUpdater = FakeUpdater(result),
    ): Pair<AppUpdateService, FakeUpdater> = AppUpdateService(updaterProvider = { fake }, config = AppUpdateConfig(), os = os) to fake

    @Test
    fun `patch on windows pre-downloads and installs silently on close`() =
        runTest {
            val (svc, fake) = service(UpdateResult.Available(updateInfo("1.0.1"), UpdateLevel.PATCH), Platform.Windows)
            svc.checkOnStartup()

            val state = svc.state.value
            assertIs<UpdateUiState.ReadyToInstall>(state)
            assertEquals(UpdateMode.SILENT_ON_CLOSE, state.mode)
            assertFalse(state.showTitleBarIcon) // silent → no badge

            assertTrue(svc.installPendingOnClose())
            assertEquals(1, fake.quitCalls)
            assertEquals(0, fake.restartCalls)
        }

    @Test
    fun `patch on linux pre-downloads but prompts`() =
        runTest {
            val (svc, _) = service(UpdateResult.Available(updateInfo("1.0.1"), UpdateLevel.PATCH), Platform.Linux)
            svc.checkOnStartup()

            val state = svc.state.value
            assertIs<UpdateUiState.ReadyToInstall>(state)
            assertEquals(UpdateMode.PROMPT, state.mode)
            assertTrue(state.showTitleBarIcon)
            assertFalse(svc.installPendingOnClose()) // not silent → no auto-install on close
        }

    @Test
    fun `minor stays available until user starts download then installs and restarts`() =
        runTest {
            val fake = FakeUpdater(UpdateResult.Available(updateInfo("1.1.0"), UpdateLevel.MINOR))
            val svc =
                AppUpdateService(
                    updaterProvider = { fake },
                    config = AppUpdateConfig(),
                    os = Platform.Windows,
                    scope = this,
                )
            svc.checkOnStartup()

            val available = svc.state.value
            assertIs<UpdateUiState.Available>(available)
            assertEquals(UpdateMode.PROMPT, available.mode)
            assertTrue(available.needsDbWarning)
            assertTrue(available.showTitleBarIcon)

            // Download runs on the (background) service scope, not the caller's — advance it.
            svc.startDownload()
            advanceUntilIdle()
            assertIs<UpdateUiState.ReadyToInstall>(svc.state.value)

            svc.installAndRestart()
            assertEquals(1, fake.restartCalls)
        }

    @Test
    fun `up to date when no update available`() =
        runTest {
            val (svc, _) = service(UpdateResult.NotAvailable, Platform.MacOS)
            svc.checkOnStartup()
            assertEquals(UpdateUiState.UpToDate, svc.state.value)
        }

    @Test
    fun `dialog visibility toggles`() {
        val (svc, _) = service(UpdateResult.NotAvailable, Platform.Windows)
        assertFalse(svc.dialogVisible.value)
        svc.openDialog()
        assertTrue(svc.dialogVisible.value)
        svc.closeDialog()
        assertFalse(svc.dialogVisible.value)
    }
}
