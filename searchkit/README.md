# searchkit

Zayit's search without its UI, for the desktop app and the mobile ones (Android 10+, iOS): the engines (lexical, fused
with the semantic search in the official builds), the lookup (books, authors, tables of contents), the search use cases
and the view models of the search screens. The desktop app uses it as is; a mobile app only adds its screens.

Verified on real data: `SearchKitDump` (lookup, hybrid search, snippets, highlights by meaning, smart find) gives the
same text on the JVM, in the iOS simulator and on Android 11 and 15 emulators (`jvmTest`, `iosSimulatorArm64Test`,
`searchkit-check`).

## What runs where

| | Desktop (JVM) | Android 10+ | iOS |
|---|---|---|---|
| Lucene (full-text and vector index) | in the app's JVM | on HotSpot in the app's process (NJA) | on Zero linked into the app (NJA) |
| Database (`SeforimRepository`) | the app's pooled JDBC driver | `openSeforimRepository(context, path)` | `openSeforimRepository(path)` |
| Semantic model (official builds) | ONNX Runtime Java, model in the jar | ONNX Runtime Android | ONNX Runtime C API (`onnxruntime-c` 1.20.0) |
| Model files | bundled | downloaded with the data | downloaded with the data |

Lucene runs on a real JVM only: ART can't keep up with the Java versions Lucene requires (21 for Lucene 10, 25 for 11).
SeforimLibrary's search reaches it through [Nucleus JVM Access](https://github.com/NucleusFramework/NucleusJvmAccess):
its façade (`search-facade`) runs on the JVM, its common code calls the proxies NJA generates; the mobile JVMs are
jdk-mobile's OpenJDK 27 (see [JVM_RUNTIME.md](../SeforimLibrary/JVM_RUNTIME.md)).

Everything else (query building, dictionary, ranking, snippets, highlights, RRF fusion, tokenizer, use cases, view
models) is common code.

## Using it from an app

```kotlin
// The data: seforim.db with seforim.db.lucene, seforim.db.lookup.lucene and lexical.db beside it, downloaded into the
// app's storage (like the desktop's data). The semantic model too, in the official builds.
val repository = openSeforimRepository(context, dbPath)          // iOS: openSeforimRepository(dbPath)
val services = SearchServices(dbPath, repository, SemanticModels(model, tokenizer, staticTable))
services.engine    // SearchEngine: openSession, computeFacets, highlights, semanticFind...
services.lookup    // LuceneLookupSearchService: suggestBooks, suggestAuthors, searchTocPrefix
```

On Android there is nothing to configure: the JVM (`libnja.so`, arm64-v8a and x86_64) and its runtime come with
SeforimLibrary's search library (its native libraries and assets), and start on the first search. `searchkit-check` is
such an app (no UI).

On iOS, `./gradlew :searchkit:assembleIosJavaRuntimeDevice` assembles `static/` (what the app links: the options are in
JVM_RUNTIME.md) and `bundle/` (copy its content into `App.app` in an Xcode build phase: the VM's `lib/` and NJA's
`nja/`).

### The view models

`SearchResultViewModel` (a results tab: search, facets, filters by category/book/TOC, paging, preview, breadcrumbs,
opening a result) and `SearchHomeViewModel` (the search bar: book/author/TOC/reference suggestions, scopes, deep links)
are the desktop's, unchanged. What they need of the app is in `SearchHost.kt`, four small interfaces:

| Interface | Desktop implementation | What a mobile app provides |
|---|---|---|
| `SearchTabs` | `DesktopSearchTabs` (windows and tabs) | its navigation: open a destination (`TabsDestination`), screen still shown |
| `SearchSessionStore` | `DesktopSearchSessionStore` (the session) | where it keeps a screen's `SearchPersistedState` and opens a book at a line |
| `SearchSettings` | `AppSettings` | text size, find-in-page bar, user's name |
| `SearchHistory` | `HistoryStore` | the search history |

`SearchResultViewModel` is built by Metro on the desktop (`@AssistedInject`, `SavedStateHandle` with `StateKeys.TAB_ID`);
a mobile app can call its constructor directly.

## Builds

- **Community**: `addons/semantic/disabled`, lexical search only.
- **Official**: `addons/semantic/enabled` with the private `io.github.kdroidfilter:seforim-semantic` package (Kotlin
  Multiplatform since 0.6.0: publish it before an official CI build), or its sources beside with `-Psemantic.local=true`.

## Tests on real data

```bash
./gradlew :searchkit:jvmTest -PseforimTestDb=<copy of seforim.db>        # build/parity/jvm.txt
./gradlew :searchkit:iosSimulatorArm64Test -PseforimTestDb=<db> \
  -Pseforim.ortXcframework=<onnxruntime.xcframework> -Pseforim.semanticModels=<model dir>   # build/parity/ios.txt
```

Use a copy of the database (an APFS clone: `cp -c`): `SeforimRepository` sets its PRAGMAs on open. On Android, install
`searchkit-check` and its test APK, push the data and the runtime into its files dir and run its `SearchParityTest`
(see its KDoc).

## Not here (the UI's)

- The screens, and the rendering of snippets and lines (`:htmlparser`, Jsoup and Compose `AnnotatedString`: on iOS it
  needs Ksoup).
- The reader's find-in-page (`BookFindMatches`): its candidates come from the engine (`findInBookCandidates`), its
  matches are confirmed on the displayed text, so it follows `:htmlparser`.
