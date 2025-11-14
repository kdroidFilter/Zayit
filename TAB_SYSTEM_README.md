# Classic Tabs with State Restore

This app now uses a classic tab system: each open tab owns its own `NavHostController`
and navigation graph and stays alive while the tab exists. This greatly simplifies
behavior and maintenance compared to the previous RAM‑optimized approach, while still
restoring the full user session on cold boot.

## System Architecture

The tab system consists of several key components working together:

1. **TabsViewModel** – Manages tab list (create/select/close/replace) and titles
2. **TabsNavHost** – Renders one `NavHost` per tab (kept alive); only the selected
   tab is visible, others are stacked underneath
3. **TabStateManager** – Persists lightweight, per‑tab state in memory and
   coordinates with SessionManager for cold‑boot restoration
4. **SessionManager** – Handles disk persistence of tabs and state across app restarts
5. **TabsDestination** – Strongly‑typed routes for Home, Search, and BookContent
6. **TabsView** – Displays the tabs strip and emit user events (select/close/add)

## Behavior: Simpler, Predictable Tabs

- Each tab has its own `NavHostController`, so ViewModels and UI state remain alive
  when switching between tabs. **State is automatically preserved** because ViewModels
  are kept in memory using `tabId` as the stable remember key.
- On cold boot, the app restores open tabs, the selected tab, and per‑tab saved
  state via `SessionManager` + `TabStateManager`.
- **Key improvement**: ViewModels now use `tabId` as the remember key instead of
  `destination`, preventing unnecessary recreation when navigating within the same tab.
- This approach is intentionally simpler and more maintainable than the previous
  RAM‑optimized system. It may keep more UI in memory if many tabs are open.

Tip: you can enable a RAM saver mode in settings (`AppSettings.setRamSaverEnabled(true)`),
which switches to a single‑NavHost strategy like the old system: only the selected
tab is kept active, and switching tabs navigates the single controller. This saves
memory at the cost of re‑creating UI when switching.

## How to Use It

### 1. Define Your Destinations

Extend `TabsDestination` to add your own destinations:

```kotlin
sealed interface TabsDestination {
    val tabId: String

    @Serializable
    data class Home(override val tabId: String) : TabsDestination
    
    @Serializable
    data class MyCustomScreen(
        val parameter: String,
        override val tabId: String
    ) : TabsDestination
}
```

### 2. Create a ViewModel for Each Screen

Simply inherit from `ViewModel` and use `TabStateManager` directly:

```kotlin
class MyScreenViewModel(
    savedStateHandle: SavedStateHandle,
    private val stateManager: TabStateManager,
    // other dependencies
) : ViewModel() {
    private val tabId: String = savedStateHandle.get<String>("tabId") ?: ""

    // Load initial state from TabStateManager
    private val _myState = MutableStateFlow(
        stateManager.getState<String>(tabId, "myState") ?: ""
    )
    val myState = _myState.asStateFlow()

    // Don't forget to save state when it changes
    fun updateState(newState: String) {
        _myState.value = newState
        stateManager.saveState(tabId, "myState", newState)
    }
}
```

### 3. Configure the NavHost

Use `TabsNavHost` in your main composable. The NavHost automatically uses `tabId`
as the remember key to ensure ViewModels remain stable across destination changes:

```kotlin
@Composable
fun MyApplication() {
    Column {
        // Display tabs
        TabsView()

        // Tab content - handles ViewModel stability automatically
        TabsNavHost()
    }
}
```

### 4. Navigation Graph per Tab

`TabsNavHost` creates a `NavHost` per tab and builds the same routes in each.
Home reuses the BookContent shell. When no book is selected in state, the shell
renders `HomeView`. When navigating directly to a book (e.g., opening a tab on a
specific book or line), the navigation targets `TabsDestination.BookContent` and the
screen shows a minimal loader until the book is ready, avoiding a Home→Book flash.

```kotlin
NavHost(
    navController = navController,
    startDestination = tabItem.destination,
    modifier = Modifier
) {
    // Home – BookContent shell without a selected book shows HomeView
    nonAnimatedComposable<TabsDestination.Home> { backStackEntry ->
        val destination = backStackEntry.toRoute<TabsDestination.Home>()
        backStackEntry.savedStateHandle["tabId"] = destination.tabId

        val viewModel = remember(appGraph, destination) {
            appGraph.bookContentViewModel(backStackEntry.savedStateHandle)
        }
        BookContentScreen(viewModel)
    }

    nonAnimatedComposable<TabsDestination.MyCustomScreen> { backStackEntry ->
        val destination = backStackEntry.toRoute<TabsDestination.MyCustomScreen>()
        // Pass the tabId and any other parameters
        backStackEntry.savedStateHandle["tabId"] = destination.tabId
        backStackEntry.savedStateHandle["parameter"] = destination.parameter

        // IMPORTANT: Use tabId as remember key to keep ViewModel stable
        val viewModel = remember(appGraph, destination.tabId) {
            appGraph.myCustomScreenViewModel(backStackEntry.savedStateHandle)
        }
        MyCustomScreen(viewModel)
    }
}
```

### 5. Open Tabs / Navigate

Use `TabsViewModel.openTab(...)` to open a new tab with a destination:

```kotlin
// Access the tabs VM from the app graph
val tabsVm = LocalAppGraph.current.tabsViewModel
val scope = rememberCoroutineScope()

// Open a new tab
Button(onClick = {
    scope.launch {
        tabsVm.openTab(TabsDestination.MyCustomScreen("parameter", UUID.randomUUID().toString()))
    }
}) { Text("Open new tab") }
```

Sometimes you want to REPLACE the current tab’s destination instead of opening
another tab (e.g., replace current content with Home or Search). Use
`TabsViewModel.replaceCurrentTabDestination(...)` for that. The tab’s NavHost will
navigate to the new destination automatically:

```kotlin
// Replace the current tab content with Home (preserve the same tabId)
val tabsVm = LocalAppGraph.current.tabsViewModel
val currentTabs = tabsVm.tabs.value
val currentIndex = tabsVm.selectedTabIndex.value
val currentTabId = currentTabs.getOrNull(currentIndex)?.destination?.tabId ?: return

tabsVm.replaceCurrentTabDestination(TabsDestination.Home(currentTabId))
```

## Best Practices

1. **Save state regularly** - Use `saveState()` whenever important state changes:

```kotlin
// In your ViewModel
fun onImportantStateChange(newValue: String) {
    _myState.value = newValue
    saveState("myState", newValue)
}
```

2. **Restore state at startup** - Use `getState()` to initialize your states from saved values:

```kotlin
// In your ViewModel initialization
private val _myState = MutableStateFlow(getState<String>("myState") ?: "default value")
```

3. **Use unique IDs** - Make sure each tab has a unique ID (UUID) to avoid conflicts:

```kotlin
LocalAppGraph.current.tabsViewModel.openTab(TabsDestination.MyScreen(UUID.randomUUID().toString()))
```

4. **Limit the size of saved states** - Only save what's necessary to restore the user experience:

```kotlin
// Save only essential data
saveState("selectedItem", selectedItemId)  // Good: saves just an ID
// Instead of
saveState("allItems", completeListOfItems)  // Bad: saves entire list
```

5. **Handle tab lifecycle properly** – With classic tabs, ViewModels remain
   alive when switching tabs because `remember` uses `tabId` as the key.
   State is automatically preserved in memory. Use `TabStateManager` for
   state you want to restore after a cold boot.

6. **Localize Home titles in the UI** - The `TabsViewModel` may return an empty
   string for the Home tab title so the UI can localize the label via
   resources (e.g., using `title.ifEmpty { stringResource(Res.string.home) }`).
   This keeps titles correctly translated (in this app: דף הבית).

7. **Prefer partial state clearing over full wipes** – When switching an existing
   tab back to Home, clear only the keys that drive the content (e.g., the
   selected book) instead of wiping the whole tab state. The `TabStateManager`
   exposes `removeState(tabId, key)` for this.

   Example when handling a Home button click:

   ```kotlin
   val appGraph = LocalAppGraph.current
   val tabsViewModel = appGraph.tabsViewModel
   val tabStateManager = appGraph.tabStateManager

   val tabs = tabsViewModel.tabs.value
   val selectedIndex = tabsViewModel.selectedTabIndex.value
   val currentTabId = tabs.getOrNull(selectedIndex)?.destination?.tabId

   if (currentTabId != null) {
       // Clear book-specific state so the BookContent shell shows Home
       tabStateManager.removeState(currentTabId, StateKeys.SELECTED_BOOK)
       tabStateManager.removeState(currentTabId, StateKeys.SELECTED_LINE)
       tabStateManager.removeState(currentTabId, StateKeys.CONTENT_ANCHOR_ID)
       tabStateManager.removeState(currentTabId, StateKeys.CONTENT_ANCHOR_INDEX)

       // Replace destination in-place, no new tab created
       tabsViewModel.replaceCurrentTabDestination(TabsDestination.Home(currentTabId))
   }
   ```

8. **Avoid Home→Book flicker for new tabs** – When opening a book in a new tab,
   pre-initialize the tab’s state with the selected book so the UI can show a
   loader immediately instead of rendering the Home screen first:

   ```kotlin
   val newTabId = UUID.randomUUID().toString()
   val repository: SeforimRepository = // from DI
   val tabStateManager: TabStateManager = // from DI
   val tabsVm: TabsViewModel = // from DI

   // Pre-seed state
   repository.getBook(bookId)?.let { book ->
       tabStateManager.saveState(newTabId, StateKeys.SELECTED_BOOK, book)
   }

   // Navigate directly to BookContent
   tabsVm.openTab(TabsDestination.BookContent(bookId = bookId, tabId = newTabId))
   ```

## Example Implementation

The `BookContentViewModel` in this project is an excellent example of using the tab system:

```kotlin
class BookContentViewModel(
    savedStateHandle: SavedStateHandle,
    private val tabStateManager: TabStateManager,
    private val repository: SeforimRepository
) : ViewModel() {
    private val currentTabId: String = savedStateHandle.get<String>("tabId") ?: ""

    // BookContentStateManager wraps TabStateManager for this specific screen
    private val stateManager = BookContentStateManager(currentTabId, tabStateManager)

    // State is loaded from TabStateManager automatically
    val uiState: StateFlow<BookContentState> = stateManager.state
        .map { /* transform state as needed */ }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialState)

    // Save state when it changes
    fun updateSearchText(text: String) {
        stateManager.updateNavigation {
            copy(searchText = text)
        }
    }
}
   ```

## Search Tab: In‑Memory Restoration

To restore the Search results tab instantly (scroll, filters, category path, TOC counts)
without re‑running the database query when the tab is re‑activated, the app uses a
lightweight, per‑tab in‑memory cache:

- Implementation: `io.github.kdroidfilter.seforimapp.features.search.SearchTabCache`
- Scope: keyed by `tabId`, lives for the duration of the app process
- Contents: the current `List<SearchResult>` only (aggregates are rebuilt quickly from it)
- Persistence: not serialized; if the process restarts, a fresh search is executed

Lifecycle integration:
- When the `SearchResultViewModel` is cleared (tab deactivated), it saves a snapshot to
  `SearchTabCache` so reopening the tab restores all results and scroll immediately.
- When a new search is submitted on the same tab, the cache entry is cleared to avoid
  stale results.
- When a tab is closed, the cache entry for that `tabId` is cleared as part of tab cleanup.

This approach keeps `TabStateManager` payloads small (no large lists serialized)
while still delivering full UX restoration for Search. Since each tab has its
own `NavHost`, switching between tabs is instantaneous.
