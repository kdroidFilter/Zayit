package io.github.kdroidfilter.seforimapp.framework.update

import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.store.microsoft.StoreUpdateCheck
import dev.nucleusframework.store.microsoft.StoreUpdateProgress
import dev.nucleusframework.store.microsoft.StoreUpdateState
import dev.nucleusframework.updater.DownloadProgress
import dev.nucleusframework.updater.UpdateInfo
import dev.nucleusframework.updater.UpdateLevel
import dev.nucleusframework.updater.UpdateResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** The Microsoft Store install: updates from the Store, never the GitHub feed. */
class StoreUpdatesTest {
    private class FakeStore(
        private val check: () -> StoreUpdateCheck,
        private val progress: List<StoreUpdateProgress> = emptyList(),
    ) : StoreUpdates {
        var installs = 0

        /** The service's state right after each step the Store reported, read once the service handled it. */
        val observed = mutableListOf<UpdateUiState>()
        var stateOf: () -> UpdateUiState = { UpdateUiState.Idle }

        override val currentVersion = "0.6.12"

        override suspend fun check(): StoreUpdateCheck = check.invoke()

        override fun install(): Flow<StoreUpdateProgress> {
            installs++
            return flow {
                for (step in progress) {
                    emit(step)
                    observed += stateOf()
                }
            }
        }
    }

    /** The GitHub feed must not be asked for a Store install. */
    private object NoFeed : Updater {
        override val currentVersion = "0.6.12"

        override fun isUpdateSupported() = false

        override suspend fun checkForUpdates(): UpdateResult = fail("the feed was checked")

        override fun downloadUpdate(info: UpdateInfo): Flow<DownloadProgress> = flow { fail("the feed was downloaded") }

        override fun installAndRestart(file: File) = fail("an installer was run")

        override fun installAndQuit(file: File) = fail("an installer was run")
    }

    private fun TestScope.service(store: StoreUpdates) =
        AppUpdateService(
            updaterProvider = { NoFeed },
            config = AppUpdateConfig(),
            os = Platform.Windows,
            storeUpdates = store,
            scope = this,
        )

    private fun progress(
        state: StoreUpdateState,
        fraction: Double,
    ) = StoreUpdateProgress(state, fraction, 0, 0)

    @Test
    fun `the level is read from the Store's four-part version`() {
        assertEquals(UpdateLevel.PATCH, storeUpdateLevel("0.6.12", "0.6.13.0"))
        assertEquals(UpdateLevel.MINOR, storeUpdateLevel("0.6.12", "0.7.0.0"))
        assertEquals(UpdateLevel.MAJOR, storeUpdateLevel("0.6.12", "1.0.0.0"))
        assertEquals(UpdateLevel.MINOR, storeUpdateLevel("0.6.12", null))
    }

    @Test
    fun `a patch on the Store prompts and is not pre-downloaded`() =
        runTest {
            val store = FakeStore({ StoreUpdateCheck.Available("0.6.13.0", isMandatory = false) })
            val svc = service(store)

            svc.checkOnStartup()
            advanceUntilIdle()

            val state = svc.state.value
            assertIs<UpdateUiState.Available>(state)
            assertEquals("0.6.13", state.version)
            assertEquals(UpdateLevel.PATCH, state.level)
            assertEquals(UpdateMode.PROMPT, state.mode)
            assertFalse(state.needsDbWarning)
            assertTrue(state.showTitleBarIcon)
            assertEquals(0, store.installs)
            assertFalse(svc.dialogVisible.value)
            assertFalse(svc.installPendingOnClose())
            assertTrue(svc.isUpdateSupported())
        }

    @Test
    fun `a mandatory update opens the dialog`() =
        runTest {
            val svc = service(FakeStore({ StoreUpdateCheck.Available("0.7.0.0", isMandatory = true) }))

            svc.checkOnStartup()

            assertTrue(svc.dialogVisible.value)
            assertTrue((svc.state.value as UpdateUiState.Available).needsDbWarning)
        }

    @Test
    fun `installing reports the Store's progress`() =
        runTest {
            val store =
                FakeStore(
                    check = { StoreUpdateCheck.Available("0.6.13.0", isMandatory = false) },
                    progress =
                        listOf(
                            progress(StoreUpdateState.Downloading, 0.4),
                            progress(StoreUpdateState.Installing, 0.9),
                            progress(StoreUpdateState.Completed, 1.0),
                        ),
                )
            val svc = service(store)
            store.stateOf = { svc.state.value }
            svc.checkOnStartup()

            svc.startDownload()
            advanceUntilIdle()

            assertEquals(1, store.installs)
            assertEquals(listOf(40, 90), store.observed.filterIsInstance<UpdateUiState.Downloading>().map { it.percent })
            assertEquals(UpdateUiState.UpToDate, svc.state.value)
        }

    @Test
    fun `declining the Store's dialog keeps the update available`() =
        runTest {
            val store =
                FakeStore(
                    check = { StoreUpdateCheck.Available("0.6.13.0", isMandatory = false) },
                    progress = listOf(progress(StoreUpdateState.Cancelled, 0.0)),
                )
            val svc = service(store)
            svc.checkOnStartup()

            svc.startDownload()
            advanceUntilIdle()

            assertIs<UpdateUiState.Available>(svc.state.value)
        }

    @Test
    fun `up to date, and a failing Store, are reported`() =
        runTest {
            val upToDate = service(FakeStore({ StoreUpdateCheck.UpToDate }))
            upToDate.checkOnStartup()
            assertEquals(UpdateUiState.UpToDate, upToDate.state.value)

            val failing = service(FakeStore({ error("Store unreachable") }))
            failing.checkOnStartup()
            assertEquals(UpdateUiState.Error("Store unreachable"), failing.state.value)
        }
}
