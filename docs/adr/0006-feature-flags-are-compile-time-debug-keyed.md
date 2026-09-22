# Feature flags are compile-time, debug-keyed, and read directly via a repository

---
Status: accepted
---

We need to gate in-progress or risky features so they can be developed visibly in
local builds while staying hidden in shipped builds. We chose the **simplest thing that
fits**: a `FeatureFlag` enum whose entries declare a default for debug vs release builds,
read synchronously through an injected `FeatureFlagRepository`. There is no runtime
toggling, no persistence, and no remote/server delivery — flipping a flag means editing
the enum and rebuilding. Flags are temporary by intent and deleted once their feature ships.

## Considered Options

- **Compile-time enum + injected repository (chosen)** — `enum class FeatureFlag(val debug: Boolean, val release: Boolean)`
  in the domain layer; a build-aware impl in infrastructure (`FeatureFlagRepositoryImpl`,
  reading `BuildConfig.IS_DEBUG`) registered in Koin; an `InMemoryFeatureFlagRepository`
  fake for tests. Read surface is a plain `fun isEnabled(flag: FeatureFlag): Boolean`.
- **Runtime override via debug menu / KeyValue persistence (rejected)** — would let QA flip
  flags without a rebuild, but adds a debug screen, persistence, and reactive (`Flow`) reads
  for a value that, by design, never changes during a run. Not worth the moving parts now.
- **Remote / operator-controlled kill switches (rejected)** — a network data source with
  polling, caching, and trusted config. Heaviest option; nobody asked to flip features
  post-release without shipping a build.
- **Plain `const val` constants, no abstraction (rejected)** — zero infrastructure, but a
  `const` can't be overridden in tests and reading domain constants directly from
  presentation bypasses DI entirely. Loses testability of both on and off paths.

## Decisions worth calling out (each deviates from an obvious default)

- **`IS_DEBUG`, not flavor, is the default axis.** `./gradlew run` produces `IS_DEBUG=true`
  while `runRelease` and shipped artifacts are `IS_DEBUG=false` — the clean "me developing
  locally" vs "what users get" line. This is independent of the `mock`/`prod` flavor axis
  (fakes vs real data sources), so running the `prod` flavor locally still treats the build
  as a dev build for flag purposes.
- **Synchronous read, no `suspend`/`Flow`.** Other repositories are reactive; this one is
  not, because compile-time flags are effectively constants. Reactivity would be ceremony
  with no payoff.
- **Call sites inject `FeatureFlagRepository` directly** — ViewModels and interactors read
  it without going through an interactor. This is a *documented exception* to the
  "ViewModels only depend on interactors" rule: a feature-flag check is cross-cutting
  infrastructure config, and wrapping a synchronous boolean in a `suspend` +
  `SuspendableResult` interactor would add async ceremony around a constant.

## Consequences

- Flipping a flag requires a rebuild — acceptable for a developer/build-time mechanism.
- The enum stays small only if flags are removed after rollout; flag rot is a real risk
  and the cleanup discipline lives in convention, not tooling.
- Moving to runtime or remote toggling later means changing the read signature
  (`Boolean` → `Flow<Boolean>`/`suspend`) and touching every call site.
- One log line at startup lists active flags (via the existing SLF4J logger) for visibility.
