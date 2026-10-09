package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.SavedState
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.savedState
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.core.e2e.E2e
import io.github.kdroidfilter.seforimapp.features.author.AuthorTabContent
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentScreen
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentViewModel
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookTabUi
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.StateKeys
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.HomeSearchCallbacks
import io.github.kdroidfilter.seforimapp.features.favorites.FavoritesTabContent
import io.github.kdroidfilter.seforimapp.features.history.HistoryTabContent
import io.github.kdroidfilter.seforimapp.features.notes.NotesTabContent
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeNavigationEvent
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchResultInBookShellMvi
import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchShellActions
import io.github.kdroidfilter.seforimapp.features.siddur.installedSiddur
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * Whether the current tab is selected/visible. Used to skip animations on background tabs.
 */
val LocalTabSelected = compositionLocalOf { true }

/**
 * Stable, rename-safe discriminator for a destination, used to key the per-tab
 * saveable UI state so it resets when a tab navigates to a different destination type.
 */
private fun TabsDestination.typeKey(): String =
    when (this) {
        is TabsDestination.Home -> "home"
        is TabsDestination.Search -> "search"
        is TabsDestination.BookContent -> "book"
        is TabsDestination.History -> "history"
        is TabsDestination.Favorites -> "favorites"
        is TabsDestination.Notes -> "notes"
        is TabsDestination.Siddur -> "siddur"
        is TabsDestination.Author -> "author"
    }

private fun saveableKeyFor(destination: TabsDestination): String = "${destination.tabId}:${destination.typeKey()}"

private fun saveableKeysFor(tabId: String): List<String> = listOf("$tabId:home", "$tabId:search", "$tabId:book", "$tabId:siddur")

/**
 * The text of every tab of the current window, the centre of its dock layout.
 *
 * Every tab of the window stays composed; switching never tears one down. Only the selected tab is
 * measured and placed, so hidden tabs incur no layout/draw cost while their ViewModel, paging flow
 * and scroll state stay hot — switching back is instant, with no reload-and-jump. The cost is RAM
 * proportional to the number of open tabs. The ViewModels themselves belong to the desktop
 * ([DesktopSession.ownerOf]), so a tab dragged to another window keeps them.
 */
@Composable
fun TabsContent() {
    val openWindow = LocalOpenWindow.current
    val session = openWindow.session
    val tabsViewModel: TabsViewModel = openWindow.tabsViewModel
    val searchHomeViewModel = openWindow.searchHomeViewModel

    val group = openWindow.group()
    val tabIds = group?.ids.orEmpty()
    val currentTabId = group?.selectedId
    val sessionManager = LocalAppGraph.current.sessionManager
    val isRestoringSession by sessionManager.isRestoringSession.collectAsState()
    val isSwitchingDesktop by openWindow.isSwitching.collectAsState()
    val isTransitioning = isRestoringSession || isSwitchingDesktop

    val searchUi by remember(searchHomeViewModel) { searchHomeViewModel.uiState }.collectAsState()
    val scope = rememberCoroutineScope()
    val latestCurrentTabId by rememberUpdatedState(currentTabId)

    val homeSearchCallbacks = rememberHomeSearchCallbacks(searchHomeViewModel) { latestCurrentTabId }

    // Dismiss suggestions when navigating away from Home
    val currentDestination = currentTabId?.let(session::item)?.destination
    LaunchedEffect(currentDestination) {
        if (currentDestination !is TabsDestination.Home) {
            searchHomeViewModel.dismissSuggestions()
        }
    }

    // Collect navigation events from SearchHomeViewModel and perform navigation
    LaunchedEffect(searchHomeViewModel, tabsViewModel) {
        searchHomeViewModel.navigationEvents.collect { event -> navigate(event, tabsViewModel) }
    }

    // Holds per-tab saveable UI state across a destination change of the same tab.
    val saveableStateHolder = rememberSaveableStateHolder()
    val knownTabIds = remember { mutableSetOf<String>() }
    LaunchedEffect(tabIds) {
        val removed = knownTabIds - tabIds.toSet()
        removed.forEach { tabId -> saveableKeysFor(tabId).forEach(saveableStateHolder::removeState) }
        knownTabIds.clear()
        knownTabIds.addAll(tabIds)
    }

    // Clear the desktop-switching flag after the first frame so the loader disappears
    LaunchedEffect(isSwitchingDesktop) {
        if (isSwitchingDesktop) {
            // Wait one frame for ViewModels to initialize with persisted state
            kotlinx.coroutines.delay(100)
            openWindow.clearSwitching()
        }
    }

    val canvasBg = JewelTheme.globalColors.toolwindowBackground

    Box(
        modifier =
            Modifier
                .trackActivation()
                .fillMaxSize()
                .background(canvasBg),
    ) {
        tabIds.forEach { tabId ->
            val tabItem = session.item(tabId) ?: return@forEach
            val isSelected = tabId == currentTabId
            val saveableKey = saveableKeyFor(tabItem.destination)
            key(saveableKey) {
                saveableStateHolder.SaveableStateProvider(saveableKey) {
                    val tabOwner = session.ownerOf(tabId)
                    CompositionLocalProvider(LocalTabSelected provides isSelected) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .layout { measurable, constraints ->
                                        // Selected: measure + place normally. Hidden: skip both so
                                        // the subtree stays composed (alive) but incurs no layout
                                        // or draw cost.
                                        if (isSelected) {
                                            val placeable = measurable.measure(constraints)
                                            layout(placeable.width, placeable.height) {
                                                placeable.place(0, 0)
                                            }
                                        } else {
                                            layout(0, 0) {}
                                        }
                                    },
                        ) {
                            when (val destination = tabItem.destination) {
                                is TabsDestination.Home -> {
                                    HomeTabContent(
                                        tabOwner = tabOwner,
                                        destination = destination,
                                        isSelected = isSelected,
                                        isRestoringSession = isTransitioning,
                                        searchUi = searchUi,
                                        searchCallbacks = homeSearchCallbacks,
                                    )
                                }

                                is TabsDestination.Search -> {
                                    SearchTabContent(
                                        tabOwner = tabOwner,
                                        destination = destination,
                                        isSelected = isSelected,
                                    )
                                }

                                is TabsDestination.BookContent -> {
                                    BookContentTabContent(
                                        tabOwner = tabOwner,
                                        destination = destination,
                                        isSelected = isSelected,
                                        isRestoringSession = isTransitioning,
                                        searchUi = searchUi,
                                        searchCallbacks = homeSearchCallbacks,
                                    )
                                }

                                is TabsDestination.History -> {
                                    HistoryTabContent(tabId = tabId)
                                }

                                is TabsDestination.Favorites -> {
                                    FavoritesTabContent(tabId = tabId)
                                }

                                is TabsDestination.Notes -> {
                                    NotesTabContent(tabId = tabId)
                                }

                                is TabsDestination.Siddur -> {
                                    // ponytail: a siddur tab restored in a community build stays empty
                                    installedSiddur?.TabContent(tabId, destination)
                                }

                                is TabsDestination.Author -> {
                                    AuthorTabContent(tabId = tabId, authorId = destination.authorId)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTabContent(
    tabOwner: SimpleTabViewModelOwner,
    destination: TabsDestination.Home,
    isSelected: Boolean,
    isRestoringSession: Boolean,
    searchUi: io.github.kdroidfilter.seforimapp.features.search.SearchHomeUiState,
    searchCallbacks: HomeSearchCallbacks,
) {
    val viewModel = tabBookViewModel(tabOwner, destination)
    val uiState by viewModel.uiState.collectAsState()
    val diacritics by viewModel.diacritics.collectAsState()
    val bookCharCounts by viewModel.bookCharCounts.collectAsState()

    BookContentScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        diacritics = diacritics,
        isRestoringSession = isRestoringSession,
        searchUi = searchUi,
        searchCallbacks = searchCallbacks,
        tabUi = tabUi(tabOwner),
        isSelected = isSelected,
        bookCharCounts = bookCharCounts,
    )
}

@Composable
private fun SearchTabContent(
    tabOwner: SimpleTabViewModelOwner,
    destination: TabsDestination.Search,
    isSelected: Boolean,
) {
    val viewModel = tabSearchViewModel(tabOwner, destination)
    val actions = rememberSearchShellActions(viewModel)
    // The tab's own bar state, opened where its search looked: one truth per tab, not the window's
    val desktopManager = LocalAppGraph.current.desktopManager
    val tabsViewModel = LocalOpenWindow.current.tabsViewModel
    val barViewModel =
        viewModel(viewModelStoreOwner = tabOwner, key = "search-bar") {
            desktopManager.newSearchBarViewModel().apply { showScope(viewModel.searchScope) }
        }
    val homeSearchUi by barViewModel.uiState.collectAsState()
    LaunchedEffect(barViewModel, tabsViewModel) {
        barViewModel.navigationEvents.collect { event -> navigate(event, tabsViewModel) }
    }
    val homeSearchCallbacks = rememberHomeSearchCallbacks(barViewModel) { destination.tabId }
    // The home bar here: its text search runs in this tab, where the bar says (its picked book or
    // category, else everywhere), in the base or all books as chosen (only Ctrl+Enter widens it)
    val barCallbacks =
        remember(homeSearchCallbacks, viewModel, barViewModel) {
            homeSearchCallbacks.copy(
                onGlobalExtendedChange = { extended ->
                    if (extended) viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetGlobalExtended(true))
                },
                onSubmitTextSearch = { query -> viewModel.searchFromBar(query, barViewModel.barScope()) },
            )
        }
    val bookVm = tabBookViewModel(tabOwner, destination)

    // Keep tree computation disabled when tab is not selected
    LaunchedEffect(isSelected) {
        viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetUiVisible(isSelected))
    }
    DisposableEffect(destination.tabId) {
        onDispose { viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetUiVisible(false)) }
    }

    val bcUiState by bookVm.uiState.collectAsState()
    val diacritics by bookVm.diacritics.collectAsState()
    val searchUi by viewModel.uiState.collectAsState()
    val visibleResults by viewModel.visibleResultsFlow.collectAsState()
    val isFiltering by viewModel.isFilteringFlow.collectAsState()
    val breadcrumbs by viewModel.breadcrumbsFlow.collectAsState()
    val bookCounts by viewModel.bookFacetCountsFlow.collectAsState()
    val tabCategories by viewModel.tabCategoriesFlow.collectAsState()
    val preview by viewModel.previewFlow.collectAsState()
    val selectedCategoryIds by viewModel.selectedCategoryIdsFlow.collectAsState()
    val bookFilterIds by viewModel.selectedBookIdsFlow.collectAsState()
    val entity by viewModel.entityFlow.collectAsState()

    SearchResultInBookShellMvi(
        bookUiState = bcUiState,
        onEvent = bookVm::onEvent,
        diacritics = diacritics,
        searchUi = searchUi,
        visibleResults = visibleResults,
        isFiltering = isFiltering,
        breadcrumbs = breadcrumbs,
        bookCounts = bookCounts,
        categories = tabCategories,
        selectedCategoryIds = selectedCategoryIds,
        bookFilterIds = bookFilterIds,
        entity = entity,
        homeSearchUi = homeSearchUi,
        homeSearchCallbacks = barCallbacks,
        preview = preview,
        actions = actions,
        tabUi = tabUi(tabOwner),
    )
}

/** Performs a search bar's navigation (open a book, a reference, an author, the results) in the current tab. */
private fun navigate(
    event: SearchHomeNavigationEvent,
    tabsViewModel: TabsViewModel,
) {
    when (event) {
        is SearchHomeNavigationEvent.NavigateToSearch -> {
            tabsViewModel.replaceCurrentTabDestination(
                TabsDestination.Search(
                    searchQuery = event.query,
                    tabId = event.tabId,
                ),
            )
        }
        is SearchHomeNavigationEvent.NavigateToBookContent -> {
            tabsViewModel.replaceCurrentTabDestination(
                TabsDestination.BookContent(
                    bookId = event.bookId,
                    tabId = event.tabId,
                    lineId = event.lineId,
                ),
            )
        }
        is SearchHomeNavigationEvent.NavigateToDeepLink -> {
            tabsViewModel.replaceCurrentTabDestination(event.destination)
        }
    }
}

/** The callbacks of a search bar's state, acting in the tab [currentTabId] returns. */
@Composable
private fun rememberHomeSearchCallbacks(
    searchHomeViewModel: SearchHomeViewModel,
    currentTabId: () -> String?,
): HomeSearchCallbacks {
    val scope = rememberCoroutineScope()
    val latestCurrentTabId by rememberUpdatedState(currentTabId)

    fun launchSubmitSearch(
        @StructuredScope scope: CoroutineScope,
        query: String,
        tabId: String,
    ) {
        scope.launch { searchHomeViewModel.submitSearch(query, tabId) }
    }

    fun launchOpenReference(
        @StructuredScope scope: CoroutineScope,
        tabId: String,
    ) {
        scope.launch { searchHomeViewModel.openSelectedReferenceInCurrentTab(tabId) }
    }

    return remember(searchHomeViewModel, scope) {
        HomeSearchCallbacks(
            onReferenceQueryChanged = searchHomeViewModel::onReferenceQueryChanged,
            onTocQueryChanged = searchHomeViewModel::onTocQueryChanged,
            onGlobalExtendedChange = searchHomeViewModel::onGlobalExtendedChange,
            onSubmitTextSearch = { query ->
                val tabId = latestCurrentTabId() ?: return@HomeSearchCallbacks
                launchSubmitSearch(scope, query, tabId)
            },
            onOpenReference = {
                val tabId = latestCurrentTabId() ?: return@HomeSearchCallbacks
                launchOpenReference(scope, tabId)
            },
            onPickBook = searchHomeViewModel::onPickBook,
            onPickToc = searchHomeViewModel::onPickToc,
            onPickAuthor = searchHomeViewModel::onPickAuthor,
            onOpenBook = { book ->
                val tabId = latestCurrentTabId() ?: return@HomeSearchCallbacks
                scope.launch { searchHomeViewModel.openBook(book, tabId) }
            },
            onOpenAuthor = { author ->
                val tabId = latestCurrentTabId() ?: return@HomeSearchCallbacks
                scope.launch { searchHomeViewModel.openAuthor(author, tabId) }
            },
            onClearAuthor = searchHomeViewModel::onClearAuthor,
            onOpenJump = { jump ->
                val tabId = latestCurrentTabId() ?: return@HomeSearchCallbacks
                scope.launch { searchHomeViewModel.openJump(jump, tabId) }
            },
        )
    }
}

/** What the search screen and its facet panes do with the user's input. */
@Composable
fun rememberSearchShellActions(viewModel: SearchResultViewModel): SearchShellActions =
    remember(viewModel) {
        SearchShellActions(
            onSubmit = { q ->
                viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetQuery(q))
                viewModel.onEvent(SearchResultViewModel.SearchResultEvents.ExecuteSearch)
            },
            onQueryChange = viewModel::setDraft,
            onGlobalExtendedChange = { extended ->
                viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetGlobalExtended(extended))
                viewModel.onEvent(SearchResultViewModel.SearchResultEvents.ExecuteSearch)
            },
            onScroll = { anchorId, anchorIndex, index, offset ->
                viewModel.onEvent(
                    SearchResultViewModel.SearchResultEvents.OnScroll(anchorId, anchorIndex, index, offset),
                )
            },
            onCancelSearch = {
                viewModel.onEvent(SearchResultViewModel.SearchResultEvents.CancelSearch)
            },
            onOpenResult = { r, newTab ->
                viewModel.onEvent(SearchResultViewModel.SearchResultEvents.OpenResult(r, newTab))
            },
            onRequestBreadcrumb = { r ->
                viewModel.onEvent(SearchResultViewModel.SearchResultEvents.RequestBreadcrumb(r))
            },
            onLoadMore = { viewModel.loadMore() },
            onCategoryCheckedChange = { id, checked ->
                viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetCategoryChecked(id, checked))
            },
            onBookCheckedChange = { id, checked ->
                viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetBookChecked(id, checked))
            },
            onEnsureScopeBookForToc = { id ->
                viewModel.onEvent(SearchResultViewModel.SearchResultEvents.EnsureScopeBookForToc(id))
            },
            onTocToggle = { entry, checked ->
                viewModel.onEvent(SearchResultViewModel.SearchResultEvents.SetTocChecked(entry.id, checked))
            },
            onTocFilter = { entry ->
                viewModel.onEvent(SearchResultViewModel.SearchResultEvents.FilterByTocId(entry.id))
            },
            onShowOnlyCategory = viewModel::showOnlyCategory,
            onOpenBookAt = viewModel::openBookAt,
            onSelectResult = viewModel::selectResult,
            onMoreFromBook = viewModel::showMoreFromBook,
            onBackFromBook = viewModel::backFromBook,
            onOpenAuthor = viewModel::openAuthor,
        )
    }

@Composable
private fun BookContentTabContent(
    tabOwner: SimpleTabViewModelOwner,
    destination: TabsDestination.BookContent,
    isSelected: Boolean,
    isRestoringSession: Boolean,
    searchUi: io.github.kdroidfilter.seforimapp.features.search.SearchHomeUiState,
    searchCallbacks: HomeSearchCallbacks,
) {
    val viewModel = tabBookViewModel(tabOwner, destination)
    val uiState by viewModel.uiState.collectAsState()
    val diacritics by viewModel.diacritics.collectAsState()
    val bookCharCounts by viewModel.bookCharCounts.collectAsState()

    // React to destination changes when ViewModel is reused
    LaunchedEffect(destination.bookId, destination.lineId, destination.endLineId) {
        if (destination.bookId > 0) {
            val lineId = destination.lineId
            if (lineId != null && lineId > 0) {
                viewModel.onEvent(BookContentEvent.OpenBookAtLine(destination.bookId, lineId, destination.endLineId))
            } else {
                viewModel.onEvent(BookContentEvent.OpenBookById(destination.bookId))
            }
        }
    }

    BookContentScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        diacritics = diacritics,
        isRestoringSession = isRestoringSession,
        searchUi = searchUi,
        searchCallbacks = searchCallbacks,
        tabUi = tabUi(tabOwner),
        isSelected = isSelected,
        bookCharCounts = bookCharCounts,
    )
}

/**
 * The book ViewModel of a tab — shared by its text and the dock panes drawing it. Whoever asks
 * first creates it with [destination]'s arguments.
 */
@Composable
fun tabBookViewModel(
    tabOwner: SimpleTabViewModelOwner,
    destination: TabsDestination,
): BookContentViewModel {
    tabOwner.prepare(destination)
    val viewModel: BookContentViewModel = assistedMetroViewModel(viewModelStoreOwner = tabOwner)
    E2e.registerBookViewModel(destination.tabId, viewModel)
    return viewModel
}

/** The search ViewModel of a search tab, shared by its results and its facet panes. */
@Composable
fun tabSearchViewModel(
    tabOwner: SimpleTabViewModelOwner,
    destination: TabsDestination.Search,
): SearchResultViewModel {
    tabOwner.prepare(destination)
    return assistedMetroViewModel(viewModelStoreOwner = tabOwner)
}

/** UI state the tab's text and its panes share (connections cache, note draft). */
@Composable
fun tabUi(tabOwner: SimpleTabViewModelOwner): BookTabUi = viewModel(viewModelStoreOwner = tabOwner) { BookTabUi() }

/**
 * Simplified ViewModel owner that manages lifecycle and state for a tab.
 * No Navigation dependency - just pure ViewModel lifecycle management.
 */
class SimpleTabViewModelOwner(
    private val tabId: String,
) : ViewModelStoreOwner,
    SavedStateRegistryOwner,
    HasDefaultViewModelProviderFactory {
    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val creationExtras = MutableCreationExtras()

    init {
        lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        enableSavedStateHandles()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        creationExtras[SAVED_STATE_REGISTRY_OWNER_KEY] = this
        creationExtras[VIEW_MODEL_STORE_OWNER_KEY] = this
        creationExtras[DEFAULT_ARGS_KEY] = savedState { putString(StateKeys.TAB_ID, tabId) }
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
        ViewModelProvider.NewInstanceFactory()

    override val defaultViewModelCreationExtras: CreationExtras get() = creationExtras

    fun setDefaultArgs(defaultArgs: SavedState) {
        creationExtras[DEFAULT_ARGS_KEY] = defaultArgs
    }

    /** Arguments the tab's ViewModels are created with, from its current destination. */
    fun prepare(destination: TabsDestination) {
        setDefaultArgs(
            savedState {
                putString(StateKeys.TAB_ID, destination.tabId)
                when (destination) {
                    is TabsDestination.Search -> {
                        putString("searchQuery", destination.searchQuery)
                        putString(StateKeys.SEARCH_SCOPE, Json.encodeToString<SearchScope>(destination.scope))
                        if (destination.globalExtended) putBoolean(StateKeys.SEARCH_GLOBAL_EXTENDED, true)
                    }
                    is TabsDestination.BookContent -> {
                        if (destination.bookId > 0) putLong(StateKeys.BOOK_ID, destination.bookId)
                        destination.lineId?.let { putLong(StateKeys.LINE_ID, it) }
                        destination.endLineId?.let { putLong(StateKeys.MARK_END_LINE_ID, it) }
                        if (destination.openNotes) putBoolean(StateKeys.OPEN_NOTES, true)
                        if (destination.shnayimMikra) putBoolean(StateKeys.SHNAYIM_MIKRA, true)
                    }
                    else -> Unit
                }
            },
        )
    }

    fun clear() {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
