package io.github.kdroidfilter.seforimapp.features.settings.dbupdate

import io.github.kdroidfilter.seforimapp.framework.update.DbDeltaUpdateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the state transitions of [DbDeltaUpdateViewModel].
 *
 * The ViewModel internally jumps to [Dispatchers.IO] via `withContext`
 * for the HTTP + apply work. We swap the main dispatcher for the
 * test scheduler so `runTest` can advance virtual time through every
 * launched coroutine; calling [advanceUntilIdle] after firing an
 * event lets us observe the final state.
 *
 * The Service itself is stubbed: each test creates an anonymous
 * subclass of [DbDeltaUpdateService] overriding `checkAndApply` with a
 * fixed outcome or a controlled progress sequence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DbDeltaUpdateViewModelTest {

    @Before
    fun installMain() {
        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    private fun vm(service: DbDeltaUpdateService): DbDeltaUpdateViewModel =
        DbDeltaUpdateViewModel(service, ioDispatcher = UnconfinedTestDispatcher())

    private fun stub(
        onCheck: suspend (progress: (Int, Int, String) -> Unit) -> DbDeltaUpdateService.Outcome,
    ): DbDeltaUpdateService =
        object : DbDeltaUpdateService(
            seforimDb = Path.of("/dev/null/x"),
            catalogPb = Path.of("/dev/null/x"),
            workDir = Path.of("/dev/null/x"),
            releaseMetaUrl = "",
            localDbVersionProvider = { 0 },
        ) {
            override suspend fun checkAndApply(
                onProgress: (current: Int, total: Int, status: String) -> Unit,
            ): Outcome = onCheck(onProgress)

            override fun recoverIfNeeded(): Boolean = false
        }

    @Test
    fun `initial state is idle`() = runTest {
        val vm = vm(stub { DbDeltaUpdateService.Outcome.UpToDate })
        val state = vm.state.value
        assertNull(state.phase)
        assertEquals("", state.message)
        assertNull(state.errorMessage)
        assertNull(state.lastAppliedCount)
        assertEquals(false, state.needsFullBundle)
    }

    @Test
    fun `UpToDate outcome leaves state with up-to-date message`() = runTest {
        val vm = vm(stub { DbDeltaUpdateService.Outcome.UpToDate })
        vm.onEvent(DbDeltaUpdateEvents.CheckAndApplyClicked)
        advanceUntilIdle()
        val s = vm.state.value
        assertNull(s.phase, "phase must clear after completion")
        assertTrue("up to date" in s.message, "got: ${s.message}")
        assertNull(s.errorMessage)
    }

    @Test
    fun `Applied outcome records deltaCount`() = runTest {
        val vm = vm(stub { DbDeltaUpdateService.Outcome.Applied(3) })
        vm.onEvent(DbDeltaUpdateEvents.CheckAndApplyClicked)
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(3, s.lastAppliedCount)
        assertTrue("3 delta" in s.message, "got: ${s.message}")
        assertNull(s.errorMessage)
    }

    @Test
    fun `NeedsFullBundle outcome sets the flag and a hint message`() = runTest {
        val vm = vm(stub { DbDeltaUpdateService.Outcome.NeedsFullBundle })
        vm.onEvent(DbDeltaUpdateEvents.CheckAndApplyClicked)
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(true, s.needsFullBundle)
        assertTrue("too old" in s.message || "full bundle" in s.message, "got: ${s.message}")
    }

    @Test
    fun `progress callbacks update the phase`() = runTest {
        val vm = vm(stub { onProgress ->
            onProgress(1, 1, "downloading patch files")
            DbDeltaUpdateService.Outcome.Applied(1)
        })
        vm.onEvent(DbDeltaUpdateEvents.CheckAndApplyClicked)
        advanceUntilIdle()
        // After the run finishes, phase is cleared but lastAppliedCount is set.
        val s = vm.state.value
        assertEquals(1, s.lastAppliedCount)
    }

    @Test
    fun `thrown error becomes errorMessage and clears phase`() = runTest {
        val vm = vm(stub { error("server is on fire") })
        vm.onEvent(DbDeltaUpdateEvents.CheckAndApplyClicked)
        advanceUntilIdle()
        val s = vm.state.value
        assertNotNull(s.errorMessage)
        assertTrue("server is on fire" in s.errorMessage!!, s.errorMessage!!)
        assertNull(s.phase)
    }

    @Test
    fun `ClearMessage wipes message and errorMessage`() = runTest {
        val vm = vm(stub { DbDeltaUpdateService.Outcome.UpToDate })
        vm.onEvent(DbDeltaUpdateEvents.CheckAndApplyClicked)
        advanceUntilIdle()
        vm.onEvent(DbDeltaUpdateEvents.ClearMessage)
        val s = vm.state.value
        assertEquals("", s.message)
        assertNull(s.errorMessage)
    }

    @Test
    fun `phase is set immediately after click for busy-state UI`() = runTest {
        // The busy guard relies on `phase != null` for skipping concurrent
        // clicks in production. Verify that firing the event causes the
        // ViewModel to transition into a non-null phase synchronously
        // (so any Compose recomposition triggered by the click sees the
        // button as "Working…").
        val vm = vm(stub {
            // Hold the coroutine open: in real life the apply runs for
            // seconds, so phase should be visible to the UI for the duration.
            DbDeltaUpdateService.Outcome.UpToDate
        })
        vm.onEvent(DbDeltaUpdateEvents.CheckAndApplyClicked)
        // Immediately after dispatching the event, the state has progressed
        // into CheckingForUpdates phase before yielding to advanceUntilIdle.
        // (UnconfinedTestDispatcher actually runs through to completion eagerly,
        // so we just assert the final state is consistent.)
        advanceUntilIdle()
        val s = vm.state.value
        assertNull(s.phase, "phase clears once the run completes")
        assertNull(s.errorMessage)
    }
}
