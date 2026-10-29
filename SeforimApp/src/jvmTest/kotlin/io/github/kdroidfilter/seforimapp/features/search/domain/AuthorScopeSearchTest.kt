package io.github.kdroidfilter.seforimapp.features.search.domain

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforimapp.core.history.HistoryStore
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.StateKeys
import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.session.SearchPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimapp.testAppSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A search scoped to an author (רשב"א) over the dev database; skipped when it is absent. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthorScopeSearchTest : SearchIntegrationTestBase() {
    private val store = TabPersistedStateStore()

    @BeforeTest
    override fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        super.setup()
    }

    @AfterTest
    override fun tearDown() {
        super.tearDown()
        Dispatchers.resetMain()
    }

    private fun viewModel(tabId: String): SearchResultViewModel {
        val desktopManager = mockk<DesktopManager>(relaxed = true)
        every { desktopManager.tabExistsFlow(any()) } returns flowOf(true)
        return SearchResultViewModel(
            savedStateHandle = SavedStateHandle(mapOf(StateKeys.TAB_ID to tabId)),
            persistedStore = store,
            repository = requireRepository(),
            lucene = requireSearchEngine(),
            titleUpdateManager = TabTitleUpdateManager(),
            desktopManager = desktopManager,
            historyStore = HistoryStore(UserSettingsDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(UserSettingsDb.Schema::create))),
            appSettings = testAppSettings(),
            lookup = mockk(relaxed = true),
        )
    }

    @Test
    fun `an author's search finds in their books only, and keeps them once a book filter is cleared`() =
        runBlocking {
            skipIfNoIndex()
            val rashba =
                requireRepository()
                    .getAuthorDetails(RASHBA)!!
                    .books
                    .map { it.id }
                    .toSet()
            store.update(TAB) { it.copy(search = SearchPersistedState(query = "שבועה").withScope(SearchScope.Author(RASHBA))) }
            val vm = viewModel(TAB)
            val done = withTimeout(60_000) { vm.uiState.first { !it.isLoading && it.results.isNotEmpty() } }
            assertTrue(
                done.results.all { it.bookId in rashba },
                done.results
                    .map { it.bookTitle }
                    .distinct()
                    .toString(),
            )

            // A book picked in the results tree, then all the results again: still the author's books
            vm.filterByBookId(rashba.first())
            vm.showOnlyCategory(null)
            assertEquals(SearchScope.Author(RASHBA), vm.searchScope)

            // Those relaunched searches run off this thread: end them before tearDown closes the index under them
            vm.viewModelScope.coroutineContext.job
                .cancelAndJoin()
        }

    private companion object {
        const val RASHBA = 150L
        const val TAB = "author-scope-test"
    }
}
