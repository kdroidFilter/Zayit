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
| Lucene (full-text and vector index) | in process | in a Java process of the app's own (server VM) | in the embedded Java runtime (Zero) |
| Database (`SeforimRepository`) | the app's pooled JDBC driver | `openSeforimRepository(context, path)` | `openSeforimRepository(path)` |
| Semantic model (official builds) | ONNX Runtime Java, model in the jar | ONNX Runtime Android | ONNX Runtime C API (`onnxruntime-c` 1.20.0) |
| Model files | bundled | downloaded with the data | downloaded with the data |

Lucene runs on a real JVM only: ART can't keep up with the Java versions Lucene requires (21 for Lucene 10, 25 for 11).
The mobile runtimes are OpenJDK 27 with openjdk/mobile's port, built by the CI of
[kdroidFilter/openjdk-mobile](https://github.com/kdroidFilter/openjdk-mobile) and downloaded by the build (see
[IOS_RUNTIME.md](../SeforimLibrary/IOS_RUNTIME.md)).

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

On Android, call `AndroidJavaRuntime.configure(context)` before the first search: Lucene runs in a Java process, from
the runtime `./gradlew :searchkit:assembleAndroidJavaRuntime` assembles: `jniLibs/` (the launchers, arm64-v8a and
x86_64, among the app's native libraries, with `packaging.jniLibs.useLegacyPackaging = true`), `jdk/<abi>/` (the JDK of
an ABI: deliver the device's alone) and `jdk-app/` (delivered into its files).
`searchkit-check` is such an app (no UI). On a recent emulator, the process takes ~140 MB of memory with its default
128 MB heap (plus the index pages it maps, which Android reclaims).

On iOS, `IosJavaRuntime` (`classPath`, `options`) configures the embedded runtime before the first search; by default it
runs the jars of `App.app/lib/app`. `./gradlew :searchkit:assembleIosJavaRuntimeDevice` assembles
`static/` (what the app links, options in IOS_RUNTIME.md) and `lib/` (copy it into the bundle as `App.app/lib` in an
Xcode build phase).

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
