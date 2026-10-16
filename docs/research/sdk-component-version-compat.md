# SDK component version compatibility: what happens when `ComponentProvider` gains `llm()`

Research for [#95](https://github.com/Cereal-Automation/desktop-client/issues/95). Checked against
cereal-sdk 1.12.0 and 1.13.0 (cached jars, `javap`), `Cereal-Automation/sdk` on GitHub, and this repo
at `df873e5`.

## Short answer

- **New script (built against an SDK with `llm()`) on an old client.** The script resolves
  `ComponentProvider` from the client's own SDK copy, and that copy has no `llm()`. If the gate
  below does not stop the script, the call fails at runtime with a `LinkageError`:
  `NoSuchMethodError` on the call, or `NoClassDefFoundError` for `LlmComponent`. A default method
  in the SDK would not help, because the old client never loads the new interface.
- **Old script on a new client.** Works. The script never calls `llm()`, and only the client
  implements `ComponentProvider`.
- **Gate.** One exists: the optional `sdk_version` field in the script's `manifest.json`. The
  client rejects a script whose declared major.minor is above the SDK version the client was built
  with, both at install/update and at task start. It only works if the script author sets the
  field. Nothing fills it in automatically, and the marketplace release metadata does not carry it.

## Findings

### 1. `ComponentProvider` is all abstract, and `artifact()` was added as a plain abstract method

`javap -v` on both cached jars shows every method as `ACC_PUBLIC, ACC_ABSTRACT`, including
`artifact()`. There are no JVM default methods and no `ComponentProvider$DefaultImpls`.
(`ArtifactComponent` itself has a `$DefaultImpls` for its optional `mimeType` parameter, so the SDK
compiles Kotlin interface bodies the DefaultImpls way, not as `-Xjvm-default=all`.)

- Source: `~/.gradle/caches/modules-2/files-2.1/com.cereal-automation/cereal-sdk/{1.12.0,1.13.0}/…/cereal-sdk-*.jar`,
  class `com.cereal.sdk.component.ComponentProvider`.
- `artifact()` arrived in [sdk#1](https://github.com/Cereal-Automation/sdk/pull/1) (merged
  2026-06-18) as `fun artifact(): ArtifactComponent` with no body. That PR also updated
  `TestComponentProviderBuilder` in `cereal-test-utils`, the only other implementer. The client
  implements it in
  `cereal-client/src/main/java/com/cereal/client/infrastructure/sdkcomponent/ComponentProviderImpl.kt`.
- The SDK tracks its public API with the Kotlin Binary Compatibility Validator
  (`cereal-sdk/api/cereal-sdk.api`, SDK README). It treats a new abstract interface method as an
  API change, but it says nothing about which client versions a script can run on.

### 2. The SDK classes a script sees come from the client, not from the script jar

`ScriptClassLoader` loads every class under `com.cereal.sdk` (and `kotlinx.coroutines`)
**parent-first**, so it comes from the client's classpath. A script cannot sideload `com.cereal.*`
classes either: a missing `com.cereal` class becomes a `ClassNotFoundException` instead of falling
through to the parent.

- Source: `cereal-client/src/main/java/com/cereal/client/infrastructure/data/datasource/filesystem/reader/ScriptClassLoader.kt`
  (`parentClassloaderPackages`).

As a result, the version of `ComponentProvider`, of `LlmComponent`, and of every other SDK type a
running script uses is the version the **client** was compiled with (`libs.versions.cereal.sdk`,
currently `1.13.0`).

### 3. What the failure looks like if a new script gets past the gate

Bytecode compiled against the newer SDK contains `invokeinterface ComponentProvider.llm()`. On an
older client:

- the `LlmComponent` reference cannot be resolved, because it is a `com.cereal.sdk` class missing
  from the parent loader, so the JVM throws `NoClassDefFoundError`, and/or
- the method reference fails against the client's `ComponentProvider`, so the JVM throws
  `NoSuchMethodError`.

The JVM resolves these lazily, at the first call. `ScriptJarReader.loadScript` catches
`Throwable`, so the script still loads, and the error surfaces inside `onStart`/`execute`.

`TaskExecutor.run` catches only `CancellationException`, `CerealException` and `Exception`
(`cereal-client/src/main/java/com/cereal/client/application/task/TaskExecutor.kt`, around lines
107–131). A `LinkageError` is an `Error`, so it gets past those handlers. The `finally` block
runs, but no terminal `TaskStatus` is written in `TaskManager.startTask`. This conclusion comes
from reading the code; I did not reproduce it. A task in this state probably keeps its last
"Running" status, and Sentry gets no script context with the error. Commit `1d6d9e8` fixed the
same Error-vs-Exception gap for task actions only.

### 4. The existing gate: manifest `sdk_version`

- **Declared:** `manifest.json` → `"sdk_version"`, which is optional
  (`…/filesystem/reader/models/ManifestJson.kt`, `@SerialName("sdk_version") val sdkVersion: String? = null`).
  The script author writes it by hand. The sample script's manifest
  (`cereal-script-sample/src/main/resources/manifest.json`) leaves it out, and nothing in the SDK
  or its build fills it in.
- **Compared:** `isClientUpdateRequired(requiredSdkVersion, currentSdkVersion)` in
  `cereal-client/src/main/java/com/cereal/client/domain/model/script/ScriptPackageExtensions.kt`.
  A `null` value means the script is treated as compatible. Otherwise the client compares
  **major.minor only** (it ignores patch) against `BuildConfig.SDK_VERSION`, which the build sets
  from `libs.versions.cereal.sdk` (`cereal-client/cereal-client.gradle.kts`,
  `ApplicationRepositoryImpl.getSdkVersion()`).
- **Enforced:**
  - At install/update: `FileSystemScriptsDataSource.readJar(…, enforceAgainstSdkVersion)` throws
    `ClientUpdateRequiredException`, and `ScriptDetailViewModel` shows "This script requires a
    newer version of Cereal. Update your client to use it."
  - At start: `StartScriptInteractor.run` throws `ClientUpdateRequiredException` before anything
    executes.
  - Scripts that are already installed and incompatible still load, so `MyScriptsViewModel` and
    `ScriptSelectionViewModel` can mark them "update required" instead of deleting them.
- **SDK guidance** already depends on this mechanism. On `Secret`, the SDK README says declaring
  an SDK version below 1.11.0 while using it "means your script fails to load on older clients
  rather than failing at runtime".

### 5. What is missing

- **The marketplace has no version data.** `Release` (`domain/model/script/ScriptEntitlement.kt`)
  has only `versionName`, `versionCode` and `releaseNotes`. The client finds out a release is
  incompatible only after it downloads and decrypts the jar. The marketplace cannot hide or badge
  such a release in advance, and it cannot offer the newest compatible release instead. The client
  sends `X-Client-Version` on marketplace requests (`ClientVersionInterceptor`), so the backend
  could filter, but nothing in this repo shows that it does.
- **Nothing fills in `sdk_version` automatically.** A script built against an `llm()`-capable SDK
  that leaves the field out passes every check and fails at runtime, as described in §3.
- **Nothing checks per capability at runtime.** No `ComponentProvider` method reports whether
  `llm()` is available, so a script cannot degrade gracefully.
- **Script `Error`s are unhandled in `TaskExecutor.run`** (§3), so a script that gets past the
  gate fails with poor diagnostics.

## Implications for adding `llm()`

1. Adding `fun llm(): LlmComponent` as an abstract method, the same way `artifact()` was added,
   is safe for the client and for old scripts. A default body would not protect old clients and
   is not needed.
2. Ship it in a **minor** SDK release (e.g. 1.14.0). A patch release would get past the gate,
   because the gate ignores patch.
3. Document it in the SDK README as "Available since SDK version 1.14.0", and tell script authors
   to set `"sdk_version": "1.14.0"` (or higher) in `manifest.json`, as the README already does
   for `Secret`.
4. Optional hardening, each a separate decision:
   - catch `LinkageError` in `TaskExecutor.run` and map it to an "update required" status;
   - have the SDK's build tooling write `sdk_version` automatically;
   - add a minimum SDK version to marketplace release metadata.
