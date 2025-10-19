# SeforimApp

Kotlin Multiplatform desktop application built with JetBrains Compose Multiplatform, Jewel windowing, Metro DI, a RAM‑efficient tab navigation system, and Paging. The app consumes the composite build SeforimLibrary (`core`, `dao`).

## Contents
- Overview
- Architecture
- Requirements
- Quick Start
- Desktop
- Tests
- Project Structure
- Contributing
- Security
- License

## Overview
- Compose Multiplatform UI with Jewel themes and native packaging (DMG/MSI/DEB).
- RAM‑efficient tabbed navigation via `TabsNavHost` (see `TAB_SYSTEM_README.md`).
- Dependency injection with Metro (`AppGraph` exposed through `LocalAppGraph`).
- Data provided by `SeforimLibrary` (`core`, `dao`) with Paging.
- Hot reload for a fast desktop development loop.

## Architecture
- UI: Compose + Jewel; desktop entrypoint: `SeforimApp/src/jvmMain/kotlin/main.kt`.
- DI: Metro graph via `createGraph<AppGraph>()`, accessed with `LocalAppGraph`.
- Navigation: `TabsNavHost`; features live under `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features`.
- Data: `SeforimLibrary` (modules `core`, `dao`) + Paging.
- Shared resources: `src/commonMain/composeResources` (strings, fonts, etc.).

See `TAB_SYSTEM_README.md` for details on the memory‑friendly tab system and state restoration.

## Requirements
- OS: macOS / Linux / Windows.
- Java: JetBrains Runtime 21 (JBR 21) is required to build and run this project.
  - Gradle Toolchains will download JBR 21 automatically when you run `./gradlew`. If you use a locally installed JDK or an IDE, make sure it is JDK 21.
  - Terminal: ensure `JAVA_HOME` points to a JDK 21. IntelliJ IDEA: set Gradle JDK to the bundled JBR 21.

## Quick Start
Clone the repo and build:

```bash
./gradlew build
```

## Desktop
- Run the desktop app (Compose):

```bash
./gradlew :SeforimApp:run
```

- Hot reload (Desktop):

```bash
./gradlew :SeforimApp:hotRunJvm
# In another terminal, trigger reload
./gradlew :SeforimApp:reload
```

- Create a native package (DMG/MSI/DEB):

```bash
./gradlew :SeforimApp:createDistributable
```


## Tests
- Run all tests:

```bash
./gradlew test
```

- Desktop JVM tests only:

```bash
./gradlew :SeforimApp:jvmTest
```


Example test scaffold: `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/SampleTest.kt`

```kotlin
import kotlin.test.Test
import kotlin.test.assertTrue

class SampleTest {
  @Test fun runs() { assertTrue(true) }
}
```

## Project Structure
- App module: `SeforimApp` (Kotlin Multiplatform)
  - Source sets: `src/commonMain`, `src/jvmMain`
  - Shared resources: `src/commonMain/composeResources`
  - Desktop entry: `SeforimApp/src/jvmMain/kotlin/main.kt`
- Composite build: `SeforimLibrary` (consumed via coordinates in the app)
  - Modules: `core` (domain), `dao` (persistence), `generator` (tools)
- Local libraries (UI/Utils): `htmlparser`, `icons`, `logger`, `navigation`, `pagination`, `texteffects`, `jewel`
- Tests: `src/<target>Test` (e.g., `commonTest`, `jvmTest`)

## Contributing
- Conventional Commits: `feat:`, `fix:`, `chore:`, `refactor:`, `docs:`
  - Example: `feat(SeforimApp): add Paging3 for desktop`
- Pull Requests: include a clear description, touched modules, linked issues, UI screenshots/GIFs, and local run steps (`:SeforimApp:run`). Keep PRs focused and incremental.
- Style: Kotlin + Compose, 4‑space indentation, ~120‑char lines. Naming: classes/objects in PascalCase; functions/properties in camelCase; constants in UPPER_SNAKE_CASE. Composables follow the `SomethingView` and `SomethingViewModel` pattern.
- Organization: put shared logic in `commonMain`; platform code in `jvmMain`. Avoid leaking platform types across source sets.
- DI: use the Metro graph (`AppGraph` + `@Provides`) and access via `LocalAppGraph` instead of global singletons.

## Security
- Do not commit secrets or API keys. Keep machine‑specific settings in `local.properties`.
- To increase JVM log verbosity locally, add VM option: `-Dorg.slf4j.simpleLogger.defaultLogLevel=debug` in your IDE run config.

## License
AGPL‑3.0. See `LICENSE` for details.

## Key Tools and Dependencies
- JetBrains Compose Multiplatform, Jewel UI
- Metro (DI), KotlinX (Coroutines, Serialization, DateTime)
- Lifecycle, Navigation, Paging3
- Ktor Client

---
Tip: for a fast desktop dev loop, use hot reload (`hotRunJvm` + `reload`). For the memory‑efficient tab navigation and state handling, see `TAB_SYSTEM_README.md`.
