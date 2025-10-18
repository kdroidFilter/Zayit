 # Repository Guidelines
 
 ## Project Structure & Modules
 - App: `SeforimApp` (Kotlin Multiplatform). Sources under `src/commonMain`, `src/jvmMain` (desktop), `src/androidMain` (Android). Shared resources in `src/commonMain/composeResources`.
 - Local libraries: `htmlparser`, `icons`, `logger`, `navigation`, `pagination`, `texteffects`, `jewel`.
 - Composite build: `SeforimLibrary` (modules `core`, `dao`) consumed via Maven coordinates in the app.
 - Tests live in `src/<target>Test` (e.g., `commonTest`, `jvmTest`, `androidUnitTest`).
 
## Architecture Overview
- UI: Compose Multiplatform with Jewel windowing; desktop entry in `SeforimApp/src/jvmMain/kotlin/main.kt`.
- DI: Metro graph (`AppGraph` + `@Provides`), created via `createGraph<AppGraph>()` and exposed with `LocalAppGraph`.
- Navigation: `TabsNavHost` composes screens; features live under `SeforimApp/src/jvmMain/kotlin/io/github/kdroidfilter/seforimapp/features`.
- Data: Provided by `SeforimLibrary` (`core`, `dao`); paging via `androidx.paging`.
- Resources: strings/fonts under `src/commonMain/composeResources`.

## Build, Test, Run
 - Build all: `./gradlew build`
 - Desktop run (Compose): `./gradlew :SeforimApp:run`
 - Hot reload (desktop): `./gradlew :SeforimApp:hotRunJvm`
 - Desktop package (DMG/MSI/DEB): `./gradlew :SeforimApp:createDistributable`
 - Android install (debug): `./gradlew :SeforimApp:installDebug`
 - Tests: `./gradlew test` (all), `./gradlew :SeforimApp:jvmTest`
 - Lint: `./gradlew :SeforimApp:lint`
 
 ## Coding Style & Naming
 - Kotlin + Compose. Use 4‑space indentation, meaningful names, and keep lines ~120 chars.
 - Naming: classes/objects `PascalCase`, functions/props `camelCase`, constants `UPPER_SNAKE_CASE`.
 - Composables in `PascalCase`; prefer the existing pattern `SomethingView` for UI surfaces and `SomethingViewModel` for state.
 - Place shared logic in `commonMain`; platform code in `androidMain`/`jvmMain`. Avoid leaking platform types across source sets.
 - DI: use Metro graph (`AppGraph` with `@Provides`) and access via `LocalAppGraph` instead of singletons.
 
## Testing
- Frameworks: Kotlin Test; Compose UI test artifacts are available. Add tests next to code in `src/<target>Test/kotlin`.
- Naming: mirror the source name with `...Test.kt` (e.g., `ContentUseCaseTest.kt`). Prefer small, focused tests.
- Run desktop tests locally with `./gradlew :SeforimApp:jvmTest`.

### Test Scaffold (Example)
Create `SeforimApp/src/jvmTest/kotlin/io/github/kdroidfilter/seforimapp/SampleTest.kt`:

```kotlin
import kotlin.test.Test
import kotlin.test.assertTrue

class SampleTest {
  @Test fun runs() { assertTrue(true) }
}
```
 
 ## Commits & Pull Requests
 - Prefer Conventional Commits: `feat:`, `fix:`, `chore:`, `refactor:`, `docs:`. Example: `feat(SeforimApp): add Paging3 for desktop`.
 - PRs must include: clear description, scope (modules touched), linked issues, screenshots/GIFs for UI changes, and local run steps (`:SeforimApp:run` or `installDebug`). Keep PRs focused and incremental.
 
## Security & Environment
- Do not commit secrets or API keys. Keep machine‑specific settings in `local.properties`.
- Gradle toolchains provision JBR automatically; no manual JDK setup is usually needed.

## Module Map
- `SeforimApp`: Desktop/Android app; entrypoint `SeforimApp/src/jvmMain/kotlin/main.kt`.
- `SeforimLibrary` (composite build): `core` (domain), `dao` (persistence), `generator` (tools).
- `htmlparser`, `icons`, `logger`, `navigation`, `pagination`, `texteffects`, `jewel`: shared UI/util modules.

## Debugging & Hot Reload
- Desktop debug: run `./gradlew :SeforimApp:run` and attach breakpoints in IDE.
- Hot reload: `./gradlew :SeforimApp:hotRunJvm`, then trigger reload with `./gradlew :SeforimApp:reload`.
- Adjust logging on JVM: add VM option `-Dorg.slf4j.simpleLogger.defaultLogLevel=debug` in your IDE run config.
- Android: `./gradlew :SeforimApp:installDebug` then use Android Studio + Logcat.
