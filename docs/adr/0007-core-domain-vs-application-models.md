# Core domain models live in `domain/model/`; application models live in `application/`

---
Status: accepted
---

`domain/model/` had become a grab-bag holding genuine entities and value objects
(`Task`, `User`, `PasswordStrength`) alongside use-case orchestration helpers
(`ScriptInstanceFactory`, `JobTaskFactory`). Because location no longer signalled
classification, nobody could tell from a file's package whether a type was a stable
core-domain concept or an application-layer concern that had drifted into the domain.
We decided to make the distinction explicit: **core domain models stay in
`domain/model/`; application models move into `application/<feature>/` beside the
interactors and services that own them.** As the first application of the rule,
`ScriptInstanceFactory` and `JobTaskFactory` moved to `application/script/` and
`application/task/`.

## How to classify a type

Applied in order — the first that decides, wins:

1. **Guard (overrides everything).** If a `domain` repository / provider / service
   interface or another core entity references the type, it is **core domain** and
   cannot move — the dependency rule forbids domain depending on application, so a type
   named in a domain contract is core by construction. This pins `PaginatedResult`
   (named by `MarketplaceProvider`), `ScriptSubscriptionResult`, `ChannelResolution`,
   `NotificationResolver`, and `ScriptEntitlement` regardless of how use-case-shaped
   they look.
2. **Litmus test.** *"If every Interactor were deleted, would this type still be needed
   to express the business?"* Yes → core domain. No (it exists only to shape a use
   case's input/output/orchestration) → application model.
3. **Side-effect test (for factories/builders).** A factory that is a *pure function of
   its inputs* (constructs and returns a value, no injected collaborators, no side
   effects) is core domain. A factory that **injects a collaborator and performs
   orchestration/side-effects is an application model — even when the collaborator is a
   domain abstraction.** `ScriptInstanceFactory`/`JobTaskFactory` inject `ScopeLinker`
   and call `linkScriptInstanceToPackage` / `linkTaskToScriptInstance`; that scope-linking
   is application orchestration, so they move. `DiscordPresenceBuilder` (pure construction
   of the `DiscordPresence` value object) stays.

## Considered Options

- **Side-effect test (chosen).** A factory's *behavior* decides its layer: pure
  construction is domain, injected-collaborator orchestration is application. Matches the
  issue's intent ("scope linking is application work however it's abstracted") and the
  pre-existing anti-pattern note in `domain/AGENTS.md` ("`object` factory depending on
  Koin → move to application layer").
- **Framework-import test (rejected).** Treat a type as application *only* if it imports
  a framework (Koin, Room, …). Under this rule the two factories would **stay** in domain,
  because they depend only on the `ScopeLinker` domain interface and import no framework —
  which is precisely the loophole that let orchestration accumulate in `domain/model/`.
  This issue would then move almost nothing and the ambiguity would persist.
- **Move only pure data DTOs, leave services (rejected).** Narrower scope, but it leaves
  orchestrating factories sitting in `domain/model/` — reintroducing the exact "is this
  core or just un-migrated?" ambiguity the distinction exists to remove.

## Consequences

- Location now signals classification: a type in `domain/model/` is core; an application
  model lives in `application/<feature>/` next to its interactors/services. The placement
  rule is documented in `domain/AGENTS.md` ("Core domain models vs. application models")
  and `application/AGENTS.md` (Contents → Application Models).
- Infrastructure may reference application models (it already depends on both layers);
  the moved factories are still resolved through Koin (`ApplicationModule`) — only their
  package changed.
- The guard makes most classification mechanical and refactor-proof: a type named by a
  domain contract is pinned, so the boundary can't be eroded by a careless move without a
  compile break.
- The split is convention, not tooling — a future application model dropped into
  `domain/model/` won't fail the build. The PR checklist and AGENTS.md rule are the
  enforcement surface.
