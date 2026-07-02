# Tabs with State Restore

This app uses a custom tab system rendered by `TabsContent` **without Compose
Navigation**. Each open tab owns a `SimpleTabViewModelOwner` (its own
`ViewModelStoreOwner` + `SavedStateRegistryOwner`) that stays alive while the tab
exists. To bound memory, only a small LRU of tab compositions is kept actively
composed; the rest are torn down and rebuilt from saved state when re‑selected.
The full user session is still restored on cold boot.

## Windows and Virtual Desktops

The app is multi-window (Chrome-style). The model is: **a virtual desktop is a
user-curated set of tabs laid out in 1..n OS windows**. A desktop is either OPEN
(all its windows live) or DORMANT (a serialized snapshot); several desktops can be
open at once — each in its own window(s) — but a desktop is never open twice.

- `DesktopManager` (app singleton) owns desktops and windows. Each `OpenWindow`
  carries its own window-scoped `TabsViewModel` + `SearchHomeViewModel` and its
  Compose `WindowState` (geometry, persisted per window and restored at boot,
  clamped to the current screens).
- Inside a window's composition, `LocalOpenWindow.current` gives the window; use
  `LocalOpenWindow.current.tabsViewModel` instead of a global TabsViewModel (there
  is none anymore).
- Per-tab ViewModels that need to navigate inject `DesktopManager` and resolve
  their window at call time: `desktopManager.tabsViewModelFor(tabId)?.openTab(...)`.
  This stays correct when the tab is dragged to another window.
- `TabDockManager` implements cross-window tab drag & drop (IntelliJ DockManager
  style): in-strip drags reorder as before; dragging past the strip shows a ghost
  and the release either drops the tab on another window's strip or detaches it
  into a new window of the same desktop. Desktops are never created implicitly.

## System Architecture

The tab system consists of several key components working together:

1. **TabsViewModel** – Manages one window's tab list (create/select/close/replace),
   titles, and cross-window move primitives (`takeTab`/`insertTab`). One instance per window
2. **TabsContent** – Renders tab content (no `NavHost`). Keeps a per‑tab
   `SimpleTabViewModelOwner` in a `tabOwners` map and retains an LRU of up to
   `MAX_RETAINED_TAB_COMPOSITIONS` (3) tab compositions. The selected tab is visible
   (`zIndex` 1, `alpha` 1); retained/preloaded tabs are stacked underneath at `alpha` 0
3. **TabPersistedStateStore** – App-wide per‑tab state keyed by tabId (UUIDs, so
   entries from different windows/desktops never collide); the source of truth
   serialized by SessionManager
4. **SessionManager** – Handles disk persistence (desktops, windows, geometry, tab
   state) across app restarts; debounced autosave (~2s) plus save on window close/quit
5. **TabsDestination** – Strongly‑typed routes for Home, Search, and BookContent
6. **TabsView** – Displays the tabs strip and emits user events (select/close/add),
   registers the strip as a drag & drop target with TabDockManager
7. **DesktopManager / OpenWindow / TabDockManager** – Multi-window layer (see above)

## Behavior: Bounded, Predictable Tabs

- ViewModels are keyed by `tabId` via the tab's `SimpleTabViewModelOwner`, so UI state
  is preserved **as long as the tab stays within the retained‑composition LRU**. When a
  tab falls out of the LRU its composition is torn down; on re‑selection it is rebuilt
  from `TabStateManager` (and, for Search, `SearchTabCache`).
- On cold boot, the app restores open tabs, the selected tab, and per‑tab saved
  state via `SessionManager` + `TabStateManager`.
- **Memory is bounded by design**: at most the selected tab plus
  `MAX_RETAINED_TAB_COMPOSITIONS` (3) compositions are active at once, regardless of how
  many tabs are open. There is no separate "RAM saver" toggle.
- **Hover preload**: hovering a tab in the strip publishes `TabsViewModel.preloadTabId`;
  `TabsContent` composes that tab off‑screen (still within the LRU cap) so selecting it
  is instant. Selecting a tab clears the preload.

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

### 3. Render Tab Content

Use `TabsContent` in your main composable. It manages the per‑tab
`SimpleTabViewModelOwner` lifecycle, the retained‑composition LRU, and hover preload —
no `NavHost` involved:

```kotlin
@Composable
fun MyApplication() {
    Column {
        // Display the tabs strip
        TabsView()

        // Tab content - handles ViewModel lifecycle and the retained-composition LRU
        TabsContent()
    }
}
```

### 4. Dispatch by Destination

`TabsContent` resolves each retained tab to its `TabsDestination` and renders the
matching screen with a `tabId`‑scoped `ViewModel`. ViewModels are obtained against the
tab's `SimpleTabViewModelOwner` (e.g. via `assistedMetroViewModel(viewModelStoreOwner = tabOwner)`),
so they stay stable while the tab's composition is retained.

Home reuses the BookContent shell: when no book is selected in state, the shell renders
`HomeView`. When opening a tab directly on a book or line, the destination is
`TabsDestination.BookContent` and the screen shows a minimal loader until the book is
ready, avoiding a Home→Book flash. To add a new destination, extend the `when (destination)`
block in `TabsContent`:

```kotlin
when (val destination = tabItem.destination) {
    is TabsDestination.Home -> HomeTabContent(tabOwner, tabId, isSelected, /* ... */)
    is TabsDestination.Search -> SearchTabContent(tabOwner, destination, isSelected)
    is TabsDestination.BookContent -> BookContentTabContent(tabOwner, destination, isSelected, /* ... */)
    // Add your own:
    is TabsDestination.MyCustomScreen -> {
        tabOwner.setDefaultArgs(savedState { putString(StateKeys.TAB_ID, destination.tabId) })
        val viewModel: MyCustomViewModel = assistedMetroViewModel(viewModelStoreOwner = tabOwner)
        MyCustomScreen(viewModel)
    }
}
```

### 5. Open Tabs / Navigate

Use `TabsViewModel.openTab(...)` to open a new tab with a destination:

```kotlin
// Access the current window's tabs VM (composition scope)
val tabsVm = LocalOpenWindow.current.tabsViewModel
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
val tabsVm = LocalOpenWindow.current.tabsViewModel
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

5. **Handle tab lifecycle properly** – ViewModels are scoped to each tab's
   `SimpleTabViewModelOwner` and stay alive while the tab's composition is retained
   in the LRU. Once a tab falls out of the LRU it is rebuilt from saved state on
   re‑selection, so always persist anything you need via `TabStateManager` (and
   `SearchTabCache` for Search results) rather than relying on in‑memory ViewModel state.

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
while still delivering full UX restoration for Search. Combined with the
retained‑composition LRU and hover preload, switching between tabs is near‑instant.
