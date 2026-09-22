# AGENTS.md — Infrastructure Layer

Agentic coding guide for `cereal-client/src/main/java/com/cereal/client/infrastructure`.

---

## Purpose

Implement every interface defined in the domain layer using concrete technology: Room for persistence, OkHttp for HTTP, Discord RPC and Telegram/Discord webhooks for notifications, the filesystem for scripts and datasets, and Koin for dependency wiring. This layer is the only place where framework dependencies are allowed to appear.

---

## Package Structure

```
infrastructure/
├── CerealConfiguration.kt       – App-wide config (URLs, paths, secrets, version)
├── data/
│   ├── Settings.kt              – Infrastructure-level constants (e.g. DEFAULT_PROXY_PORT)
│   ├── datasource/
│   │   ├── auth/                – Session/token management (UserSession, UserTokenDataSource)
│   │   ├── csv/                 – CSV I/O (CsvReader, CsvWriter)
│   │   ├── database/            – Room DB setup, DAOs, entities, mappers, TypeConverters
│   │   │   └── room/            – Room internals (DAOs, entities, encrypted converters)
│   │   ├── discord/             – Discord RPC native bridge (DiscordRpcDataSource)
│   │   ├── filesystem/          – Script JAR loading, proxy templates, encryption, temp files
│   │   ├── network/             – OkHttp clients: marketplace API, object storage, downloader
│   │   ├── os/                  – OS notifications, KCEF browser, platform-specific adapters
│   │   └── preference/          – Preference key constants (ApplicationPreferenceKey)
│   ├── notification/            – Notification strategy implementations
│   │   ├── DiscordNotificationStrategy.kt
│   │   ├── EmailNotificationStrategy.kt
│   │   ├── SystemNotificationStrategy.kt
│   │   ├── TelegramNotificationStrategy.kt
│   │   ├── discord/             – Discord HTTP client + mappers/serializables
│   │   ├── telegram/            – Telegram HTTP client + models/mappers
│   │   └── mapper/              – NotificationMapper
│   └── repository/              – Data repository implementations (*RepositoryImpl)
├── provider/                   – Provider implementations (*ProviderImpl); external/OS/channel adapters
├── di/
│   ├── Injector.kt              – startKoin entry point; loads all modules
│   ├── NotificationChannelQualifiers.kt
│   ├── UserScopeProvider.kt
│   └── modules/
│       ├── ApplicationModule.kt
│       ├── DataSourceModule.kt
│       ├── InteractorModule.kt
│       ├── NotificationModule.kt
│       ├── ProviderModule.kt
│       ├── RepositoryModule.kt
│       ├── ScriptInstanceScopeModule.kt
│       ├── ScriptPackageInstanceScopeModule.kt
│       ├── TaskScopeModule.kt
│       ├── UserScopeModule.kt
│       └── ViewModelModule.kt
├── logger/
│   └── SLF4JLogger.kt           – Routes Koin log output through SLF4J/Logback
└── sdkcomponent/                – SDK ComponentProvider bridge implementations
```

---

## Sub-layer Responsibilities

### `data/datasource/` — Technology Adapters (Persistence & Integration)

Concrete technology access. Each subdirectory is responsible for exactly one external concern.

| Subdirectory | Concern | Key rule |
|---|---|---|
| `database/` | Room DB (DAOs, entities, mappers, TypeConverters) | Never expose Room entities outside this subdirectory — map to domain types at the boundary |
| `auth/` | User session and token state | No business rules — only reading/writing session state |
| `csv/` | CSV file I/O | Pure data serialization; no domain logic |
| `filesystem/` | Script JAR loading, file encryption, temp files | `ScriptJarReader`/`ScriptClassLoader` are infrastructure-only; never called from application |
| `network/` | OkHttp API clients (marketplace, storage) | Translate HTTP responses to domain types before returning; translate HTTP errors to domain exceptions |
| `discord/` | Discord RPC native bridge | Isolate native JNI/library calls here |
| `os/` | OS toast notifications, KCEF browser | Platform-specific adapters hidden behind interfaces |
| `preference/` | Preference key constants | Constants only — no logic |

### `data/notification/` — Notification Strategy Implementations

Implement `NotificationStrategy<T>` for each channel (Discord webhook, Email, Telegram, System OS).

- Each strategy translates a domain `Notification` into the channel-specific wire format using mappers in `mapper/`.
- HTTP clients (`DiscordHttpClient`, `TelegramHttpClient`) are private to this package — do not expose them via DI.
- Strategies are registered with Koin qualifiers defined in `di/NotificationChannelQualifiers.kt`.

### `data/repository/` — Data Repository Implementations

Implement domain `*Repository` interfaces (entity-owning persistence) by composing one or more datasources.

- Each file is named `[Noun]RepositoryImpl` and implements exactly one domain repository interface.
- Repositories compose datasources — they do **not** contain business logic.
- Map datasource/ORM results to domain types within the repository; never return Room entities or raw HTTP responses upward.

### `provider/` — Provider Implementations

Implement domain `*Provider` interfaces (adapters to external services, the OS, the browser, device
bridges, third-party SDKs, or outbound channels) by composing one or more datasources.

- Each file is named `[Noun]ProviderImpl` and implements exactly one domain provider interface.
- Same rules as repositories: compose datasources, no business logic, translate external errors to
  domain exceptions, never leak transport/ORM types upward.
- A provider owns no entity collection — if a type's job is CRUD over domain entities we store, it
  belongs in `data/repository/` instead. See the repository-vs-provider rule in the domain AGENTS.md.

### `di/` — Dependency Wiring

Wire all implementations to their interfaces via Koin modules.

- One module file per concern (`DataSourceModule`, `RepositoryModule`, `ProviderModule`, `NotificationModule`, etc.). Data repositories are bound in `RepositoryModule`; providers in `ProviderModule` (with in-memory twins `InMemoryRepositoryModule` / `InMemoryProviderModule` for the `mock` flavor and tests).
- Koin scopes (`UserScope`, `TaskScope`, `ScriptInstanceScope`, `ScriptPackageInstanceScope`) are first-class: use the correct scope for lifecycle-bound dependencies.
- `Injector.initialize()` is the single entry point — do not load modules piecemeal from elsewhere.
- All bindings use constructor injection. Never call `get()` outside DI modules.

### `sdkcomponent/` — SDK Bridge

Implement the SDK's `ComponentProvider` interface to expose app capabilities (logging, notifications, preferences, licensing) to user scripts. Each `*Impl` class bridges one SDK component to the app's domain/application layer via injected interactors or repositories.

---

## Dependencies

- May depend on the domain layer (entities, value objects, repository interfaces, domain exceptions).
- May depend on the application layer interfaces (interactors, application services) for SDK bridge components.
- May use framework and library dependencies: Room, OkHttp, Koin, Kotlinx Serialization, KCEF, Discord RPC, SLF4J/Logback, Sentry.
- Must **not** depend on the presentation layer.
- `data/datasource/` subdirectories must **not** depend on each other directly — compose at the `repository/` level.
- `data/` subdirectories must **not** depend on `di/` — dependency direction is one-way outward.

---

## Disallowed

- Business logic inside repository implementations or datasources — persistence and transport only.
- Passing Room entities, ORM rows, or raw HTTP response objects upward to the application or domain layers.
- Calling interactors or application services from within a datasource or repository.
- Importing Compose, AWT, or UI framework classes anywhere in this layer.
- Storing API keys, tokens, or credentials in source code — use `Sekret` and `CerealConfiguration`.
- Logging sensitive data (tokens, passwords, API keys, personal data) — sanitize before logging.
- Catching `CancellationException`.

---

## Design Notes

### Mapping
- Map Room entities to domain types in dedicated mapper classes under `database/room/` — one mapper per aggregate.
- Map HTTP/API responses to domain types inside the repository implementation, not in the datasource.
- Keep mapping logic explicit and in one place; avoid spreading `.toDomain()` conversions across call sites.

### Encryption
- Room TypeConverters encrypt string values at two levels: application-key (`ApplicationEncryptedStringConverter`) and user-key (`UserEncryptedStringConverter`).
- Encryption keys are loaded via `Sekret` + `BuildConfig.SEKRET_KEY` in `CerealConfiguration` — never hard-code keys.
- `Encryption.kt` in `filesystem/` handles file-level encryption; use it for any file that must be protected at rest.

### Koin Scopes
- `UserScope` — dependencies requiring an authenticated user; closed on logout.
- `TaskScope` — dependencies bound to the lifecycle of a single task run.
- `ScriptInstanceScope` / `ScriptPackageInstanceScope` — dependencies for a running script instance.
- Always open and close scopes explicitly; never leak a scoped dependency into a wider scope.

### Mock Flavor
- For the `mock` build flavor, use in-memory **repositories / providers / components** (e.g. `InMemoryMarketplaceProvider`, `InMemorySessionRepository`, `InMemoryAuthProvider`, `InMemoryScriptRepository`, `FakeLicenseComponent`) rather than mocking datasources. They are bound in `InMemoryRepositoryModule` / `InMemoryProviderModule` (selected in `Injector` for the `mock` flavor), with `SandboxRepositoryModule` layering disk-backed sandbox overrides on top. The only remaining datasource-level fakes are `FakeSubscriptionDataSource` (in-memory subscriptions) and `MockObjectStorageDataSource`.
- Gate fakes behind `BuildConfig.FLAVOR == "mock"` in `DataSourceModule` / `RepositoryModule` / `ScriptPackageInstanceScopeModule`.
- New fakes go next to their real counterparts (`infrastructure/data/repository/Fake*Repository.kt`, etc.) and must never be referenced from production wiring.

### Configuration
- `CerealConfiguration` is injected wherever config values are needed — never read `BuildConfig` or `Sekret` directly outside of `CerealConfiguration` and DI modules.
- Environment-specific values (base URLs, SSL pins, pool sizes) belong in `CerealConfiguration`, not scattered across datasources.

### External HTTP Clients
- Set explicit timeouts on every `OkHttpClient` instance — never rely on defaults.
- Translate HTTP error codes and network exceptions into domain exceptions at the repository boundary; callers must not receive `IOException` or OkHttp types.
- Do not log request/response bodies that may contain credentials or personal data.

---

## Error Handling

- Translate all technology-specific exceptions (SQL constraint violations, `IOException`, HTTP error codes, `AMQPException`, etc.) into `CerealException` or a domain subclass at the repository boundary.
- Datasources may throw technology exceptions internally; repositories must catch and translate them before they cross the infrastructure/application boundary.
- Handle concurrent modification conflicts explicitly (e.g., stale Room entities) — do not silently overwrite.
- Never swallow `CancellationException`.

---

## Logging

Use `LoggerFactory.getLogger(MyClass::class.java)` (SLF4J). Log at the appropriate level:

| What to log | Level |
|---|---|
| DB query execution time, slow queries (> threshold) | `DEBUG` / `WARN` |
| Transaction boundaries (begin, commit, rollback) | `DEBUG` |
| HTTP request URL, method, response code, duration | `DEBUG` |
| External service errors (with correlation ID if available) | `ERROR` |
| Migration execution start/finish | `INFO` |
| Retry attempts and circuit-breaker state changes | `WARN` |

Never log: tokens, passwords, API keys, full request/response bodies containing personal data.

---

## Testing

- **Framework**: JUnit 5 + Mockk + `kotlinx.coroutines.test`
- **Repository tests**: Integration tests against an in-memory Room database or a real DB in a Docker container. Assert observable behavior (store then retrieve), not internal SQL.
- **Datasource/network tests**: Unit tests with mocked `OkHttpClient` or a `MockWebServer` (OkHttp). Assert that the correct request is formed and that responses are correctly mapped.
- **Notification strategy tests**: Unit tests with mocked HTTP clients; assert that the correct payload is sent for each `Notification` variant.
- **Mapper tests**: Pure unit tests — no I/O, no coroutines. Assert round-trip fidelity between domain entity and ORM/DTO.
- Use `runTest` for all suspending tests.
- Use `coEvery` / `coVerify` for suspending mocked functions; `slot<T>()` to capture arguments.

```kotlin
@Test
fun `save and retrieve proxy group should round-trip through Room`() = runTest {
    val proxyGroup = ProxyGroup(id = "group-1", name = "Test Group", port = 8080, proxies = emptySequence())
    repository.save(proxyGroup)

    val retrieved = repository.getById("group-1")

    assertEquals(proxyGroup, retrieved)
}

@Test
fun `get by id should return null when proxy group does not exist`() = runTest {
    val result = repository.getById("nonexistent")

    assertNull(result)
}
```

---

## Anti-Patterns

| Anti-Pattern | Why It Is Wrong |
|---|---|
| Returning Room entities to the application layer | Leaks persistence technology into callers; breaks domain purity |
| Business logic in a `*RepositoryImpl` | Repositories compose datasources — domain decisions belong in entities or interactors |
| Calling an interactor from a datasource | Violates dependency direction; creates circular coupling |
| Reading `BuildConfig`/`Sekret` directly in a datasource | Configuration must flow through `CerealConfiguration` for testability |
| Hard-coding timeouts or URLs in datasources | Prevents environment-specific configuration and makes tests brittle |
| N+1 queries | Fetch related data eagerly or with a single join; do not loop over DB results issuing per-row queries |
| Missing index on frequently queried Room columns | Causes full table scans; add `@Index` to the entity field |
| Logging sensitive data | Credentials and personal data must be redacted before any log statement |
| Leaking a scoped dependency into a wider Koin scope | Causes stale state, memory leaks, or incorrect behavior after scope closure |
| Mock datasources registered in production DI | Silently swaps real implementations; gate strictly behind build flags |

---

## PR Checklist

### Persistence (datasource/database, repository)

- [ ] Repository implements the domain interface exactly — no extra methods, no missing methods
- [ ] Room entities are never returned outside `datasource/database/` — mapped to domain types in the repository
- [ ] Mapper functions are tested with round-trip unit tests
- [ ] New Room entities have appropriate `@Index` annotations for queried fields
- [ ] TypeConverters use the correct encryption level (application vs. user key)
- [ ] Integration tests cover store → retrieve, constraint violations, and null/empty edge cases

### Integration (datasource/network, notification)

- [ ] HTTP client implements the domain/application interface exactly
- [ ] All external errors are translated to domain exceptions at the repository boundary
- [ ] Explicit timeouts set on every `OkHttpClient` instance
- [ ] No API keys, tokens, or credentials in source code or logs
- [ ] Retry / error-translation logic is tested with mocked responses

### DI

- [ ] New dependency registered in the correct Koin module and scope
- [ ] Mock datasources are NOT registered in production modules
- [ ] `Injector.kt` updated if a new module is added

### General

- [ ] No business logic embedded in any infrastructure class
- [ ] `CancellationException` is never caught or swallowed
- [ ] `ktlintCheck` passes with no warnings

---

## Example Skeleton

### Repository Implementation

```kotlin
class ProxyRepositoryImpl(
    private val proxyDataSource: ProxyDataSource,
    private val mapper: ProxyMapper,
) : ProxyRepository {

    override suspend fun getProxyGroups(): List<ProxyGroup> =
        proxyDataSource.getAllGroups().map(mapper::toDomain)

    override suspend fun saveProxyGroup(proxyGroup: ProxyGroup) {
        proxyDataSource.upsert(mapper.toEntity(proxyGroup))
    }

    override suspend fun deleteProxyGroup(proxyGroup: ProxyGroup) {
        proxyDataSource.deleteById(proxyGroup.id)
    }
}
```

### Network Datasource

```kotlin
class RealMarketplaceDataSource(
    private val client: OkHttpClient,
    private val baseUrl: String,
) : MarketplaceDataSource {

    override suspend fun fetchScripts(page: Int): List<ScriptPackage> {
        val request = Request.Builder()
            .url("$baseUrl/scripts?page=$page")
            .build()

        return try {
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) throw MarketplaceException("HTTP ${response.code}")
                val body = response.body?.string() ?: throw MarketplaceException("Empty response")
                Json.decodeFromString<List<ScriptPackageDto>>(body).map(ScriptPackageMapper::toDomain)
            }
        } catch (e: IOException) {
            throw MarketplaceException("Network error: ${e.message}", cause = e)
        }
    }
}
```

### Koin Module

```kotlin
val repositoryModule = module {
    factory<ProxyRepository> {
        ProxyRepositoryImpl(
            proxyDataSource = get(),
            mapper = ProxyMapper(),
        )
    }
}
```
