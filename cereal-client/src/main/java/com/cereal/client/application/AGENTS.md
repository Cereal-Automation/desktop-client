# AGENTS.md — Application Layer

Rules for the `com.cereal.client.application` package.

---

## Purpose

Orchestrate domain objects to fulfill use cases. Define interaction boundaries between the domain and infrastructure layers through injected interfaces.

---

## Contents

- **Interactors** — one class per use case, extends `Interactor<ReturnType, Params>` or `FlowInteractor<ReturnType, Params>`
- **Nested `Params`** — typed input carrier, defined as `data class Params(...)` inside the interactor; use sealed variants (`Params.Discord`, `Params.Slack`) when multiple input shapes exist
- **Application-level Exceptions** — translate domain exceptions to application semantics (e.g., `InvalidParameterException`)
- **Application Services** — coordinate multiple repositories or domain services when logic spans more than one interactor
- **Application Models** — use-case-shaped types that exist only to serve a use case: request/response shapes, orchestration helpers, and factories/builders that inject a collaborator or perform side-effects (e.g. `ScriptInstanceFactory`, `JobTaskFactory`, which link DI scopes). They live in `application/<feature>/` beside the interactors/services that own them — never in `domain/model/`. A type is core domain (and belongs in the domain layer) if a domain `repository`/`provider`/`service` interface references it, or if it would still be needed with every interactor deleted. See `domain/AGENTS.md` → "Core domain models vs. application models" and `docs/adr/0007-core-domain-vs-application-models.md`.

---

## Dependencies

- May depend on the domain layer directly (entities, value objects, domain exceptions)
- May import repository interfaces defined in the domain layer
- May depend on infrastructure **only through interfaces** — never through concrete implementations
- Constructor injection only; never call `get()` outside DI modules

---

## Disallowed

- Direct use of Room DAOs or database sessions
- Direct HTTP calls or SDK usage (OkHttp, Ktor, etc.)
- Importing Compose, AWT, or any UI framework class
- Network or persistence calls that bypass an injected interface
- Returning raw infrastructure types (ORM rows, HTTP response objects)

---

## Interactor Design

- One class per use case — `[Verb][Noun]Interactor` (e.g., `DeleteProxyGroupInteractor`, `SetDiscordActivityStatusEnabledInteractor`)
- Override `suspend fun run(params: Params)` — this is the single entry point
- `Params` is a nested `data class`; use `Interactor.None` when no input is needed
- Return a domain entity, value object, or a simple data wrapper — never a raw infrastructure type
- Side effects delegated entirely to injected repository/adapter interfaces; no inline persistence logic
- Idempotency considered for command interactors where relevant
- Interactors contain **little to no business logic** — delegate all domain decisions to entities, value objects, or domain services

---

## Error Handling

- Throw `CerealException` (or a subclass) for expected, domain-meaningful failures
- `RuntimeException` is automatically caught by the `Interactor` base class, reported to Sentry, and wrapped — do not catch it here unless you need to transform it
- Never swallow `CancellationException`
- Guard invalid `Params` early with an explicit throw (`InvalidParameterException`)

---

## Testing

- Framework: JUnit 5 + Mockk + `kotlinx.coroutines.test`
- Use `runTest` for all suspending tests
- Prefer `mockk<T>(relaxed = true)` unless you need strict verification
- Use `coEvery` / `coVerify` for suspending functions
- Use `slot<T>()` to capture arguments for detailed assertions
- Test naming: `` `run should <outcome> when <context>` ``
- Use `@BeforeEach` when setup is shared across multiple tests; inline construction when tests are few and independent

---

## PR Checklist

- [ ] Each interactor has a single cohesive responsibility
- [ ] `Params` is a nested `data class` (sealed when multiple shapes required)
- [ ] No direct framework, ORM, or HTTP coupling in the interactor body
- [ ] `CancellationException` is never swallowed
- [ ] Tests use `runTest` and mock only repository/adapter interfaces
- [ ] `ktlintCheck` passes with no warnings

---

## Anti-Patterns

| Anti-Pattern | Why It's Wrong |
|---|---|
| Fat God Interactor | Multiple unrelated responsibilities — split into focused interactors |
| Inline persistence | Bypasses the repository interface; breaks testability |
| Returning infrastructure types | Leaks implementation details into callers |
| Broad `catch (e: Exception)` | Masks real failures; let the base class handle unknown exceptions |
| Business logic in `run()` | Domain decisions belong in entities/domain services, not interactors |

---

## Example Skeleton

```kotlin
class DeleteProxyGroupInteractor(
    private val proxyRepository: ProxyRepository,
) : Interactor<Unit, DeleteProxyGroupInteractor.Params>() {

    override suspend fun run(params: Params) {
        if (params.proxyGroup == null) {
            throw InvalidParameterException("Missing ProxyGroup parameter.")
        }
        proxyRepository.deleteProxyGroup(params.proxyGroup)
    }

    data class Params(
        val proxyGroup: ProxyGroup?,
    )
}
```

```kotlin
class DeleteProxyGroupInteractorTest {

    @Test
    fun `run should delete proxy group when valid params provided`() = runTest {
        val proxyRepository = mockk<ProxyRepository>(relaxed = true)
        val interactor = DeleteProxyGroupInteractor(proxyRepository)
        val proxyGroup = ProxyGroup("group-1", "Test Group", 0, emptySequence())

        interactor.run(DeleteProxyGroupInteractor.Params(proxyGroup))

        coVerify(exactly = 1) { proxyRepository.deleteProxyGroup(proxyGroup) }
    }

    @Test
    fun `run should throw InvalidParameterException when proxyGroup is null`() = runTest {
        val proxyRepository = mockk<ProxyRepository>(relaxed = true)
        val interactor = DeleteProxyGroupInteractor(proxyRepository)

        assertThrows<InvalidParameterException> {
            interactor.run(DeleteProxyGroupInteractor.Params(null))
        }

        coVerify(exactly = 0) { proxyRepository.deleteProxyGroup(any()) }
    }
}
```
