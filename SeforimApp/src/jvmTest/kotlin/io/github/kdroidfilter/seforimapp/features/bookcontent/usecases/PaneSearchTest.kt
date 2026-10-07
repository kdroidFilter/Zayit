package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.features.bookcontent.state.PaneSearchResult
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.PaneSearchState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaneSearchTest {
    private data class Result(
        override val matchIds: List<Long>,
    ) : PaneSearchResult {
        override val bestMatchId: Long? get() = matchIds.firstOrNull()
    }

    private var state: PaneSearchState<Result>? = PaneSearchState()
    private val update: ((PaneSearchState<Result>) -> PaneSearchState<Result>) -> Unit = { t -> state = state?.let(t) }

    @Test
    fun `a superseded query never overwrites the newer one`() =
        runTest {
            val slow = CompletableDeferred<Result>()
            val first = launch { runPaneSearch("ab", update) { slow.await() } }
            testScheduler.runCurrent()
            runPaneSearch("abc", update) { Result(listOf(2)) }
            slow.complete(Result(listOf(1)))
            first.join()
            assertEquals("abc", state?.query)
            assertEquals("abc", state?.resultQuery)
            assertEquals(listOf(2L), state?.result?.matchIds)
        }

    @Test
    fun `the previous result stays shown while the next one computes`() =
        runTest {
            runPaneSearch("ab", update) { Result(listOf(1)) }
            val slow = CompletableDeferred<Result>()
            val next = launch { runPaneSearch("abc", update) { slow.await() } }
            testScheduler.runCurrent()
            assertEquals("abc", state?.query)
            assertEquals("ab", state?.resultQuery)
            assertEquals(listOf(1L), state?.result?.matchIds)
            slow.complete(Result(listOf(2)))
            next.join()
        }

    @Test
    fun `closing the bar while searching keeps it closed`() =
        runTest {
            val slow = CompletableDeferred<Result>()
            val search = launch { runPaneSearch("ab", update) { slow.await() } }
            testScheduler.runCurrent()
            state = null
            slow.complete(Result(listOf(1)))
            search.join()
            assertNull(state)
        }

    @Test
    fun `a blank query clears the result`() =
        runTest {
            runPaneSearch("ab", update) { Result(listOf(1)) }
            runPaneSearch(" ", update) { error("must not compute") }
            assertNull(state?.result)
            assertEquals(" ", state?.resultQuery)
        }

    @Test
    fun `toggling opens a hidden bar and hides a shown one`() {
        val hidden: PaneSearchState<Result>? = null
        assertEquals(PaneSearchState(), hidden.toggled())
        assertNull(PaneSearchState<Result>("x").toggled())
    }
}
