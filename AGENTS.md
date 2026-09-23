# AGENTS.md — Cereal Client & SDK

Agentic coding guide for the Cereal Kotlin multi-module Gradle project.

---

## Project Overview

Multi-module Kotlin desktop application built with **Compose for Desktop**.

| Module                   | Purpose                                            |
|--------------------------|----------------------------------------------------|
| `cereal-client`          | Main desktop app (Clean Architecture)              |
| `cereal-client:sekret`   | Generated Sekret subproject for secret obfuscation |
| `cereal-licensing`       | Licensing logic                                    |
| `cereal-script-sample`   | SDK usage example                                  |

The Cereal SDK is consumed as an external Maven dependency (`libs.cereal.sdk`), not a local module.
The marketplace API client (formerly the `cereal-marketplace-api` module) now lives inside
`cereal-client` under `infrastructure/data/datasource/network/marketplace`.

---

## Build & Run Commands

```bash
# Run the app (debug)
./gradlew run

# Run the app (release / obfuscated)
./gradlew runRelease

# Build all modules
./gradlew build

# Run all tests
./gradlew test

# Run tests for a single module
./gradlew :cereal-client:test

# Run a single test class
./gradlew :cereal-client:test --tests "*PasswordStrengthTest"

# Run a single test method (use full class path)
./gradlew :cereal-client:test --tests "com.cereal.client.application.interactor.settings.discord.SendDiscordTestMessageInteractorTest.run should send Discord test message with correct parameters"

# Lint check (all modules)
./gradlew ktlintCheck

# Auto-fix lint issues
./gradlew ktlintFormat

# Generate secrets (needed after changing sekret.properties)
./gradlew cereal-client:generateSekret
```

---

## Architecture: Clean Architecture

The `cereal-client` module strictly follows Clean Architecture. **Never violate layer boundaries.**

### Layer Rules

**Domain** (`com.cereal.client.domain`)

- Pure Kotlin only — zero dependencies on Compose, Android, or infrastructure frameworks.
- Contains: Entities, Value Objects, data repository interfaces (`*Repository`, persistence of owned
  entities), provider interfaces (`*Provider`, adapters to external services/OS/channels), Domain Exceptions.
- This is the source of truth. No framework imports allowed here. See `domain/AGENTS.md` for the
  repository-vs-provider rule.

**Application** (`com.cereal.client.application`)

- Orchestrates use cases via Interactors.
- Depends only on the Domain layer.
- Naming: `[Verb][Noun]Interactor` (e.g., `CreateTaskInteractor`, `SetDiscordActivityStatusEnabledInteractor`).

**Infrastructure** (`com.cereal.client.infrastructure`)

- Implements Domain interfaces with concrete tech: Room (DB), OkHttp (network), KCEF (browser).
- DI modules live in `infrastructure/di/modules/`.
- Uses Koin for dependency injection.

**Presentation** (`com.cereal.client.presentation`)

- Jetpack Compose for Desktop UI.
- Feature-based structure (e.g., `tasks/`, `settings/`, `authenticate/`).
- Entry point: `presentation/main.kt` (top-level `fun main()`, compiled as `MainKt`).
- ViewModels receive a `CoroutineScope` and `CoroutinesDispatcherProvider`; they do NOT extend any framework base class.

---

## Tech Stack

- **UI**: Compose for Desktop
- **DI**: Koin
- **Database**: Room
- **Networking**: OkHttp
- **Serialization**: Kotlinx Serialization
- **Browser automation**: kdriver (Chrome DevTools Protocol client)
- **Secrets**: `Sekret` (obfuscation) — use `Sekret.myKey(BuildConfig.SEKRET_KEY)`
- **Logging**: SLF4J + Logback — use `LoggerFactory.getLogger(MyClass::class.java)`
- **Error tracking**: Sentry
- **Async results**: `SuspendableResult` from `com.github.kittinunf.result`

---

## Coding Conventions

### Naming

- Interactors: `[Verb][Noun]Interactor`
- Data repositories (interfaces): `[Noun]Repository` — persistence of owned domain entities
- Providers (interfaces): `[Noun]Provider` — adapters to external services, the OS, the browser, or channels
- ViewModels: `[Feature]ViewModel`
- Params nested class inside each Interactor: `data class Params(...)`
- Use `sealed class` or `data class Params` variants when an interactor accepts multiple input shapes.

### Interactor Pattern

Every use case extends `Interactor<ReturnType, Params>` and overrides `run(params)`:

```kotlin
class SetDiscordActivityStatusEnabledInteractor(
    private val applicationPreferenceRepository: ApplicationPreferenceRepository,
) : Interactor<Unit, SetDiscordActivityStatusEnabledInteractor.Params>() {
    override suspend fun run(params: Params) {
        applicationPreferenceRepository.setDiscordActivityStatusEnabled(params.enabled)
    }

    data class Params(val enabled: Boolean)
}
```

For flows, extend `FlowInteractor<ReturnType, Params>`.

### Error Handling

- Throw `CerealException` (or a subclass) for expected domain/application errors.
- `RuntimeException` is automatically caught by the `Interactor` base class, reported to Sentry, and wrapped in
  `CerealException`.
- Never swallow `CancellationException`.
- In the presentation layer, use `handleFailureOrElse` on `SuspendableResult` to route errors to `ErrorResolver`.

### Imports

- Use explicit imports (no wildcard `import com.foo.*`).
- Domain layer: **never** import Compose, Android, AWT, or infrastructure classes.
- Infrastructure layer: fine to import framework classes.

### Formatting

- Enforced by ktlint with `allWarningsAsErrors = true` — run `ktlintCheck` before committing.
- Standard Kotlin formatting: 4-space indentation, trailing commas in multi-line parameter lists.
- Use `ktlintFormat` to auto-fix most violations.

### Commits

- Sign off every commit (`git commit -s`): CI's DCO check fails a pull request with any commit lacking a
  `Signed-off-by` for its author, and `master` accepts changes only through a pull request. See `CONTRIBUTING.md`.

### Compose Best Practices

- Hoist state up; use unidirectional data flow.
- Keep `@Composable` functions free of business logic — delegate to ViewModels.
- Use `androidx.compose.runtime.State` (mutableStateOf) in ViewModels to expose UI state.

### Dependency Injection

- Register all dependencies in Koin modules under `infrastructure/di/modules/`.
- Use Koin scopes (UserScope, TaskScope, etc.) for lifecycle-bound dependencies.
- Inject via constructor — avoid `get()` calls outside DI modules.

---

## Testing

- Framework: **JUnit 5** (Jupiter) + `kotlinx.coroutines.test`, with **Mockk** available for the edges (see below).
  Tests run via `useJUnitPlatform()`. JUnit 4 is on the classpath but new tests should use Jupiter APIs.
- Test files mirror source structure: `src/test/kotlin/com/cereal/client/...`.
- Use `runTest` for coroutine tests.

### Test at the architecture boundaries

Each layer talks to the next through an abstraction it owns. Test at that seam, and put a **real in-memory
fake** on the other side rather than a mock — assert on observable behaviour (resulting state/output), not on
call sequences. Mocks are reserved for genuine *external* edges and for pure interaction verification where
there is no state to observe.

The guiding rule: **fake your own abstractions (repository / data-source interfaces); use the real thing at
genuine external boundaries (Room, HTTP).** Fast and refactor-proof in the middle; the few tests that touch
SQL or HTTP verify the contracts that can actually break.

| Layer under test            | Seam                       | What goes on the other side          | Canonical example |
|-----------------------------|----------------------------|--------------------------------------|-------------------|
| Application (interactor)     | repository interface       | **in-memory repository**             | `application/interactor/task/GetTaskGroupsInteractorTest.kt` |
| Infra: repository impl       | data-source interface      | **fake / in-memory data source**     | — |
| Infra: Room data source      | SQLite                     | **in-memory Room DB** (real SQL)     | `infrastructure/data/datasource/database/integration/KeyValueDataSourceIntegrationTest.kt` |
| Infra: network data source   | HTTP transport             | **MockWebServer** (OkHttp) / **MockEngine** (Ktor) | `infrastructure/data/datasource/network/DigitalOceanSpacesDataSourceTest.kt` |
| Infra: mappers               | —                          | pure unit test, round-trip           | `presentation/tasks/mappers/TaskUiMapperTest.kt` |
| Infra: os/platform           | os interface               | don't test the impl; fake the interface for its callers | — |
| Presentation (ViewModel)     | interactors                | **real interactors + in-memory repos** (or fakes) | the `presentation/**/*ViewModelTest.kt` suite |
| Presentation (screen / Compose) | full ViewModel graph    | **real graph + in-memory repos**, rendered via the screen harness | `presentation/settings/ApplicationSettingsScreenTest.kt` |
| Domain (models / services)   | —                          | pure unit test                       | `domain/model/task/ScriptPackageGroupTest.kt` |

### In-memory fakes

- Reusable in-memory repositories live in `infrastructure/data/repository/inmemory/` (they back the `mock`
  flavor, so they double as test fakes). Prefer these over MockK for interactor and ViewModel tests.
- Caveat: some are pre-seeded for the mock-flavor UI (e.g. `InMemoryProxyRepository` seeds sample groups).
  For deterministic assertions prefer an unseeded one (e.g. `InMemoryTasksRepository`) or a purpose-built
  fake. Put test-only fakes under a `fixtures` package in `src/test/kotlin`.

```kotlin
// Interactor ↔ repository interface — assert behaviour against a real in-memory fake, not a mock.
@Test
fun `should create default group if no groups exist`() = runTest {
    val tasksRepository = InMemoryTasksRepository()
    val interactor = GetTaskGroupsInteractor(tasksRepository)

    val groups = interactor.run(Interactor.None()).filter { it.isNotEmpty() }.first()

    assertEquals(1, groups.size)
    assertEquals("Default", groups.first().name)
}
```

### When MockK is appropriate

Use MockK only when there is no state to observe — true external edges (the OS, a third-party client you
own no fake for) or pure interaction verification. Use `coEvery` / `coVerify` for suspending functions and
`slot<T>()` to capture arguments. Many older tests are MockK-first (legacy style); follow the in-memory-first
approach above for new tests.

```kotlin
// Interaction verification at an edge — no observable state, so capture + verify the call.
@Test
fun `run should send Discord test message with correct parameters`() = runTest {
    val slot = slot<Notification>()
    coEvery { notificationRepository.sendNotification(capture(slot)) } returns Unit

    interactor.run(params)

    coVerify(exactly = 1) { notificationRepository.sendNotification(any()) }
    assertEquals("Cereal Test", (slot.captured as DiscordNotificationData).username)
}
```

### UI tests (screen-level Compose)

Screens can be tested rendered, not just at the ViewModel. The harness in
`testutil/ScreenTestHarness.kt` exposes `runScreenTest { ... }` + `setScreenContent { ... }`:
it renders a single screen via `runDesktopComposeUiTest`, wired to the **real
ViewModel/interactor graph** but with every repository replaced by an in-memory impl
(`InMemoryRepositoryModule`), wrapped in the real `CerealTheme`. A fresh Koin container is
started/stopped per test, so screens resolve their own ViewModel through `KoinJavaComponent.get(...)`.

These tests **self-skip on headless CI**: the harness probes skiko's native graphics lib once via
`assumeTrue(...)`, so they run for real on dev machines and are skipped (not failed) where rendering
is unavailable. The dependency is `compose-ui-test-junit4` (`ui-test-junit4`); use the standard
`onNodeWith*` / `assertIsDisplayed` / `performClick` assertions. See
`presentation/proxy/ProxiesScreenTest.kt`.

```kotlin
@Test fun `renders notification toggle`() = runScreenTest {
    setScreenContent { ApplicationSettingsScreen() }
    onNodeWithText("Notifications").assertExists()
}
```

---

## Secrets Management

- Keys are in `cereal-client/sekret.properties` (use `debug` values locally; never commit real keys).
- After changing `sekret.properties`, regenerate: `./gradlew cereal-client:generateSekret`.
- Access at runtime: `Sekret.myKey(BuildConfig.SEKRET_KEY)`.

---

## Release & Versioning

- Versioning is driven by Git tags: `cereal-client/1.2.0`.
- Release builds enable ProGuard obfuscation. If you see runtime errors only in release, check
  `cereal-client/proguard-rules/`.
- Generate release notes: `make release-notes FROM=cereal-client/1.0.0 TO=cereal-client/1.1.0` (requires GitHub Copilot
  CLI).
- Auto-update metadata (`latest-<os>.json`) is signed and the installer is hash-checked; the client
  rejects unsigned/invalid metadata. Signing uses the `RELEASE_PRIVATE_KEY` secret. See
  `docs/release_signing.md`.

---

## Agent skills

### Issue tracker

Issues are tracked on GitHub at [Cereal-Automation/desktop-client](https://github.com/Cereal-Automation/desktop-client) via the `gh` CLI. External PRs are also a triage surface. See `docs/agents/issue-tracker.md`.

### Triage labels

Canonical roles: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. Only `wontfix` exists on the repo today — the other four will be created on first use by the triage skill. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: one `CONTEXT.md` + `docs/adr/` at the repo root, created lazily by `/grill-with-docs` when terms or decisions actually get resolved. See `docs/agents/domain.md`.
