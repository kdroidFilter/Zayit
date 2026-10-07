package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.features.bookcontent.state.PaneSearchResult
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.PaneSearchState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaneSearchTest {
    private data class Result(
        override val matchIds: List<Long>,
    ) : PaneSearchResult {
        override val bestMatchId: Long? get() = matchIds.firstOrNull()
    }

    private var state: PaneSearchState<Result>? = PaneSearchState()
    private val runner = PaneSearchRunner<Result> { t -> state = state?.let(t) }

    @Test
    fun `a new search cancels the one still running`() =
        runTest {
            val slow = CompletableDeferred<Result>()
            val first = launch { runner.run("ab") { slow.await() } }
            testScheduler.runCurrent()
            runner.run("abc") { Result(listOf(2)) }
            assertTrue(first.isCancelled)
            assertEquals("abc", state?.resultQuery)
            assertEquals(listOf(2L), state?.result?.matchIds)
        }

    @Test
    fun `a superseded query never overwrites the newer one`() =
        runTest {
            // The older search outlives the newer one, e.g. running in another scope
            val slow = CompletableDeferred<Result>()
            val first = launch { PaneSearchRunner<Result> { t -> state = state?.let(t) }.run("ab") { slow.await() } }
            testScheduler.runCurrent()
            runner.run("abc") { Result(listOf(2)) }
            slow.complete(Result(listOf(1)))
            first.join()
            assertEquals("abc", state?.query)
            assertEquals("abc", state?.resultQuery)
            assertEquals(listOf(2L), state?.result?.matchIds)
        }

    @Test
    fun `the previous result stays shown while the next one computes`() =
        runTest {
            runner.run("ab") { Result(listOf(1)) }
            val slow = CompletableDeferred<Result>()
            val next = launch { runner.run("abc") { slow.await() } }
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
            val search = launch { runner.run("ab") { slow.await() } }
            testScheduler.runCurrent()
            state = null
            slow.complete(Result(listOf(1)))
            search.join()
            assertNull(state)
        }

    @Test
    fun `a blank query clears the result`() =
        runTest {
            runner.run("ab") { Result(listOf(1)) }
            runner.run(" ") { error("must not compute") }
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
