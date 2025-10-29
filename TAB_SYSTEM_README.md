# RAM-Efficient Tab System Based on Compose Navigation

This document explains how to use the RAM-efficient tab system implemented in this project. The system allows managing multiple tabs in a Compose application while conserving memory resources.

## System Architecture

The tab system consists of several key components working together:

1. **TabsViewModel** - Manages tab state (creation, selection, closing)
2. **TabsNavHost** - Handles navigation between different destinations within tabs
3. **TabStateManager** - Preserves tab state even when tabs are not visible
4. **TabAwareViewModel** - Base class for ViewModels that need to maintain their state between tab changes
5. **TabsDestination** - Defines possible destinations for navigation
6. **TabsView** - Displays tabs in the user interface

## How It Saves RAM

The RAM savings come from the fact that:

1. Only the active tab is actually loaded in memory.
2. The state of other tabs is serialized and stored in the `TabStateManager`.
3. When switching tabs, the state of the previous tab is saved, and the state of the new tab is restored.

This avoids having multiple complete screens loaded simultaneously in memory, as would be the case with a naive approach to tabs.

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

Inherit from `TabAwareViewModel` for screens that need to maintain their state:

```kotlin
class MyScreenViewModel(
    savedStateHandle: SavedStateHandle,
    stateManager: TabStateManager,
    // other dependencies
) : TabAwareViewModel(
    tabId = savedStateHandle.get<String>("tabId") ?: "",
    stateManager = stateManager
) {
    // Use saveState() and getState() to persist state
    private val _myState = MutableStateFlow(getState<String>("myState") ?: "")
    val myState = _myState.asStateFlow()
    
    // Don't forget to save state when it changes
    fun updateState(newState: String) {
        _myState.value = newState
        saveState("myState", newState)
    }
}
```

### 3. Configure the NavHost

Use `TabsNavHost` in your main composable:

```kotlin
@Composable
fun MyApplication() {
    Column {
        // Display tabs
        TabsView()
        
        // Tab content
        TabsNavHost()
    }
}
```

### 4. Set Up Navigation Routes

Define your navigation routes in the `TabsNavHost`.

In this project, the Home route reuses the BookContent shell. When no book is
selected in the tab state, the shell renders the HomeView. This ensures a
consistent layout whether you’re on Home, Search results, or a specific book.

```kotlin
NavHost(
    navController = navController,
    startDestination = navigator.startDestination,
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

        MyCustomScreen()
    }
}
```

### 5. Navigate Between Screens

Use the `Navigator` to navigate, which will automatically create new tabs:

```kotlin
// Access the navigator from the app graph
val navigator = LocalAppGraph.current.navigator
val scope = rememberCoroutineScope()

// Navigation example
Button(onClick = {
    scope.launch {
        navigator.navigate(TabsDestination.MyCustomScreen("parameter", UUID.randomUUID().toString()))
    }
}) {
    Text("Open new tab")
}
```

Sometimes you want to REPLACE the current tab’s destination instead of opening
another tab (e.g., replace current content with Home or Search). Use
`TabsViewModel.replaceCurrentTabDestination(...)` for that:

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
navigator.navigate(TabsDestination.MyScreen(UUID.randomUUID().toString()))
```

4. **Limit the size of saved states** - Only save what's necessary to restore the user experience:

```kotlin
// Save only essential data
saveState("selectedItem", selectedItemId)  // Good: saves just an ID
// Instead of
saveState("allItems", completeListOfItems)  // Bad: saves entire list
```

5. **Handle tab lifecycle properly** - Remember that your ViewModel's `onCleared()` method will be called when switching tabs, but the state will be preserved by the `TabStateManager`.

6. **Localize Home titles in the UI** - The `TabsViewModel` may return an empty
   string for the Home tab title so the UI can localize the label via
   resources (e.g., using `title.ifEmpty { stringResource(Res.string.home) }`).
   This keeps titles correctly translated (in this app: דף הבית).

7. **Prefer partial state clearing over full wipes** - When switching an
   existing tab back to Home, clear only the keys that drive the content
   (e.g., the selected book) instead of wiping the whole tab state. The
   `TabStateManager` exposes `removeState(tabId, key)` for this.

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

## Example Implementation

The `BookContentViewModel` in this project is an excellent example of using the tab system:

```kotlin
class BookContentViewModel(
    savedStateHandle: SavedStateHandle,
    stateManager: TabStateManager,
    private val repository: SeforimRepository
) : TabAwareViewModel(
    tabId = savedStateHandle.get<String>("tabId") ?: "",
    stateManager = stateManager
) {
    // Initialize with saved state or default value
    private val _searchText = MutableStateFlow(getState<String>("searchText") ?: "")
    private val _showBookTree = MutableStateFlow(getState<Boolean>("showBookTree") ?: true)
    
    // Save state when it changes
    fun updateSearchText(text: String) {
        _searchText.value = text
        saveState("searchText", text)
    }
    
    fun toggleBookTree() {
        val newValue = !_showBookTree.value
        _showBookTree.value = newValue
        saveState("showBookTree", newValue)
    }
}
   ```

## Search Tab: Full In‑Memory Restoration

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

This approach keeps the TabStateManager payloads small (no large lists serialized) while
still delivering full UX restoration for Search.

This system allows you to have many open tabs without overloading memory, as only the active tab is actually loaded, while the state of others is preserved in a lightweight manner.
