package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.StateKeys
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.AltTocUseCase
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.BookContentUseCaseFactory
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.CategoryDisplaySettingsUseCase
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.CommentariesUseCase
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.ContentUseCase
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.NavigationUseCase
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.TocUseCase
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimlibrary.core.models.AltTocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Tests that postSelectLine correctly syncs alt-TOC and reapplies commentaries
 * when different event types trigger line selection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PostSelectLineTest {
    private val testTabId = "test-tab"
    private val testBook = TestFactories.createBook(id = 1L)
    private val testLine = TestFactories.createLine(id = 10L, bookId = 1L)

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var persistedStore: TabPersistedStateStore
    private lateinit var repository: SeforimRepository
    private lateinit var useCaseFactory: BookContentUseCaseFactory
    private lateinit var titleUpdateManager: TabTitleUpdateManager
    private lateinit var tabsViewModel: TabsViewModel

    // Mocked use cases
    private lateinit var contentUseCase: ContentUseCase
    private lateinit var commentariesUseCase: CommentariesUseCase
    private lateinit var altTocUseCase: AltTocUseCase
    private lateinit var tocUseCase: TocUseCase
    private lateinit var navigationUseCase: NavigationUseCase
    private lateinit var categoryDisplaySettingsUseCase: CategoryDisplaySettingsUseCase

    // Captured from factory calls so we can manipulate VM-internal state
    private var capturedStateManager: BookContentStateManager? = null

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        persistedStore = TabPersistedStateStore()
        repository = mockk(relaxed = true)
        titleUpdateManager = TabTitleUpdateManager()
        tabsViewModel =
            TabsViewModel(
                titleUpdateManager = titleUpdateManager,
                startDestination = TabsDestination.Home(tabId = "start"),
            )

        // Create mocked use cases
        contentUseCase = mockk(relaxed = true)
        commentariesUseCase = mockk(relaxed = true)
        altTocUseCase = mockk(relaxed = true)
        tocUseCase = mockk(relaxed = true)
        navigationUseCase = mockk(relaxed = true)
        categoryDisplaySettingsUseCase = mockk(relaxed = true)

        // CategoryDisplaySettingsUseCase.categoryChanges must return a flow
        every { categoryDisplaySettingsUseCase.categoryChanges } returns MutableSharedFlow()

        // Factory returns mocked use cases; capture stateManager from the first call
        val stateManagerSlot = slot<BookContentStateManager>()
        useCaseFactory =
            mockk {
                every { createNavigationUseCase(capture(stateManagerSlot)) } answers {
                    capturedStateManager = stateManagerSlot.captured
                    navigationUseCase
                }
                every { createContentUseCase(any()) } returns contentUseCase
                every { createCommentariesUseCase(any(), any()) } returns commentariesUseCase
                every { createAltTocUseCase(any()) } returns altTocUseCase
                every { createTocUseCase(any()) } returns tocUseCase
                every { createCategoryDisplaySettingsUseCase() } returns categoryDisplaySettingsUseCase
            }

        // Stub init-block calls
        coEvery { navigationUseCase.loadRootCategories() } returns Unit

        // ContentUseCase.buildLinesPager needs to return a flow
        every { contentUseCase.buildLinesPager(any(), any()) } returns flowOf(PagingData.empty())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): BookContentViewModel {
        val savedStateHandle =
            SavedStateHandle(
                mapOf(StateKeys.TAB_ID to testTabId),
            )
        return BookContentViewModel(
            savedStateHandle = savedStateHandle,
            persistedStore = persistedStore,
            repository = repository,
            useCaseFactory = useCaseFactory,
            titleUpdateManager = titleUpdateManager,
            tabsViewModel = tabsViewModel,
        )
    }

    /**
     * Creates a ViewModel with a book pre-selected in state so that
     * loadAndSelectLine does not early-return.
     */
    private fun createViewModelWithBook(): BookContentViewModel {
        val vm = createViewModel()
        // Set the book on the VM's internal stateManager (captured via factory)
        capturedStateManager!!.updateNavigation { copy(selectedBook = testBook) }
        return vm
    }

    // --- LineSelected ---

    @Test
    fun `LineSelected syncs alt-TOC`() =
        runTest {
            val vm = createViewModelWithBook()
            coEvery { contentUseCase.selectLine(any(), any()) } returns Unit

            vm.onEvent(BookContentEvent.LineSelected(testLine))
            testScheduler.advanceUntilIdle()

            coVerify { altTocUseCase.selectAltEntryForLine(testLine.id) }
        }

    @Test
    fun `LineSelected reapplies commentaries for single line`() =
        runTest {
            val vm = createViewModelWithBook()
            coEvery { contentUseCase.selectLine(any(), any()) } returns Unit

            vm.onEvent(BookContentEvent.LineSelected(testLine))
            testScheduler.advanceUntilIdle()

            coVerify { commentariesUseCase.reapplySelectedCommentators(any()) }
            coVerify { commentariesUseCase.reapplySelectedLinkSources(any()) }
            coVerify { commentariesUseCase.reapplySelectedSources(any()) }
        }

    @Test
    fun `LineSelected reapplies commentaries for multi-selection`() =
        runTest {
            val line1 = TestFactories.createLine(id = 10L, bookId = 1L)
            val line2 = TestFactories.createLine(id = 11L, bookId = 1L)

            val vm = createViewModelWithBook()

            // Pre-populate state with multi-selection on the VM's internal stateManager
            capturedStateManager!!.updateContent {
                copy(
                    selectedLines = setOf(line1, line2),
                    primarySelectedLineId = line1.id,
                )
            }

            coEvery { contentUseCase.selectLine(any(), any()) } returns Unit

            vm.onEvent(BookContentEvent.LineSelected(line1))
            testScheduler.advanceUntilIdle()

            coVerify {
                commentariesUseCase.reapplySelectedCommentatorsForLines(any(), any(), any())
            }
            coVerify {
                commentariesUseCase.reapplySelectedLinkSourcesForLines(any(), any(), any())
            }
            coVerify {
                commentariesUseCase.reapplySelectedSourcesForLines(any(), any(), any())
            }
        }

    // --- LoadAndSelectLine ---

    @Test
    fun `LoadAndSelectLine syncs alt-TOC`() =
        runTest {
            val vm = createViewModelWithBook()
            coEvery { contentUseCase.loadAndSelectLine(testLine.id, scroll = true) } returns testLine

            vm.onEvent(BookContentEvent.LoadAndSelectLine(testLine.id))
            testScheduler.advanceUntilIdle()

            coVerify { altTocUseCase.selectAltEntryForLine(testLine.id) }
        }

    @Test
    fun `LoadAndSelectLine reapplies commentaries`() =
        runTest {
            val vm = createViewModelWithBook()
            coEvery { contentUseCase.loadAndSelectLine(testLine.id, scroll = true) } returns testLine

            vm.onEvent(BookContentEvent.LoadAndSelectLine(testLine.id))
            testScheduler.advanceUntilIdle()

            coVerify { commentariesUseCase.reapplySelectedCommentators(any()) }
            coVerify { commentariesUseCase.reapplySelectedLinkSources(any()) }
            coVerify { commentariesUseCase.reapplySelectedSources(any()) }
        }

    // --- AltTocEntrySelected ---

    @Test
    fun `AltTocEntrySelected does not re-sync alt-TOC`() =
        runTest {
            val entry = AltTocEntry(id = 5L, structureId = 1L, level = 0, lineId = testLine.id)
            val vm = createViewModelWithBook()
            coEvery { altTocUseCase.selectAltEntry(entry) } returns testLine.id
            coEvery { contentUseCase.loadAndSelectLine(testLine.id, scroll = true) } returns testLine

            vm.onEvent(BookContentEvent.AltTocEntrySelected(entry))
            testScheduler.advanceUntilIdle()

            // selectAltEntry is called (to resolve the entry)
            coVerify { altTocUseCase.selectAltEntry(entry) }
            // selectAltEntryForLine must NOT be called (syncAltToc = false)
            coVerify(exactly = 0) { altTocUseCase.selectAltEntryForLine(any()) }
        }

    @Test
    fun `AltTocEntrySelected still reapplies commentaries`() =
        runTest {
            val entry = AltTocEntry(id = 5L, structureId = 1L, level = 0, lineId = testLine.id)
            val vm = createViewModelWithBook()
            coEvery { altTocUseCase.selectAltEntry(entry) } returns testLine.id
            coEvery { contentUseCase.loadAndSelectLine(testLine.id, scroll = true) } returns testLine

            vm.onEvent(BookContentEvent.AltTocEntrySelected(entry))
            testScheduler.advanceUntilIdle()

            coVerify { commentariesUseCase.reapplySelectedCommentators(any()) }
            coVerify { commentariesUseCase.reapplySelectedLinkSources(any()) }
            coVerify { commentariesUseCase.reapplySelectedSources(any()) }
        }
}
