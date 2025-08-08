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

Define your navigation routes in the `TabsNavHost`:

```kotlin
NavHost(
    navController = navController,
    startDestination = navigator.startDestination,
    modifier = Modifier
) {
    nonAnimatedComposable<TabsDestination.Home> { backStackEntry ->
        val destination = backStackEntry.toRoute<TabsDestination.Home>()
        // Pass the tabId to the savedStateHandle
        backStackEntry.savedStateHandle["tabId"] = destination.tabId
        
        HomeScreen()
    }
    
    nonAnimatedComposable<TabsDestination.MyCustomScreen> { backStackEntry ->
        val destination = backStackEntry.toRoute<TabsDestination.MyCustomScreen>()
        // Pass the tabId to the savedStateHandle
        backStackEntry.savedStateHandle["tabId"] = destination.tabId
        // Pass any other parameters
        backStackEntry.savedStateHandle["parameter"] = destination.parameter
        
        MyCustomScreen()
    }
}
```

### 5. Navigate Between Screens

Use the `Navigator` to navigate, which will automatically create new tabs:

```kotlin
// Inject the navigator
val navigator = koinInject<Navigator>()
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

This system allows you to have many open tabs without overloading memory, as only the active tab is actually loaded, while the state of others is preserved in a lightweight manner.