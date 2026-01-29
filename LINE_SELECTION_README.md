# Line Selection System

This document describes the complete flow and side effects when a line is selected
in the BookContent feature. Line selection is a central user interaction that
cascades state changes across multiple UI panels.

## Overview

When a user clicks on a line in the content view, the following happens:

1. The line is marked as selected (visual highlight)
2. The TOC entry corresponding to this line is identified and highlighted
3. The breadcrumb path updates to show the hierarchical position
4. Commentaries panel loads available commentators for this line
5. Targum panel loads available links for this line
6. Sources panel loads available sources for this line
7. State is persisted for session restoration

**Multi-line selection** (Ctrl+click on Windows/Linux, Cmd+click on macOS):

1. Additional lines are toggled into/out of the selection set
2. All selected lines display visual highlights
3. Commentaries/Targum/Sources panels aggregate data from ALL selected lines
4. TOC highlight and breadcrumb follow the **primary line** (last clicked)

## Architecture

### Key Components

| Component | File | Responsibility |
|-----------|------|----------------|
| `BookContentViewModel` | `BookContentViewModel.kt` | Central event orchestrator |
| `ContentUseCase` | `ContentUseCase.kt` | Line selection business logic |
| `CommentariesUseCase` | `CommentariesUseCase.kt` | Commentaries reapplication |
| `TocUseCase` | `TocUseCase.kt` | TOC expansion management |
| `BookContentStateManager` | `BookContentStateManager.kt` | State mutation & persistence |
| `BookContentState` | `BookContentState.kt` | Immutable state model |

### State Structure

```kotlin
data class BookContentState(
    val navigation: NavigationState,
    val content: ContentState,      // Contains selectedLines, primarySelectedLineId
    val toc: TocState,              // Contains selectedEntryId, breadcrumbPath
    val altToc: AltTocState,
    val layout: LayoutState,
    val providers: Providers,
)

data class ContentState(
    // Multi-line selection
    val selectedLines: Set<Line> = emptySet(),
    val primarySelectedLineId: Long? = null,
    val isTocEntrySelection: Boolean = false,  // True when selection came from TOC entry click

    // Per-line selections (temporary, for current line)
    val selectedCommentatorsByLine: Map<Long, Set<Long>> = emptyMap(),
    val selectedLinkSourcesByLine: Map<Long, Set<Long>> = emptyMap(),
    val selectedSourcesByLine: Map<Long, Set<Long>> = emptyMap(),

    // Per-book selections (sticky, remembered across lines)
    val selectedCommentatorsByBook: Map<Long, Set<Long>> = emptyMap(),
    val selectedLinkSourcesByBook: Map<Long, Set<Long>> = emptyMap(),
    val selectedSourcesByBook: Map<Long, Set<Long>> = emptyMap(),

    // UI-ready selections (computed for current line)
    val selectedCommentatorIds: Set<Long> = emptySet(),
    val selectedTargumSourceIds: Set<Long> = emptySet(),
    val selectedSourceIds: Set<Long> = emptySet(),

    // Scroll state
    val anchorId: Long = -1L,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val scrollToLineTimestamp: Long = 0L,
) {
    // Computed properties for convenience
    val primaryLine: Line?
        get() = selectedLines.firstOrNull { it.id == primarySelectedLineId }
            ?: selectedLines.firstOrNull()

    val selectedLineIds: Set<Long>
        get() = selectedLines.mapTo(mutableSetOf()) { it.id }

    // Backward compatibility: single selected line = primary line
    val selectedLine: Line?
        get() = primaryLine
}

data class TocState(
    val entries: List<TocEntry> = emptyList(),
    val expandedEntries: Set<Long> = emptySet(),     // Expanded TOC nodes
    val selectedEntryId: Long? = null,               // Highlighted entry
    val breadcrumbPath: List<TocEntry> = emptyList(), // For breadcrumb display
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
)
```

## Event Flow

### Entry Points

Line selection can be triggered from multiple sources:

| Event | Source | Description |
|-------|--------|-------------|
| `LineSelected(line, isModifierPressed)` | Content view click | Direct line click with optional Ctrl/Cmd modifier |
| `LoadAndSelectLine(lineId)` | TOC entry click, search result | Load line by ID then select |
| `OpenBookAtLine(bookId, lineId)` | Deep link, cross-reference | Open book and jump to line |
| `NavigateToPreviousLine` | Keyboard (Up arrow) | Select previous line |
| `NavigateToNextLine` | Keyboard (Down arrow) | Select next line |

### Complete Flow Diagram

```
User clicks line in BookContentView
              ↓
    LineItem.onClick()                    [BookContentView.kt:860]
              ↓
    Capture keyboard modifiers via awaitPointerEvent()
              ↓
    onLineSelect(line, isModifierPressed) [BookContentView.kt:557]
              ↓
    onEvent(LineSelected(line, isModifier)) [BookContentPanel.kt:151-152]
              ↓
    BookContentViewModel.onEvent()        [BookContentViewModel.kt:301-363]
              ↓
    selectLine(line, isModifier)          [BookContentViewModel.kt:730-736]
              ↓
    ┌─────────────────────────────────────────────────────────────┐
    │           Branch on isModifierPressed                       │
    ├─────────────────────────────────────────────────────────────┤
    │                                                              │
    │  if (!isModifier) {                                          │
    │    // Single-click: replace selection                        │
    │    selectedLines = setOf(line)                               │
    │    primarySelectedLineId = line.id                           │
    │    isTocEntrySelection = false                               │
    │  } else {                                                    │
    │    // Ctrl/Cmd+click: toggle in selection set                │
    │    if (line in selectedLines) {                              │
    │      selectedLines -= line                                   │
    │      primarySelectedLineId = selectedLines.firstOrNull()?.id │
    │    } else {                                                  │
    │      selectedLines += line                                   │
    │      primarySelectedLineId = line.id                         │
    │    }                                                         │
    │    isTocEntrySelection = false                               │
    │  }                                                           │
    │                                                              │
    └─────────────────────────────────────────────────────────────┘
              ↓
    ┌─────────────────────────────────────────────────────────────┐
    │                    Parallel Operations                       │
    ├─────────────────────────────────────────────────────────────┤
    │  ContentUseCase.selectLine(line, isMultiSelect)              │
    │    ├─→ Update content.selectedLines                          │
    │    ├─→ Update content.primarySelectedLineId                  │
    │    ├─→ Query getTocEntryIdForLine(primaryLine.id)            │
    │    ├─→ Build breadcrumbPath via buildTocPathToRoot()         │
    │    └─→ Update toc.selectedEntryId + toc.breadcrumbPath       │
    │                                                              │
    │  CommentariesUseCase.reapplySelectedCommentators(...)        │
    │    ├─→ If multi-line: reapplySelectedCommentatorsForLines()  │
    │    ├─→ Get sticky selections from book level                 │
    │    ├─→ Fetch available commentators for line(s)              │
    │    ├─→ Compute intersection (sticky ∩ available)             │
    │    └─→ Update selectedCommentatorsByLine                     │
    │                                                              │
    │  CommentariesUseCase.reapplySelectedLinkSources(...)         │
    │    └─→ Same pattern for targum/links                         │
    │                                                              │
    │  CommentariesUseCase.reapplySelectedSources(...)             │
    │    └─→ Same pattern for sources                              │
    └─────────────────────────────────────────────────────────────┘
              ↓
    StateManager persists to TabPersistedStateStore
              ↓
    uiState.stateFlow emits new state
              ↓
    ┌─────────────────────────────────────────────────────────────┐
    │                    UI Recomposition                          │
    ├─────────────────────────────────────────────────────────────┤
    │  BookContentView     → All selected lines show border        │
    │  LineCommentsView    → Loads commentators for line(s)        │
    │  LineTargumView      → Loads links for line(s)               │
    │  SourcesPane         → Loads sources for line(s)             │
    │  BookTocView         → Highlights TOC entry (primary line)   │
    │  BreadcrumbView      → Updates path (primary line)           │
    └─────────────────────────────────────────────────────────────┘
```

## Multi-Line Selection

### Overview

The multi-line selection feature allows users to select multiple lines simultaneously using Ctrl+click (Windows/Linux) or Cmd+click (macOS). This enables viewing aggregated commentaries, targum, and sources for all selected lines at once.

### Selection Modes

| Mode | Trigger | Behavior |
|------|---------|----------|
| **Single selection** | Normal click | Replaces selection with clicked line |
| **Multi-selection** | Ctrl/Cmd + click | Toggles line in/out of selection set |
| **TOC entry selection** | Click TOC entry | Selects all lines in section (up to 128) |

### Primary Line Concept

When multiple lines are selected, one line is designated as the **primary line**:

- **On normal click**: The clicked line becomes the primary line
- **On Ctrl/Cmd + click (add)**: The newly added line becomes the primary line
- **On Ctrl/Cmd + click (remove)**: If the removed line was primary, the first remaining line becomes primary

The primary line determines:
- Which TOC entry is highlighted
- The breadcrumb path displayed
- Keyboard navigation starting point (Up/Down arrows)

### The `isTocEntrySelection` Flag

This boolean flag distinguishes between:

| Scenario | `isTocEntrySelection` | Panel Behavior |
|----------|----------------------|----------------|
| User Ctrl+clicks multiple lines | `false` | Panels show aggregated content for ALL selected lines |
| User clicks a TOC entry | `true` | Panels show content for PRIMARY LINE only (default behavior) |

This preserves the existing behavior where clicking a TOC entry shows only the default commentaries/targum for that section's heading line, while still allowing manual multi-selection to aggregate content.

### Implementation Details

**Event definition:**

```kotlin
// BookContentEvent.kt
data class LineSelected(
    val line: Line,
    val isModifierPressed: Boolean = false,
) : BookContentEvent
```

**Selection logic in ContentUseCase:**

```kotlin
suspend fun selectLine(line: Line, isMultiSelect: Boolean = false) {
    stateManager.updateContent {
        if (isMultiSelect) {
            // Toggle selection
            val newSelection = if (line in selectedLines)
                selectedLines - line
            else
                selectedLines + line
            val newPrimaryId = if (line in selectedLines)
                newSelection.firstOrNull()?.id
            else
                line.id
            copy(
                selectedLines = newSelection,
                primarySelectedLineId = newPrimaryId,
                isTocEntrySelection = false,  // Manual multi-selection
            )
        } else {
            // Replace with single selection
            copy(
                selectedLines = setOf(line),
                primarySelectedLineId = line.id,
                isTocEntrySelection = false,
            )
        }
    }
    // Update TOC based on primary line
    updateTocForPrimaryLine()
}
```

**Modifier detection in BookContentView:**

```kotlin
// Using awaitPointerEvent() to capture keyboard modifiers
Box(
    modifier = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Release) {
                    val mods = event.keyboardModifiers
                    val isModifier = mods.isCtrlPressed || mods.isMetaPressed
                    onClick(isModifier)
                }
            }
        }
    }
)
```

## Panel Influences

### 1. Content View (BookContentView)

**File:** `features/bookcontent/ui/panels/bookcontent/views/BookContentView.kt`

| Aspect | Behavior |
|--------|----------|
| Observation | `selectedLineIds = uiState.content.selectedLineIds` |
| Visual effect | **All** selected lines display colored left border |
| Scroll | Auto-scrolls to keep primary selected line visible |

```kotlin
// Line highlighting (LineItem composable)
val borderColor = if (isSelected)
    JewelTheme.globalColors.outlines.focused
else
    Color.Transparent

Box(
    modifier = Modifier
        .width(4.dp)
        .fillMaxHeight()
        .background(borderColor)
)
```

### 2. Commentaries Panel (LineCommentsView)

**File:** `features/bookcontent/ui/panels/bookcontent/views/LineCommentsView.kt`

| Aspect | Behavior |
|--------|----------|
| Observation | `contentState.selectedLines`, `contentState.isTocEntrySelection` |
| When empty | Displays "select_line_for_commentaries" message |
| Single line / TOC entry | Fetches `getCommentatorGroupsForLine(lineId)` |
| Manual multi-selection | Fetches `getCommentatorGroupsForLines(lineIds)` |
| Cache | Uses `lineConnections[selectedLine.id]?.commentatorGroups` |

**Selection logic:**

```kotlin
val selectedLineIds = contentState.selectedLineIds
val isManualMultiSelection = selectedLineIds.size > 1 && !contentState.isTocEntrySelection

when {
    selectedLine == null -> CenteredMessage(stringResource(Res.string.select_line_for_commentaries))
    isManualMultiSelection -> MultiLineCommentariesContent(
        selectedLineIds = selectedLineIds.toList(),
        // Uses buildCommentariesPagerForLines()
    )
    else -> CommentariesContent(
        selectedLine = selectedLine,
        // Uses buildCommentariesPager() for single line
    )
}
```

### 3. Targum Panel (LineTargumView)

**File:** `features/bookcontent/ui/panels/bookcontent/views/LineTargumView.kt`

| Aspect | Behavior |
|--------|----------|
| Observation | `selectedLineIds`, `isTocEntrySelection` |
| When empty | Displays "select_line_for_links" message |
| Single line / TOC entry | Fetches `getAvailableLinksForLine(lineId)` |
| Manual multi-selection | Fetches aggregated links for all lines |
| Cache | Uses `lineConnections[selectedLine.id]?.targumSources` |
| Pager | Single: `buildLinksPager()`, Multi: `buildLinksPagerForLines()` |
| ConnectionType | `ConnectionType.TARGUM` |

### 4. Sources Panel (SourcesPane)

**File:** `features/bookcontent/ui/panels/bookcontent/BookContentPanel.kt` (lines 261-300)

The Sources panel reuses `LineTargumView` with different parameters:

| Aspect | Behavior |
|--------|----------|
| Observation | `uiState.content.selectedLineIds`, `isTocEntrySelection` |
| When empty | Displays "no_sources_for_line" message |
| Single line / TOC entry | Fetches `getAvailableSourcesForLine(lineId)` |
| Manual multi-selection | Fetches aggregated sources for all lines |
| Cache | Uses `lineConnections[selectedLine.id]?.sources` |
| ConnectionType | `ConnectionType.SOURCE` |
| Event | `SelectedSourcesChanged(lineId, ids)` |

**Comparison: Sources vs Targum vs Commentaries:**

| Type | Single Pager | Multi Pager | Cache Field | ConnectionType |
|------|--------------|-------------|-------------|----------------|
| Sources | `buildSourcesPager` | `buildSourcesPagerForLines` | `snapshot.sources` | `SOURCE` |
| Targum | `buildLinksPager` | `buildLinksPagerForLines` | `snapshot.targumSources` | `TARGUM` |
| Commentaries | `buildCommentariesPager` | `buildCommentariesPagerForLines` | `snapshot.commentatorGroups` | N/A |

### 5. TOC Panel (BookTocView)

**File:** `features/bookcontent/ui/panels/booktoc/BookTocView.kt`

| Aspect | Behavior |
|--------|----------|
| Observation | `uiState.toc.selectedEntryId` (based on primary line) |
| Highlight | Entry matching `selectedEntryId` displays in bold |
| Auto-scroll | Scrolls to bring selected entry into view (once per book) |
| Breadcrumb | Separate from highlight, used for `BreadcrumbView` |

**How `selectedEntryId` is computed:**

```kotlin
// In ContentUseCase.selectLine()
// Always uses primary line for TOC highlight
val primaryLine = stateManager.state.value.content.primaryLine ?: return
val tocId = repository.getTocEntryIdForLine(primaryLine.id)
val tocPath = buildTocPathToRoot(tocId)

stateManager.updateToc {
    copy(
        selectedEntryId = tocId,
        breadcrumbPath = tocPath,
    )
}
```

**Auto-scroll behavior:**

```kotlin
LaunchedEffect(selectedTocEntryId, hasRestored, didAutoCenter) {
    if (!didAutoCenter && hasRestored) {
        val idx = visibleEntries.indexOfFirst { it.entry.id == selectedTocEntryId }
        if (idx >= 0) {
            listState.scrollToItem(idx)
            didAutoCenter = true
        }
    }
}
```

### 6. Breadcrumb (BreadcrumbView)

**File:** `features/bookcontent/ui/panels/bookcontent/views/BreadcrumbView.kt`

| Aspect | Behavior |
|--------|----------|
| Observation | `primaryLine?.id` + `toc.breadcrumbPath` |
| Display | `Category > Book > Chapter > Section` |
| Clickable | TOC items trigger `LoadAndSelectLine(lineId)` |

**Path construction:**

```kotlin
val breadcrumbPath = remember(book, primaryLine?.id, tocPath) {
    buildList {
        // 1. Category path (ancestors)
        addAll(categoryPath)
        // 2. Book item
        add(BookBreadcrumbItem(book))
        // 3. TOC path from line selection
        addAll(tocPath.map { TocBreadcrumbItem(it) })
    }
}
```

## TOC Auto-Expansion

**Important:** TOC auto-expansion only occurs in ONE scenario:

| Scenario | Auto-expansion |
|----------|---------------|
| Click on line in content | No |
| Click on TOC entry | No |
| **Open book from search/deep link** | **Yes** |

When opening via `OpenBookAtLine`:

```kotlin
// BookContentViewModel.kt:527-528
runCatching { tocUseCase.expandPathToLine(line.id) }
```

**TocUseCase.expandPathToLine():**

```kotlin
suspend fun expandPathToLine(lineId: Long) {
    val tocId = repository.getTocEntryIdForLine(lineId)
    expandPathToTocEntry(tocId)
}

suspend fun expandPathToTocEntry(tocId: Long) {
    // Build path from leaf to root
    val path = mutableListOf<TocEntry>()
    var currentId: Long? = tocId
    while (currentId != null) {
        val entry = repository.getTocEntry(currentId)
        path += entry
        currentId = entry.parentId
    }

    // Expand all ancestors
    for (e in path) {
        stateManager.updateToc {
            copy(expandedEntries = expandedEntries + e.id)
        }
    }
}
```

## TOC Line Selection: Impact on Panels

When the selected line is a **TOC entry heading** (a line that represents a section title), the behavior of the side panels changes significantly. The panels don't hide—they aggregate data from the entire section.

### Detection

A line is identified as a TOC entry via:

```kotlin
val headingToc = repository.getHeadingTocEntryByLineId(lineId)
// headingToc != null ⟺ the line IS a TOC entry heading
```

**Key files:**
- `SeforimRepository.kt:186` - `getHeadingTocEntryByLineId(lineId: Long): TocEntry?`
- `SeforimRepository.kt:195` - `getLineIdsForTocEntry(tocEntryId: Long): List<Long>`

### Resolution Structure

```kotlin
// CommentariesUseCase.kt:591-625
private data class BaseLineResolution(
    val baseLineIds: List<Long>,           // Lines to consider for aggregation
    val headingTocEntryId: Long? = null,   // Non-null if line is TOC entry
    val headingBookId: Long? = null,       // Book containing the TOC
)
```

### Impact by Panel

| Panel | Normal Line | TOC Entry Line |
|-------|-------------|----------------|
| **Commentaries** | Comments for this line only | Aggregates comments from **up to 128 lines** of the section |
| **Sources** | Sources for this line only | Aggregates sources from entire section |
| **Targum** | All available Targumim | **Filtered to default Targum only** (if exists) |

**Important distinction with multi-line selection:**

| Selection Type | `isTocEntrySelection` | Panel Data |
|---------------|----------------------|------------|
| TOC entry click | `true` | Primary line content only (default behavior) |
| Manual Ctrl+click | `false` | Aggregated from ALL selected lines |

### Commentaries Aggregation

**File:** `CommentsForLineOrTocPagingSource.kt:36-53`

```kotlin
if (resolvedLineIds == null) {
    val headingToc = repository.getHeadingTocEntryByLineId(baseLineId)
    resolvedLineIds = if (headingToc != null) {
        // LINE IS A TOC HEADING: load comments from ENTIRE SECTION
        val lines = repository.getLineIdsForTocEntry(headingToc.id)
            .filter { it != baseLineId }
        // Sliding window centered on current line (max 128 lines)
        val half = maxBatchSize / 2
        val start = max(0, idx - half)
        val end = min(lines.size, start + maxBatchSize)
        lines.subList(start, end)
    } else {
        // Normal line: just this line
        listOf(baseLineId)
    }
}
```

### Sources/Targum Aggregation

**File:** `LineTargumPagingSource.kt:33-41`

Same pattern as commentaries—aggregates links from the entire TOC section.

### Special Targum Filtering

**File:** `CommentariesUseCase.kt:636-645`

```kotlin
private fun filterTargumConnections(
    connections: List<CommentarySummary>,
    resolution: BaseLineResolution,
    defaultTargumId: Long?,
): List<CommentarySummary> {
    // If line is TOC AND default Targum exists: STRICT FILTERING
    if (resolution.headingTocEntryId == null || defaultTargumId == null)
        return connections

    return connections.filter { summary ->
        summary.link.connectionType != ConnectionType.TARGUM ||
        summary.link.targetBookId == defaultTargumId
    }
}
```

### Flow Diagram

```
User selects a line (possibly a TOC heading)
                    ↓
    repository.getHeadingTocEntryByLineId(line.id)
                    ↓
            headingToc != null?
                    ↓
    ┌─── YES: It's a TOC ──────────────────┐
    │                                       │
    │  CommentariesUseCase will load:       │
    │   • Up to 128 lines from section     │
    │   • Aggregated commentaries          │
    │   • Aggregated sources               │
    │   • Targum FILTERED to default       │
    │     (if default exists)              │
    │                                       │
    │  isTocEntrySelection = true          │
    │  Panels show PRIMARY LINE only       │
    │                                       │
    └───────────────────────────────────────┘

    ┌─── NO: Normal line ──────────────────┐
    │                                       │
    │  CommentariesUseCase will load:       │
    │   • Comments for this line only      │
    │   • Sources for this line only       │
    │   • All available Targumim           │
    │                                       │
    └───────────────────────────────────────┘
```

### Key Point: Panels Are Never Hidden

**Important:** No panel is hidden or disabled when the selected line is a TOC entry. All panels remain visible and functional—they simply change:

1. **Data scope** (single line vs. entire section)
2. **Filtering** (Targum restricted to default if TOC)

The visibility of panels is controlled by user preferences (`showCommentaries`, `showSources`, `showTargum`), not by whether the line is a TOC entry.

## Dual-Level Selection Model

The app uses a "sticky" selection system for commentaries, links, and sources:

### Per-Book (Sticky)
- User's persistent choices: "Always show Rashi and Tosafot"
- Stored in `selectedCommentatorsByBook[bookId]`
- Persisted across app restarts

### Per-Line (Temporary)
- Intersection of sticky selections with what's available for this specific line
- Stored in `selectedCommentatorsByLine[lineId]`
- Computed on line selection

### UI-Ready (Computed)
- Final selection displayed in UI
- Falls back to per-book if per-line is empty

```kotlin
// BookContentViewModel.kt:88-134
val uiState = stateManager.state.map { state ->
    val lineId = state.content.primarySelectedLineId
    val bookId = state.navigation.selectedBook?.id

    val selectedCommentators = when {
        lineId != null -> {
            val perLine = state.content.selectedCommentatorsByLine[lineId].orEmpty()
            perLine.ifEmpty {
                state.content.selectedCommentatorsByBook[bookId].orEmpty()
            }
        }
        else -> state.content.selectedCommentatorsByBook[bookId].orEmpty()
    }

    state.copy(
        content = state.content.copy(
            selectedCommentatorIds = selectedCommentators,
        ),
    )
}
```

## Prefetching and Caching

### LineConnectionsSnapshot

To optimize performance, line connections are prefetched and cached:

```kotlin
@Immutable
data class LineConnectionsSnapshot(
    val commentatorGroups: List<CommentatorGroup> = emptyList(),
    val targumSources: Map<String, Long> = emptyMap(),
    val sources: Map<String, Long> = emptyMap(),
)
```

**Cache management in BookContentPanel:**

```kotlin
val connectionsCache = remember(selectedBook.id) {
    mutableStateMapOf<Long, LineConnectionsSnapshot>()
}

// Prefetch visible lines
val prefetchConnections = { ids: List<Long> ->
    val missing = ids.filterNot { it in connectionsCache }
    if (missing.isNotEmpty()) {
        providers.loadLineConnections(missing)
            .onSuccess { connectionsCache.putAll(it) }
    }
}
```

**Prefetch trigger:**

```kotlin
// BookContentView.kt:152-172
LaunchedEffect(listState, lazyPagingItems) {
    snapshotFlow {
        listState.layoutInfo.visibleItemsInfo
            .mapNotNull { lazyPagingItems.peek(it.index)?.id }
    }
    .debounce(150)
    .collect { ids -> onPrefetchLineConnections(ids) }
}
```

## State Persistence

When a line is selected, state is automatically persisted:

```kotlin
// BookContentStateManager.kt
fun saveAllStates() {
    val currentState = _state.value
    val persisted = BookContentPersistedState(
        // Multi-line selection
        selectedLineIds = currentState.content.selectedLineIds,
        primarySelectedLineId = currentState.content.primarySelectedLineId ?: -1L,
        isTocEntrySelection = currentState.content.isTocEntrySelection,

        // Per-book selections
        selectedCommentatorsByBook = currentState.content.selectedCommentatorsByBook,
        selectedLinkSourcesByBook = currentState.content.selectedLinkSourcesByBook,
        selectedSourcesByBook = currentState.content.selectedSourcesByBook,

        // TOC state
        expandedTocEntryIds = currentState.toc.expandedEntries,
        selectedTocEntryId = currentState.toc.selectedEntryId ?: -1L,
        // ... other fields
    )
    persistedStore.update(tabId) { current ->
        current.copy(bookContent = persisted)
    }
}
```

**Session restoration:**

```kotlin
// BookContentStateManager.loadInitialState()
private fun loadInitialState(): BookContentState {
    val persisted = persistedStore.get(tabId)?.bookContent ?: BookContentPersistedState()

    return BookContentState(
        content = ContentState(
            selectedLines = emptySet(),  // Line objects loaded by ViewModel
            primarySelectedLineId = persisted.primarySelectedLineId.takeIf { it > 0 },
            isTocEntrySelection = persisted.isTocEntrySelection,
            // ...
        ),
        // ...
    )
}

// BookContentViewModel restores Line objects from persisted IDs
private suspend fun restoreSelectionFromPersistedState() {
    val persisted = persistedStore.get(tabId)?.bookContent ?: return
    val lineIds = persisted.selectedLineIds
    if (lineIds.isEmpty()) return

    val lines = lineIds.mapNotNull { repository.getLine(it) }.toSet()
    stateManager.updateContent {
        copy(
            selectedLines = lines,
            primarySelectedLineId = persisted.primarySelectedLineId.takeIf { it > 0 },
            isTocEntrySelection = persisted.isTocEntrySelection,
        )
    }
}
```

This enables full session restoration on cold boot, including multi-line selections.

## Key Files Reference

| File | Path | Purpose |
|------|------|---------|
| ViewModel | `features/bookcontent/BookContentViewModel.kt` | Event routing, state coordination |
| Events | `features/bookcontent/BookContentEvent.kt` | All event types including `LineSelected(line, isModifierPressed)` |
| State | `features/bookcontent/state/BookContentState.kt` | State model with `selectedLines`, `primarySelectedLineId`, `isTocEntrySelection` |
| StateManager | `features/bookcontent/state/BookContentStateManager.kt` | State mutation & persistence |
| ContentUseCase | `features/bookcontent/usecases/ContentUseCase.kt` | Line selection logic (single & multi) |
| CommentariesUseCase | `features/bookcontent/usecases/CommentariesUseCase.kt` | Commentaries reapplication (single & multi-line) |
| TocUseCase | `features/bookcontent/usecases/TocUseCase.kt` | TOC expansion |
| BookContentView | `features/bookcontent/ui/panels/bookcontent/views/BookContentView.kt` | Line rendering with multi-highlight |
| LineCommentsView | `features/bookcontent/ui/panels/bookcontent/views/LineCommentsView.kt` | Commentaries panel (single/multi) |
| LineTargumView | `features/bookcontent/ui/panels/bookcontent/views/LineTargumView.kt` | Targum/Sources panel (single/multi) |
| BookTocView | `features/bookcontent/ui/panels/booktoc/BookTocView.kt` | TOC panel |
| BreadcrumbView | `features/bookcontent/ui/panels/bookcontent/views/BreadcrumbView.kt` | Breadcrumb display |
| BookContentPanel | `features/bookcontent/ui/panels/bookcontent/BookContentPanel.kt` | Panel orchestration |
| SessionModels | `framework/session/SessionModels.kt` | `BookContentPersistedState` with multi-line fields |
| MultiLineLinksPagingSource | `libs/pagination/src/commonMain/kotlin/.../MultiLineLinksPagingSource.kt` | Paging source for multi-line links |

## Summary

Line selection is a core interaction that:

1. **Updates central state** via `ContentUseCase.selectLine()`
2. **Supports multi-selection** via Ctrl/Cmd+click with `selectedLines` set
3. **Tracks primary line** for TOC highlight and breadcrumb via `primarySelectedLineId`
4. **Distinguishes selection modes** via `isTocEntrySelection` flag
5. **Synchronizes TOC** by computing `selectedEntryId` and `breadcrumbPath` for primary line
6. **Reapplies sticky selections** for commentaries, links, and sources
7. **Triggers UI recomposition** across all dependent panels
8. **Aggregates panel data** for manual multi-selection, preserves default behavior for TOC entry selection
9. **Persists state** for session restoration including multi-line selections
10. **Leverages caching** via `LineConnectionsSnapshot` for performance

The architecture follows unidirectional data flow: user interaction → event →
use case → state manager → UI recomposition.
