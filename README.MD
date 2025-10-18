# SeforimApp

Kotlin Multiplatform application (Desktop + Android) built with JetBrains Compose Multiplatform, Jewel windowing, Metro DI, a RAM‑efficient tab navigation system, and AndroidX Paging. The app consumes the composite build SeforimLibrary (`core`, `dao`).

## Contents
- Overview
- Architecture
- Requirements
- Quick Start
- Desktop and Android
- Tests and Lint
- Project Structure
- Contributing
- Security
- License

## Overview
- Compose Multiplatform UI with Jewel themes and native packaging (DMG/MSI/DEB).
- RAM‑efficient tabbed navigation via `TabsNavHost` (see `TAB_SYSTEM_README.md`).
- Dependency injection with Metro (`AppGraph` exposed through `LocalAppGraph`).
- Data provided by `SeforimLibrary` (`core`, `dao`) with `androidx.paging`.
- Hot reload for a fast desktop development loop.

## Architecture
- UI: Compose + Jewel; desktop entrypoint: `SeforimApp/src/jvmMain/kotlin/main.kt`.
- DI: Metro graph via `createGraph<AppGraph>()`, accessed with `LocalAppGraph`.
- Navigation: `TabsNavHost`; features live under `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features`.
- Data: `SeforimLibrary` (modules `core`, `dao`) + AndroidX Paging.
- Shared resources: `src/commonMain/composeResources` (strings, fonts, etc.).

See `TAB_SYSTEM_README.md` for details on the memory‑friendly tab system and state restoration.

## Requirements
- OS: macOS / Linux / Windows.
- Java: JDK 17+ recommended. Gradle toolchains auto‑provision JBR; manual setup is usually unnecessary.
- Android (optional): Android SDK. Set `sdk.dir` in `local.properties` at the project root.

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

## Android
- Install on a device/emulator (debug):

```bash
./gradlew :SeforimApp:installDebug
```

- From Android Studio: open the project and run the imported Android configuration.

## Tests and Lint
- Run all tests:

```bash
./gradlew test
```

- Desktop JVM tests only:

```bash
./gradlew :SeforimApp:jvmTest
```

- Lint the app module:

```bash
./gradlew :SeforimApp:lint
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
  - Source sets: `src/commonMain`, `src/jvmMain`, `src/androidMain`
  - Shared resources: `src/commonMain/composeResources`
  - Desktop entry: `SeforimApp/src/jvmMain/kotlin/main.kt`
- Composite build: `SeforimLibrary` (consumed via coordinates in the app)
  - Modules: `core` (domain), `dao` (persistence), `generator` (tools)
- Local libraries (UI/Utils): `htmlparser`, `icons`, `logger`, `navigation`, `pagination`, `texteffects`, `jewel`
- Tests: `src/<target>Test` (e.g., `commonTest`, `jvmTest`, `androidUnitTest`)

## Contributing
- Conventional Commits: `feat:`, `fix:`, `chore:`, `refactor:`, `docs:`
  - Example: `feat(SeforimApp): add Paging3 for desktop`
- Pull Requests: include a clear description, touched modules, linked issues, UI screenshots/GIFs, and local run steps (`:SeforimApp:run` or `installDebug`). Keep PRs focused and incremental.
- Style: Kotlin + Compose, 4‑space indentation, ~120‑char lines. Naming: classes/objects in PascalCase; functions/properties in camelCase; constants in UPPER_SNAKE_CASE. Composables follow the `SomethingView` and `SomethingViewModel` pattern.
- Organization: put shared logic in `commonMain`; platform code in `androidMain` / `jvmMain`. Avoid leaking platform types across source sets.
- DI: use the Metro graph (`AppGraph` + `@Provides`) and access via `LocalAppGraph` instead of global singletons.

## Security
- Do not commit secrets or API keys. Keep machine‑specific settings in `local.properties`.
- To increase JVM log verbosity locally, add VM option: `-Dorg.slf4j.simpleLogger.defaultLogLevel=debug` in your IDE run config.

## License
AGPL‑3.0. See `LICENSE` for details.

## Key Tools and Dependencies
- JetBrains Compose Multiplatform, Jewel UI
- Metro (DI), KotlinX (Coroutines, Serialization, DateTime)
- AndroidX (Lifecycle, Navigation, Paging3)
- Ktor Client

---
Tip: for a fast desktop dev loop, use hot reload (`hotRunJvm` + `reload`). For the memory‑efficient tab navigation and state handling, see `TAB_SYSTEM_README.md`.
