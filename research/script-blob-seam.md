# How can a script hand a large blob to the client today?

Research for [#280](https://github.com/Cereal-Automation/desktop-client/issues/280) (map [#277](https://github.com/Cereal-Automation/desktop-client/issues/277)). Facts only; short implications list at the end.

Sources, all read at origin/HEAD on 2026-11-03:

| Repo | Commit | Cited as |
|------|--------|----------|
| desktop-client | `0cb0ec6` | paths from repo root; `client/` = `cereal-client/src/main/java/com/cereal/client/` |
| sdk | `572d72a` (version 1.15.0) | `sdk:` + path; `sdk/component/` = `cereal-sdk/src/main/java/com/cereal/sdk/component/` |
| Script-AI-Browser-Agent | `48c649a` | `agent:` + path; `agent/` = `src/main/kotlin/com/cereal/script/` |

## 1. ArtifactComponent

**Contract (SDK).** One method, `suspend fun emit(name: String, bytes: ByteArray, mimeType: String? = null)` (`sdk:sdk/component/artifact/ArtifactComponent.kt:36-40`), reached via `ComponentProvider.artifact()` (`sdk:sdk/component/ComponentProvider.kt:38-39`). Documented as append-only, persisted "until the user deletes the owning task", and "in-memory payload ... suited to small, bounded outputs" (`ArtifactComponent.kt:13-19`, README `sdk:README.md:386-387`). `emit` returns after durable persistence and throws on failure (`ArtifactComponent.kt:25-28`). Added in sdk commit `63f9f44` (2026-06-18); first tag containing it is **1.10.0**.

**Client wiring.** `ArtifactComponentImpl(artifactRepository, taskId)` is bound in the `Task` Koin scope (`client/infrastructure/di/modules/TaskScopeModule.kt:62-65`) and returned as-is by `ComponentProviderImpl.artifact()` (`client/infrastructure/sdkcomponent/ComponentProviderImpl.kt:39`). It just forwards to `ArtifactRepository.emit(taskId, ...)` (`client/infrastructure/sdkcomponent/ArtifactComponentImpl.kt:10-16`). Design record: `docs/adr/0002-script-artifacts.md`.

### Size limits

- **None enforced.** No size check anywhere on the path: `ArtifactRepositoryImpl.emit` (`client/infrastructure/data/repository/ArtifactRepositoryImpl.kt:23-49`) writes the file, then a row with `sizeBytes = bytes.size` (`:44`); `FileSystemArtifactDataSource.write` (`client/infrastructure/data/datasource/filesystem/FileSystemArtifactDataSource.kt:17-26`) has no cap.
- **Practical ceiling = heap.** The whole payload is a `ByteArray` in the script; `Encryption.encryptBytes` does a single `cipher.doFinal(data)` and returns `GCM_MAGIC + iv + ciphertext` (`client/infrastructure/data/datasource/filesystem/security/Encryption.kt:173-184`), so at least ~2-3 copies are live at once. Download is the same in reverse: `readBytes()` then `decryptBytes()` (`FileSystemArtifactDataSource.kt:33-36`). The packaged app sets no `-Xmx` (`cereal-client/cereal-client.gradle.kts:316-341` only sets locale, `LOG_LEVEL`, `CEREAL_HOME_SUFFIX`), so the JVM default heap applies.
- The ADR deliberately chose in-memory bytes for v1 and lists a streaming/sink `emit` and a per-task retention cap as deferred, additive changes (`docs/adr/0002-script-artifacts.md:33-37`, `:60-64`, `:96-101`).

### Encryption at rest

- **Yes, bytes are encrypted.** AES/GCM with a key derived from the app's `fileEncryptionKey` + the user's `encryptionKey` (`FileSystemArtifactDataSource.kt:10-13`, `:24`, `:54`). Ciphertext format is frozen by ADR-0001 (`docs/adr/0002-script-artifacts.md:51-58`).
- Path on disk: `<home>/Data/Artifacts/<userId>/<taskId>/<artifactId>` (`FileSystemArtifactDataSource.kt:11`, `:52`; `Data` = `databaseDirectory`, `client/infrastructure/CerealConfiguration.kt:105`). (ADR-0002 `:47` says `Artifacts/<taskId>/...`; code adds the `<userId>` level.)
- **Metadata is not encrypted.** The `artifact` Room row (name, MIME, size, path, created_at) is plain columns (`client/infrastructure/data/datasource/database/room/entity/ArtifactEntity.kt:23-39`), and the user DB is plain SQLite via `BundledSQLiteDriver` (`client/infrastructure/data/datasource/database/room/DatabaseConnector.kt:296-332`); encryption in that DB is per-column through `EncryptedString` converters only.

### Does the user see it?

- **Yes, always, in the Tasks screen.** The selected task's inspector renders `TaskArtifactsSection` under the log (`client/presentation/tasks/TaskInspector.kt:146-148`): an "ARTIFACTS" label, "None yet" when empty, and per artifact a row with name, formatted size, timestamp and a Download button (`client/presentation/logs/LogView.kt:212-225`, `:377-410`). The list is live (`TasksViewModel.kt:537-555` observes `ObserveArtifactsInteractor`). Download decrypts to a user-chosen file (`TasksViewModel.kt:123-131`, `ArtifactRepositoryImpl.kt:61-70`).
- There is **no hidden/internal artifact kind**: `Artifact` has only `id, taskId, name, mimeType, sizeBytes, createdAt` (`client/domain/model/artifact/Artifact.kt:14-23`), and nothing filters the list.
- **No programmatic read.** `ArtifactRepository` offers `emit`, `observeArtifacts`, `writeToFile(artifactId, File)`, `deleteForTask` (`client/domain/repository/ArtifactRepository.kt:11-34`); there is no "read bytes" or "list across tasks" for an uploader to use.

### Lifecycle

- **Task delete:** `TaskManager.deleteTask` calls `artifactRepository.deleteForTask(task.id)` (files, `deleteRecursively` of the task dir) before deleting the task row; DB rows go by FK `ON DELETE CASCADE` (`client/application/task/TaskManager.kt:129-137`; `ArtifactRepositoryImpl.kt:72-76`; `FileSystemArtifactDataSource.kt:39-45`; `ArtifactEntity.kt:13-19`). `deleteTask` is reached from deleting the script instance (`client/application/script/ScriptInstanceManager.kt:15`), lowering the concurrent-task count (`client/application/interactor/task/SetConcurrentTaskCountInteractor.kt:45`) and reconfiguring with fewer task configurations (`TaskManager.kt:124`).
- **Restart:** artifacts survive. A restart re-persists the same task id and updates the task row in place precisely so the cascade doesn't fire (`TaskManager.kt:186-188`; `client/infrastructure/data/datasource/database/room/RoomScriptTaskDataSource.kt:36-41`).
- **No pruning.** No count/size/age cap; artifacts accumulate per task across every Start and every Repeat check until the task is deleted (`docs/adr/0002-script-artifacts.md:60-64`). Scope is the **task**, not a run or check: `ArtifactComponentImpl` only knows `taskId` (`ArtifactComponentImpl.kt:6-9`).
- Failure mode: file written before the row; a failed insert leaves an orphan file removed on task delete (`ArtifactRepositoryImpl.kt:33-35`). `emit` throws into the script; uncaught, `TaskExecutor` turns it into `TaskStatus.Error` (`docs/adr/0002-script-artifacts.md:66-69`).

## 2. LoggerComponent's path

- SDK: four methods `error/warn/info/debug(message, [throwable], vararg args)`, documented as writing "to the Cereal application log files" (`sdk:sdk/component/logger/LoggerComponent.kt:3-38`).
- Client: `LoggerComponentImpl(task.id, loggerProvider)` in the Task scope, so the tag is the **task id** (`TaskScopeModule.kt:27-30`; the `parametersOf(manifest.name)` passed at `:44` is ignored by that `scoped` definition). It delegates to `LoggerProviderImpl` (`client/infrastructure/sdkcomponent/LoggerComponentImpl.kt:6-55`).
- `LoggerProviderImpl` formats (`String.format` with args, stack trace appended) and writes to **two sinks** (`client/infrastructure/provider/LoggerProviderImpl.kt:21-38`, `:94-122`):
  1. **SLF4J/Logback** as `"[<taskId>] message"`: console + rolling file `~/Cereal<suffix>/Logs/session.log`, rolled daily or at 10 MB, 30 days kept (`cereal-client/src/main/resources/logback.xml:9-28`). Release builds run at `LOG_LEVEL=info`, so script `debug` lines don't reach the file (`cereal-client/cereal-client.gradle.kts:323-329`, `logback.xml:31`). Plaintext, shared by all tasks and the app itself.
  2. **Room `log_event` table** via fire-and-forget `LogEventRepository.persist` (errors swallowed) (`client/infrastructure/data/repository/LogEventRepositoryImpl.kt:22-29`). Every insert prunes to the newest **500 events per task** (`client/infrastructure/data/datasource/database/room/RoomLogEventDataSource.kt:34`, `:57`; `LogEventDao.kt:18-33`). `message` is a plain `String` column, not encrypted (`client/infrastructure/data/datasource/database/room/entity/LogEventEntity.kt:21-29`). FK cascade on task delete (`:11-19`). This is what the Tasks screen log shows (`TaskInspector.kt:146`).
- `stderr.log`: not produced by anything in this repo (no reference in client source or build). The agent asserts "a release keeps Logs/stderr.log" and prints stack traces with `System.err.println` (`agent:agent/aiagent/AiAgentScript.kt:42-53`); where that file comes from was not traced.

## 3. Other script-to-client channels

| Channel | What it carries | Notes |
|---|---|---|
| `statusUpdate: suspend (String) -> Unit` / `ExecutionResult` | Text into the task's status history | The client already **parses status text** as a data channel: the goal run report's cost comes from a regex `Cost: (\$...)` over status messages (`client/application/agent/AiBrowserAgent.kt:38-49`). |
| `PreferenceComponent` | Key/value strings, read back by the script | Stored as `EncryptedString` in `script_preference`, keyed by script **instance** (`client/infrastructure/data/datasource/database/room/entity/ScriptEntity.kt:370-384`). The agent stores its replay caches and goal specs here as JSON (`agent:agent/aiagent/application/Task.kt:115-128`, `TaskStart.kt:165-180`). ADR-0002 rejected it for binary payloads (`docs/adr/0002-script-artifacts.md:25-28`). |
| `NotificationComponent` | Out-of-band alerts (Discord/Telegram/email/in-app) | Not a storage channel. |
| `UserInteractionComponent` | HTML shown to the user, returns a URL | The agent uses `showHtml` for confirmations (`agent:agent/aiagent/application/TaskStart.kt:418`). |
| `LlmComponent` | Model calls made by the client | The client sees every request/response it proxies, but persists nothing from them beyond cost via status text. Gated by manifest feature `llm` (`ComponentProviderImpl.kt:41-44`). |
| Filesystem | Anything | Scripts run in-process with no file sandbox; the agent writes Chrome profiles to `~/.cereal/ai-browser-agent/profiles` (`agent:agent/aiagent/infrastructure/Chrome.kt:9`). ADR-0002 rejected a well-known-directory convention as an implicit contract (`docs/adr/0002-script-artifacts.md:28-30`). |

What the agent uses today: `logger()`, `preference()`, `llm()`, `userInteraction()`, `notification()`. It never calls `artifact()` (grep over `agent:src/main`).

## 4. How an SDK change ships

1. **SDK.** Version lives in `sdk:gradle.properties:4-5` (`version=1.15.0`, `sdk_version=1.15.0`). Public API is locked by the Kotlin binary-compatibility validator: `cereal-sdk/api/cereal-sdk.api`, `apiCheck` on every PR and master push, "add, don't remove" (`sdk:cereal-sdk/API_COMPATIBILITY.md:20-56`). Pushing a numeric tag publishes `cereal-sdk` and `cereal-test-utils` to the DigitalOcean-Spaces Maven repo (`sdk:.github/workflows/release-libs.yml:3-6`, `:33-47`). Adding a method to `ComponentProvider` is source-breaking for custom providers only (1.14.0 note, `sdk:CHANGELOG.md:16`).
2. **Client.** Pins the SDK in `gradle/libs.versions.toml:41` (`cereal-sdk = "1.15.0"`), compiles it in (`cereal-client/cereal-client.gradle.kts:110`) and bakes the version into `BuildConfig.SDK_VERSION` (`:63-67`), read by `ApplicationRepositoryImpl.getSdkVersion()` (`client/infrastructure/data/repository/ApplicationRepositoryImpl.kt:15`). It must implement any new component, then ship as a normal client release.
3. **Scripts.** Compile against the SDK but **exclude it from the jar** (`agent:build.gradle.kts:34-40`): at runtime `ScriptClassLoader` loads `com.cereal.sdk.*` and `kotlinx.coroutines.*` parent-first from the client (`client/infrastructure/data/datasource/filesystem/reader/ScriptClassLoader.kt:17-18`, `:40-42`). So a script always runs against the **client's** SDK classes. The agent's manifest `sdk_version` is filled from its SDK dependency at build time (`agent:src/main/resources/manifest.json:5`, `agent:build.gradle.kts:115-120`); the SDK docs say nothing fills it in for other scripts (`sdk:README.md:393`).

## 5. Older client, script using a newer component

- **Declared `sdk_version` newer than the client (major.minor only; patch ignored)** (`client/domain/model/script/ScriptPackageExtensions.kt:16-25`):
  - install/update is refused with `ClientUpdateRequiredException` (`client/infrastructure/data/datasource/filesystem/FileSystemScriptsDataSource.kt:155-162`);
  - an already-installed one is still loaded and marked "update required" in the UI (`:227`);
  - Start throws `ClientUpdateRequiredException` before any task runs (`client/application/interactor/script/StartScriptInteractor.kt:31-35`).
- **Undeclared or lower `sdk_version`, script calls a method the client's SDK lacks:** the call links against the client's older `ComponentProvider`/component class, which gives a `NoSuchMethodError` (or `NoClassDefFoundError` for a new type). That is a `LinkageError`, not an `Exception`. `TaskExecutor` catches only `CancellationException` and `Exception` (`client/application/task/TaskExecutor.kt:108-129`), and the agent's own wrapper catches `Exception` too (`agent:agent/aiagent/AiAgentScript.kt:44-53`). By reading (not run), the error escapes `executor.run()` inside `scope.launch` on a `SupervisorJob` with no handler (`TaskManager.kt:45`, `:191-201`), so the final status is never written. The SDK only says the script "fails at runtime instead of asking the user to update" (`sdk:CHANGELOG.md:15`, `sdk:README.md:393`).
- **Manifest `features`:** unknown values are ignored with a warning ("sdk_version is what asks for an update") (`client/infrastructure/data/datasource/filesystem/ScriptPackageDefinition.kt:49-55`). Clients that know a feature can gate a component on it: `llm()` throws `ScriptFeatureNotDeclaredException` when `llm` isn't declared (`ComponentProviderImpl.kt:41-44`). Older clients ignore `features`, so it doesn't replace `sdk_version` (`sdk:CHANGELOG.md:5`).

## Implications

- `ArtifactComponent` works for multi-MB blobs mechanically (no cap, encrypted, survives restart), but each trace would show up in the Tasks screen as a downloadable artifact, accumulate per task with no pruning, and need a new read API before the client can upload it.
- `LoggerComponent` is no fit for traces: plaintext `session.log` shared across tasks, 500-event cap per task in the DB, `debug` dropped in release.
- Any new SDK method or component means: an SDK tag, a client release that implements it, and the agent bumping its SDK (which, because the agent's manifest `sdk_version` tracks its dependency, forces all agent users onto that client's major.minor).
- A script that calls a new component on an older client without a matching `sdk_version` hits a `LinkageError` the executor doesn't catch, so the gate has to come from `sdk_version`.
- The client already reads structured data out of agent status text (cost); that is the existing precedent for an agent-specific side channel.
