# AGENTS.md — Domain Layer

Agentic coding guide for `cereal-client/src/main/java/com/cereal/client/domain`.

---

## Purpose

Encapsulate the ubiquitous language, business rules, and invariants of the Cereal application.
This layer is the source of truth. It must remain **pure Kotlin** — zero dependencies on Compose,
AWT, OkHttp, Room, Koin, or any other infrastructure framework.

---

## Package Structure

```
domain/
├── model/
│   ├── app/             – Version, DownloadStatus (sealed)
│   ├── auth/            – PasswordStrength (value object)
│   ├── datasets/        – CustomDatasetGroup, CustomDatasetItem, Proxy, ProxyGroup, DatasetType, Group<T>
│   ├── discord/rpc/     – DiscordPresence, DiscordPresenceBuilder
│   ├── exception/       – Domain exceptions
│   ├── extensions/      – Pure extension functions on domain types
│   ├── logging/         – LoggingEvent, LoggingPriority
│   ├── marketplace/     – MarketplaceSort, MarketplaceDirection, PaginatedResult
│   ├── notification/    – Notification (sealed), channel data classes, NotificationStrategy<T>
│   ├── script/          – ScriptPackage, Script, Manifest, ScriptInstance, configuration/
│   ├── settings/        – ApplicationPreferenceSettings
│   ├── task/            – Task, TaskStatus (sealed), JobTask, UserInteraction (sealed), ScriptPackageGroup
│   └── user/            – User, Subscription
├── repository/          – Data repository interfaces (one file per aggregate/concept)
└── provider/            – Provider interfaces (adapters to external systems/OS/channels)
```

### Repositories vs. Providers

The domain owns two kinds of outbound abstraction. Name and place each by this rule:

- **Data Repository** (`*Repository`, in `repository/`) — owns a collection of domain entities
  and their persistence lifecycle (create / read / update / delete / query / observe). The source
  of truth is storage *we* control (Room, key-value, files-as-data). Files-as-data still counts as
  a repository — the test is entity ownership, not the storage medium.
- **Provider** (`*Provider`, in `provider/`) — an adapter to a capability we *don't* own: an
  external/remote service, the OS, the browser, a device bridge, a third-party SDK, or an outbound
  channel. It performs effects/queries against that system and owns no entity collection.

Examples: `ScriptRepository` (installed scripts on disk) vs. `ScriptInstallProvider` (marketplace
download); `SessionRepository` (local session/token) vs. `AuthProvider` (marketplace auth);
`LogEventRepository` (persisted log events) vs. `LoggerProvider` (SLF4J logging facade).

### Core domain models vs. application models

`domain/model/` holds **core domain models only** — types that express the business and would
still be needed if every Interactor were deleted. Use-case orchestration shapes are **application
models**: they live in `application/<feature>/` next to the interactors/services that own them, not
here. See `docs/adr/0007-core-domain-vs-application-models.md`.

Classify in this order; the first rule that decides, wins:

1. **Guard (overrides everything).** If a `repository/`, `provider/`, or `service/` interface — or
   another core entity — references the type, it is **core domain** and stays. The dependency rule
   forbids domain depending on application, so a type named in a domain contract is core by
   construction (e.g. `PaginatedResult` is pinned here by `MarketplaceProvider`).
2. **Litmus test.** *"If every Interactor were deleted, would this type still be needed to express
   the business?"* Yes → core domain (stays). No (it only shapes a use case's input/output/
   orchestration) → application model (move to `application/`).
3. **Side-effect test (factories/builders).** A factory that is a *pure function of its inputs* (no
   injected collaborators, no side effects) is core domain. A factory that **injects a collaborator
   and performs orchestration/side-effects is an application model — even if the collaborator is a
   domain interface.** `DiscordPresenceBuilder` (pure construction) stays; `ScriptInstanceFactory` /
   `JobTaskFactory` (inject `ScopeLinker`, link DI scopes) are application models and live in
   `application/`.

---

## Allowed Contents

| Type | Description |
|---|---|
| **Entities** | `data class` or `class` with an identity field (`id`); enforce invariants in `init` blocks |
| **Value Objects** | Immutable `data class`; equality by value; validation in `init` |
| **Sealed Hierarchies** | `sealed class` / `sealed interface` for closed state or variant sets |
| **Domain Exceptions** | Narrow, meaningful exceptions; extend `Exception` or `CerealException` |
| **Repository Interfaces** | Abstract persistence/IO contracts; return domain types or `Flow<T>` |
| **Domain Services / Strategies** | Stateless interfaces (`NotificationStrategy<T>`); only when behavior belongs to no single entity |
| **Factories (pure)** | Companion-object or standalone `object` factory that is a *pure function of its inputs* — no injected collaborators, no side-effects (e.g. `ScriptPackageGroup.createDefault()`). A factory that injects a collaborator or performs orchestration is an **application model** — see "Core domain models vs. application models" above |
| **Type Aliases** | Semantic clarity (`TaskId = String`, `ScriptConfigurationValues = Map<String, Any>`) |
| **Extension Functions** | Pure extensions on domain types; place in `extensions/` or alongside the type |
| **Builders** | Fluent builders for complex value construction (`DiscordPresenceBuilder`) |

---

## Disallowed

- Framework imports: Compose, AWT, OkHttp, Room, Retrofit, Ktor, Koin, Android SDK
- Persistence or transport logic (SQL, HTTP calls, file I/O beyond `java.io.File` references)
- Direct environment or config access (no `System.getenv`, no reading `sekret.properties`)
- Logging statements — raise `LoggingEvent`s consumed by the infrastructure layer instead
- Mutable global state

---

## Accepted Exceptions (Known Pragmatic Deviations)

These existing deviations are acknowledged as pragmatic trade-offs. **Do not replicate this pattern** for new code without explicit justification.

| Location | Deviation | Rationale |
|---|---|---|
| `Task.UserInteraction` | The `Browser`/`ContinueButton`/`TextInput` variants hold `kotlinx.coroutines.CancellableContinuation`, and `Browser` additionally references the SDK's `WebResourceRequest` | The model represents an in-flight suspending UI handshake whose result resumes a coroutine; the continuation and SDK request type are intrinsic to that bridge. Extract behind a domain responder interface + domain request type if this is ever decoupled from the SDK |
| `TasksRepository.setTaskJob` | Accepts a `kotlinx.coroutines.Job` | Stores the live coroutine handle for a running task so the task can be cancelled. `Flow` is a sanctioned coroutine type here, but `Job` is a runtime lifecycle handle; move job-lifecycle tracking to an application-layer task manager when this is refactored |

---

## Naming Conventions

| Concept | Convention | Examples |
|---|---|---|
| Entity | `<Noun>` | `User`, `Proxy`, `ScriptPackageGroup`, `CustomDatasetItem` |
| Value Object | Descriptive noun or `<Qualifier><Type>` | `PasswordStrength`, `Version`, `PaginatedResult<T>` |
| Sealed state | `<Context>Status` / `<Context>Event` / plain noun | `TaskStatus`, `DownloadStatus`, `Notification` |
| Data repository interface | `<Noun>Repository` | `SessionRepository`, `TasksRepository`, `ScriptRepository` |
| Provider interface | `<Noun>Provider` | `AuthProvider`, `NotificationProvider`, `ScriptInstallProvider` |
| Domain Service / Strategy | `<Noun>Strategy<T>` or `<Verb>Service` | `NotificationStrategy<T>` |
| Domain Exception | `<Condition>Exception` | `InvalidScriptConfigurationException`, `ScriptWithNoConfigurationException` |
| Factory (pure) | `<Type>Factory` (pure `object`) or `create*` companion | `ScriptPackageGroup.createDefault()` |
| Builder | `<Type>Builder` | `DiscordPresenceBuilder` |
| Type alias | Semantic noun | `TaskId`, `ScriptConfigurationValues` |
| Enum class | PascalCase class, UPPER_SNAKE_CASE values | `LoggingPriority`, `MarketplaceSort` |

---

## Design Notes

### Invariants
- Enforce all business invariants in `init` blocks — never allow an entity to be constructed in an invalid state.
- **Signal violations by type of model:**
  - **Entities / aggregates with identity** throw a *specific domain exception* (extending `Exception` or
    `CerealException`) — the failure is a named business rule that callers may catch and handle. Example:
    `User.init` throws `InvalidUserException` when `id` or `accessToken` is blank; `ScriptPackageGroup.init`
    validates id length, name length, character set, and minimum item count.
  - **Simple value objects** may use `require(...)` (which throws `IllegalArgumentException`) for local field
    validation — a blank string, an out-of-range number, a malformed URL. This is idiomatic Kotlin and keeps
    small value objects self-contained without a bespoke exception per field. Example: `Proxy.init` uses
    `require` to check address, port range, and the username/password pairing.
- **Do not throw a raw `IllegalArgumentException` from an identity aggregate** where a named domain exception
  belongs — that is the anti-pattern the specific-exception rule guards against. The `require` allowance is for
  value objects only.
- **`NotificationResolver` depends on this policy.** Its `build(...)` helper deliberately *catches* the
  `IllegalArgumentException` thrown by the channel `*NotificationData` value objects' `require` checks and maps
  it to `ChannelResolution.MissingConfig`, so one invalid channel is reported rather than aborting the others.
  Converting those value objects to throw a custom exception would break that mechanism.

### Identity
- Entities that belong to aggregate collections must override `equals` and `hashCode` on `id` only — structural equality (`data class` default) is wrong when identity matters.
- See `ProxyGroup`, `CustomDatasetGroup`, `ScriptPackageGroup` for the canonical pattern.

### Sealed Hierarchies
- Prefer `sealed class` / `sealed interface` over inheritance trees for closed sets of states or variants.
- Group tightly coupled declarations in the same file when the sealed type and its subtypes are always used together (e.g., `Task.kt`, `ScriptPackage.kt`).

### Repositories
- Return domain types, `Flow<T>` for reactive streams, or `Unit` for commands.
- Do **not** return infrastructure types (ORM models, HTTP responses, cursor objects).
- `suspend` functions for one-shot async; `Flow` for streams.
- Non-suspending signatures are fine for fire-and-forget (e.g., `LoggerProvider.*`).

### Flows
- Repository interfaces may declare `Flow<T>` return types — this is a pure abstraction; the `Flow` type itself lives in `kotlinx.coroutines`, which is an acceptable dependency in this layer.

### Value Objects vs. Primitives
- Avoid primitive obsession: wrap meaningful identifiers and measurements in type aliases at minimum; use a `data class` value object when validation or behavior is needed.
- Example: `PasswordStrength` encapsulates evaluation logic and computed properties rather than exposing a raw score.

### Avoiding Anemic Models
- Put behavior with data. Entities should expose domain-meaningful methods, not just getters.
- Computed properties, factory companions, and validation logic belong on the entity/value object itself.

---

## Anti-Patterns

| Anti-Pattern | Why It Is Wrong |
|---|---|
| Anemic entities (only `val` fields, no behavior) | Moves business logic into application or infrastructure layers |
| Infrastructure types in repository interfaces | Breaks the dependency rule; forces domain to know about persistence tech |
| Application orchestration inside entities | Entities should enforce invariants, not call other repositories or services |
| `object` factory depending on Koin (`KoinComponent`) | Ties domain to the DI framework; prefer pure factory functions or move to application layer |
| Logging with SLF4J / Logback in domain | Introduces an infrastructure dependency; emit events instead |
| Catching `CancellationException` | Always let it propagate |

---

## Testing

- **Framework**: JUnit 5 + Mockk + `kotlinx.coroutines.test`
- **Target**: 100% unit test coverage for all `init`-block invariants and computed properties.
- **Style**: Pure unit tests — no I/O, no real coroutine schedulers, no external dependencies.
- **Prefer in-memory fakes** over mocks for repository interfaces when testing entities that collaborate with them.
- **Deterministic**: Entity and value object tests must not depend on clock, random, or network.

```kotlin
// Identity aggregate → specific domain exception.
@Test
fun `User init should throw when access token is blank`() {
    assertThrows<InvalidUserException> {
        User(id = "u-1", name = "Ada", email = "ada@x.io", encryptionKey = "k", accessToken = "")
    }
}

// Simple value object → require(...) / IllegalArgumentException.
@Test
fun `Proxy init should throw when port is out of range`() {
    assertThrows<IllegalArgumentException> {
        Proxy(id = UUID.randomUUID(), address = "127.0.0.1", port = 99999, username = null, password = null)
    }
}

@Test
fun `PasswordStrength should be valid when all rules pass`() {
    val strength = PasswordStrength.evaluate("Str0ng!Pass")
    assertTrue(strength.isValid)
}
```

---

## PR Checklist (Domain)

- [ ] No imports from Compose, AWT, OkHttp, Room, Koin, Retrofit, or Android SDK
- [ ] All invariants enforced in `init` blocks — identity aggregates throw a specific domain exception; simple value objects may use `require(...)` (see Invariants under Design Notes)
- [ ] Identity entities override `equals`/`hashCode` on `id` only
- [ ] Repository interfaces return domain types or `Flow<T>` — no infrastructure types
- [ ] Naming follows the conventions table above
- [ ] New entity/value object has unit tests covering all invariants and computed properties
- [ ] `ktlintCheck` passes with no warnings
