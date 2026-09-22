# Implementation plan — Script Artifacts

Lets a running script produce one or more named binary **artifacts** (CSV exports,
JSON reports, generated images, …) that the host persists against the task and the
user can download from the app after the run.

**Design reference:** [ADR-0002](../adr/0002-script-artifacts.md). Glossary: the
*Artifacts* section of [CONTEXT.md](../../CONTEXT.md).
**SDK side:** shipped via
`Cereal-Automation/sdk#1`
(merged to `master`) — `ArtifactComponent.emit(name, bytes, mimeType?)` +
`ComponentProvider.artifact()`.

Phases are vertical slices, each independently testable; later phases depend on
earlier ones.

---

## Phase 0 — SDK dependency (gating)

sdk#1 is merged to `master`, but the client consumes `com.cereal-automation:cereal-sdk`
as a *released* Maven version (currently `1.9.1`). Remaining work:

- Cut an SDK release (tag) and bump `cereal-sdk` in
  [gradle/libs.versions.toml](../../gradle/libs.versions.toml) to it.
- Stopgap to unblock before a release exists: `./gradlew publishToMavenLocal` in the
  SDK, add `mavenLocal()` to the client repositories, point at the snapshot; re-pin to
  the real version later.

`provider.artifact()` will not resolve until the consumed version exposes it.

## Phase 1 — Domain (pure Kotlin)

- `domain/model/artifact/Artifact.kt` — value object: `id`, `taskId`, `name`,
  `mimeType: String?`, `sizeBytes: Long`, `createdAt: Instant`. **No bytes** — content
  is loaded on demand.
- `domain/repository/ArtifactRepository.kt`:

  ```kotlin
  interface ArtifactRepository {
      suspend fun emit(taskId: String, name: String, bytes: ByteArray, mimeType: String?)
      fun observeArtifacts(taskId: String): Flow<List<Artifact>>
      suspend fun writeToFile(artifactId: String, destination: File)  // decrypt + write
      suspend fun deleteForTask(taskId: String)                        // removes on-disk dir
  }
  ```

## Phase 2 — Infrastructure persistence

- `infrastructure/data/datasource/database/room/entity/ArtifactEntity.kt` — model on
  `LogEventEntity`: FK to `TaskEntity` with `onDelete = CASCADE`, `@Index(["task_id"])`.
  Columns: `id`, `task_id`, `name`, `mime_type`, `size_bytes`, `relative_path`,
  `created_at`.
- `.../room/dao/ArtifactDao.kt` — `insert`, `observeByTaskId(taskId): Flow<List<…>>`,
  `getById(id)`, `getPathsForTask(taskId)` (for file cleanup). **No `pruneExcess`**
  (ADR-0002: no auto-pruning).
- `RoomDatabases.kt` — add `ArtifactEntity::class` to `UserRoomDatabase`, add
  `abstract fun artifactDao()`, bump `version = 9 → 10`.
- `DatabaseConnector.kt` — add `MIGRATION_9_10` (same shape as `MIGRATION_6_7`'s
  `log_event` create) and register it in `addMigrations(...)`. Commit the exported
  schema JSON (`exportSchema = true`).
- `infrastructure/data/repository/ArtifactRepositoryImpl.kt`:
  - Bytes under `~/Cereal/Data/Artifacts/<taskId>/<artifactId>` via
    `ApplicationConfig.databaseDirectory`.
  - Encrypt with `Encryption.encryptBytes(bytes, fileKey)` / `decryptBytes` — the **same
    GCM path script jars use**, so no new ADR-0001 corpus era (reuses the existing
    format; no new fixture).
  - **Key sourcing — the one thing to confirm:** script jars build
    `Encryption.getEncryptionKey(config.fileEncryptionKey, user.encryptionKey, 32)`. The
    repo runs mid-task with no `User` in hand; resolve the current user's key the way
    `UserEncryptedStringConverter` does
    (`UserScopeProvider.currentScope.get(named("UserEncryptionKey"))`). Verify the exact
    accessor before locking the key construction.
  - Always encrypt (no LOCAL-env skip — matches `FileSystemScriptsDataSource`, unlike
    the `EncryptedString` converter).
- **Task-deletion file cleanup (easy to miss):** Room CASCADE deletes the *rows* but not
  the *files*. Find the task-delete path (`DeleteTaskInteractor` / `TasksRepository.delete`)
  and call `artifactRepository.deleteForTask(taskId)` there. Logs have no on-disk files, so
  there is no existing precedent — this wiring is net-new.
- **DI:** register `ArtifactRepository` in the repository Koin module (prod flavor).
- **Mock flavor:** `infrastructure/data/repository/inmemory/InMemoryArtifactRepository.kt`
  (backs mock flavor + doubles as test fake), registered in the mock module +
  `InMemoryRepositoryModule`.

## Phase 3 — SDK component bridge

- `infrastructure/sdkcomponent/ArtifactComponentImpl.kt` (template:
  `NotificationComponentImpl`):

  ```kotlin
  class ArtifactComponentImpl(
      private val artifactRepository: ArtifactRepository,
      private val taskId: String,
  ) : ArtifactComponent {
      override suspend fun emit(name: String, bytes: ByteArray, mimeType: String?) =
          artifactRepository.emit(taskId, name, bytes, mimeType)
  }
  ```

  (ADR-0002: `emit` throws → repo exceptions propagate → `TaskExecutor` turns them into
  `TaskStatus.Error` + Sentry. No catch here.)
- `ComponentProviderImpl.kt` — add the 7th constructor param + `override fun artifact()`.
- `TaskScopeModule.kt` — add
  `scoped<ArtifactComponent> { ArtifactComponentImpl(get(), getSource<Task>()!!.id) }`
  and add it to the `ComponentProviderImpl(...)` call.

## Phase 4 — Application interactors

- `application/interactor/artifact/ObserveArtifactsInteractor.kt` —
  `FlowInteractor<List<Artifact>, Params(taskId)>` over `observeArtifacts`.
- `application/interactor/artifact/SaveArtifactToFileInteractor.kt` —
  `Interactor<Unit, Params(artifactId, file: File)>` calling `writeToFile` (template:
  `DownloadDatasetTemplateFileInteractor`).

## Phase 5 — Presentation

- `LogOutputView.kt` → tabbed drawer **Logs | Artifacts**. Artifacts tab lists
  name/size/time with a per-row download button.
- `TasksViewModel.kt` — alongside `observeLogEventsForTask`, add
  `observeArtifactsForTask(task)` exposing `artifacts: State<List<Artifact>>`;
  `onDownloadArtifact(artifact, file)` → `SaveArtifactToFileInteractor` with
  `handleFailureOrElse(errorResolver)`.
- Download wiring: `rememberFileDialogLauncher()(SAVE)` defaulting the filename to
  `artifact.name` → ViewModel.

## Phase 6 — Tests (per the testing matrix in AGENTS.md)

| Seam | Test | Other side |
|------|------|-----------|
| Room | `ArtifactDao` integration — insert/observe/cascade-on-task-delete | in-memory Room (real SQL) |
| Repo encryption | `ArtifactRepositoryImpl` emit→writeToFile **round-trip** decrypts identical bytes; `deleteForTask` removes the dir | real `Encryption` + temp dir |
| Interactor | `ObserveArtifactsInteractor` / `SaveArtifactToFileInteractor` | `InMemoryArtifactRepository` |
| Component | `ArtifactComponentImpl` forwards emit; propagates repo failure | in-memory repo |
| ViewModel | `TasksViewModel` shows artifacts for selected task; download routes errors | real interactors + in-memory repos |
| Screen | Artifacts tab renders + download button (self-skips headless) | real graph + in-memory repos |

## Risks / call-outs

1. **File-key sourcing** (Phase 2) — the single unconfirmed detail; resolve before
   writing the crypto.
2. **File cleanup on task delete** — net-new wiring with no log precedent; easy to forget.
3. **Migration discipline** — version bump + registered migration + committed schema JSON,
   or release builds break on existing DBs.
4. **ADR-0001** — artifacts reuse the existing GCM era, so *no* new backward-compat
   fixture; only a format *change* would require one.
