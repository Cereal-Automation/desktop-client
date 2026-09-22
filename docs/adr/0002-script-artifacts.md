# 2. Script artifacts: a new SDK channel for downloadable per-run output

Date: 2026-06-18

## Status

Accepted

## Context

Scripts could already speak to the app through the SDK's `ComponentProvider`
(`logger`, `notification`, `preference`, `scriptLauncher`, `userInteraction`) —
but every existing channel carries *text or signals*, never *binary output the
user keeps*. There was no way for a running script to produce a file (a CSV
export, a JSON report, a generated image) that the user can download from the app
after the run.

A *Dataset* is the inverse — user-managed **input** to a script — so it does not
fit. The need is for script-produced **output**, owned by the run that made it.

## Decision

Introduce **Artifacts** as a first-class concept with its own SDK channel.

1. **New SDK component, not a piggyback.** Add `ArtifactComponent`, reached via
   `provider.artifact()`, with a client-side impl exactly like
   `NotificationComponentImpl`. Rejected: overloading `preference` (abuses an
   abstraction not meant for binary payloads) and a filesystem convention where
   the script writes to a well-known dir (implicit contract, no type safety,
   scripts could write anywhere). This means the SDK ships the interface before
   the end-to-end feature can land.

2. **In-memory bytes for v1.** `emit(name: String, bytes: ByteArray, mimeType:
   String? = null)`. Name required; MIME optional (inferred from extension when
   absent). Cereal outputs are small and bounded today, so a streaming/sink API
   is deferred — a future overload can add it without breaking the byte-array
   signature.

3. **Append-only.** Every `emit` yields a new `Artifact` scoped to the run; there
   is no key/replace semantics. Handles the multi-artifact single-run case
   naturally and keeps the API minimal. A "latest-only" keyed overload can be
   added later if real scripts need it.

4. **Persisted across restarts; metadata in Room, bytes on disk.** A new
   `artifact` table holds `id`, `task_id` (FK, `ON DELETE CASCADE`), display name,
   MIME, size, `created_at`, and a relative path. Bytes live under
   `~/Cereal/Data/Artifacts/<taskId>/<artifactId>` — never as a SQLite BLOB. This
   mirrors the Dataset split (Room rows + file on disk) and makes cleanup a
   directory delete.

5. **Encrypted at rest.** Artifact bytes funnel through `Encryption` with the
   existing `fileEncryptionKey`, decrypted on download. This keeps the invariant
   that nothing user-data in `~/Cereal/Data` is plaintext, at the cost of an
   encrypt-then-decrypt round-trip for data destined to leave the app. **This puts
   artifact ciphertext on the compatibility surface governed by
   [ADR-0001](./0001-encrypted-data-format-contract.md):** a persisted artifact
   must stay decryptable after an app upgrade, so the forward-readability rules
   there apply.

6. **No auto-pruning.** Artifacts live until their task is deleted (cascade). No
   per-task cap or TTL — silently evicting a file the user meant to download is
   worse than letting them manage it, and logs already set the "bounded by task
   lifetime, not count" precedent. A cap is an easy additive change if runaway
   loopers become real.

7. **`emit()` throws on failure.** A failed encrypt/write/insert propagates into
   the script's `execute()`; uncaught, `TaskExecutor` turns it into
   `TaskStatus.Error` and reports to Sentry. Never silently lose an artifact. A
   script that treats an artifact as optional wraps the call itself.

8. **Live, Flow-backed UI.** `ArtifactRepository.observeArtifacts(taskId)` feeds a
   new **Artifacts** tab in the existing `LogOutputView` drawer, alongside the
   live log stream for the selected task. Download reuses the dataset pattern:
   `rememberFileDialogLauncher()(SAVE)` → ViewModel → a new
   `SaveArtifactToFileInteractor(artifactId, file)` that decrypts and writes.

## Consequences

**Positive**

- A typed, discoverable script→app channel for binary output, consistent with
  every other SDK capability.
- Artifacts inherit the app's encryption-at-rest posture and the Dataset
  Room-plus-disk storage shape — no new storage paradigm.
- Failures are loud and reported, never silent data loss.

**Negative / costs**

- The feature cannot land end-to-end until the SDK ships `ArtifactComponent`
  (develop against a local SDK snapshot until then).
- Encrypt-then-decrypt-on-download is wasted work for export-bound data, accepted
  to preserve the no-plaintext invariant.
- Artifact ciphertext is now a frozen compatibility surface (ADR-0001): a future
  format change carries a corpus fixture and an ADR touch.

**Deferred (additive, non-breaking)**

- Streaming/sink `emit` for large outputs.
- Keyed `emit` for "latest-only" replace semantics.
- Per-task retention cap.
