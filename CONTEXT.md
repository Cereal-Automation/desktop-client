# Cereal Client

Domain language for the Cereal desktop client. Records project-specific terms so code, tests, and docs name the same concept the same way. Architecture vocabulary (module, seam, depth) lives in the architecture-review skill, not here.

## White-label

**White-label build**:
A rebranded distribution of the Cereal client that is locked to one Brand's designated scripts and presents as a standalone, focused product (e.g. "Ecommerce Product Monitor"). The user can only run and configure the Brand's scripts — there is no marketplace browsing, no installing other scripts. The underlying Cereal platform is invisible. A general mechanism: the ecommerce Brand is the first instance, not a one-off.
_Avoid_: skin, reskin, branded build (ambiguous — every build is branded), fork, OEM build

**Brand**:
The white-label descriptor that parameterizes one white-label build: its identity (name, icon, colors, social/marketplace URLs, auth surface) plus the set of marketplace scripts it is locked to (its Brand scripts). One Brand produces one white-label build. "Ecommerce Product Monitor" is the first Brand. The stock Cereal client is the absence of a Brand (or a default Brand).
_Avoid_: flavor (taken — `mock`/`prod` is the fakes-vs-real axis, orthogonal to Brand), theme, skin, variant, OEM

**Brand scripts**:
The fixed set of marketplace scripts a Brand is locked to, identified by marketplace identity (e.g. `publicIdentifier`) in the Brand descriptor. They are **not** shipped in the build — the appliance downloads and installs them at runtime through the normal marketplace download flow, gated by the user's subscriptions, then auto-installs them on login instead of requiring manual browsing. The appliance exposes these and nothing else, even if the user happens to be subscribed to other scripts.
_Avoid_: bundled scripts (nothing is bundled in the build), package / script package (collides with the `ScriptPackage` domain type), group (collides with `ScriptPackageGroup`), built-in scripts

> Note: "package" is overloaded — `ScriptPackage` (one installable JAR), `ScriptPackageGroup` (a group of script instances), and the marketplace "ecommerce package" product (grants subscriptions, never modelled client-side). Do not name the Brand's script set a "package."

## Notifications

**NotificationResolver**:
The pure domain module that decides which notification channels fire and resolves each one's payload, given a script's request, its per-script overrides, and a snapshot of global settings. Holds the channel-selection and priority rules; performs no I/O.
_Avoid_: NotificationPlanner, NotificationChannelResolver, NotificationService, dispatcher

**ChannelResolution**:
The outcome of resolving one channel: either `Resolved` (a sendable payload plus its preview text) or `MissingConfig` (a required field absent from script, override, and global settings). The `NotificationResolver` returns a list of these.
_Avoid_: NotificationPlan, channel result, resolved channel

**GlobalNotificationConfig**:
A value-object snapshot of every global notification setting the `NotificationResolver` needs — per-channel enabled flags and field values — read once from `ApplicationPreferenceRepository` by the interactor and passed into the pure resolver.
_Avoid_: settings snapshot, notification preferences, global config

**Channel override priority**:
The rule by which a field's value is chosen: script-supplied value first, then the per-script override, then the global setting (`script > override > global`). Owned by the `NotificationResolver`.
_Avoid_: fallback chain, precedence, merge order

**ScriptNotification**:
A script's request to notify — title, message, and optional per-channel payloads (Discord/Telegram/Email). A domain value object describing *what* to send, distinct from the resolved `*NotificationData` that says *how* to send it on a channel.
_Avoid_: notification request, message, alert

**NotificationHistory**:
The persisted record of one `ScriptNotification` a task produced — title, message, timestamp, owning task. Recorded for *every* notification a script sends, regardless of whether any external channel was configured or delivery succeeded, so it is the complete in-app record of what tasks told the user. Each carries N `NotificationHistoryAttempt`s (one per channel tried, SUCCESS/FAILURE). CASCADE-deleted with its task.
_Avoid_: notification log, sent notification, message record

**Notification center**:
The global, per-user, read-only screen that lists `NotificationHistory` across *all the user's live tasks*, newest-first. A read view over already-persisted records — it neither sends nor stores notifications. Scoped to the current user (per-user DB) and to tasks that still exist (a deleted task's notifications leave the center with it). Reached from a sidebar menu item carrying an unseen badge.
_Avoid_: notification inbox, notification log, alerts panel, activity feed

**Unseen notification**:
A `NotificationHistory` whose `timestamp` is newer than `lastSeenAt` — the per-user "last time the center was viewed" timestamp held in `ApplicationPreferenceRepository`. The sidebar badge counts unseen notifications. `lastSeenAt` advances to the newest notification's timestamp on opening the center and stays pinned there while the center is the active screen, so items viewed live never re-badge.
_Avoid_: unread, new notification, pending notification

## Feature flags

**Feature flag**:
A named, compile-time on/off switch that gates an in-progress or risky feature behind a branch in the code. Its default is chosen by build type (`IS_DEBUG` → typically on for local development, release → off in shipped builds), and it is read synchronously — flags never change while the app runs. **Temporary by intent**: a flag exists to hide a feature until it ships, then is deleted along with its dead branch. Distinct from a user **preference** (user-owned, persisted, reactive) and from a remote kill switch (none exists — flags are developer/build-time only).
_Avoid_: toggle (collides with user-facing settings toggles), preference (user-owned, persisted via `ApplicationPreferenceRepository`), experiment / A-B test, remote config, kill switch

## Artifacts

**Artifact**:
A named blob a script produces during a run and hands to the app, persisted to that task and downloadable by the user afterwards. Carries a display name, optional MIME type, the bytes (stored encrypted at rest), and creation time. Distinct from a `Dataset` (user-managed *input*) and from `ExecutionResult` (the run's *outcome*, not a file).
_Avoid_: output, export, file, result, attachment

**emit**:
The verb for a script producing an artifact — `provider.artifact().emit(name, bytes)`. Append-only: every call yields a new `Artifact` scoped to the run; it never replaces a prior one. Connotes "produced as a side effect of running," as opposed to `save`/`write` (which would imply the script controls storage location — it does not).
_Avoid_: save, write, produce, create, output

**ArtifactComponent**:
The SDK component, reached via `provider.artifact()`, through which a running script emits artifacts — the script→app channel for binary output, peer to `LoggerComponent` and `NotificationComponent`. Client-side impl persists the bytes (encrypted) and registers an `Artifact` row.
_Avoid_: FileComponent, OutputComponent, ExportComponent

**Download (an artifact)**:
The user-initiated action of decrypting a stored `Artifact` and writing it to a chosen location on disk. The *user* downloads; the *script* emits — the two directions are never both called "export."
_Avoid_: export, save as, extract

## Secrets

**Secret**:
A credential the user supplied for a configuration item — an API key, a session token, a webhook secret. A domain value object wrapping the plaintext, whose `toString` is a fixed mask (`***`), so it renders masked in the task list, in configuration summaries, and in anything built by string interpolation. Deliberately **not** a data class (destructuring would hand out the plaintext) and **not** an inline value class (the Client drives script configuration interfaces through a dynamic proxy, which name mangling and return-type unboxing would break). The domain owns this type rather than reusing the SDK's `com.cereal.sdk.models.Secret`, and converts to the SDK type at the execution boundary — the same arrangement as `Proxy` → SDK proxy. It controls *where a value may appear*; it is **not** what encrypts the value, since every configuration value is stored as an `EncryptedString` regardless of type (see ADR-0001).
_Avoid_: password (narrower — these are mostly tokens and keys), credential (used for the concept, not the type), SecureString, masked string, sensitive value

**Secret configuration item**:
A configuration item whose script-side return type is `Secret` rather than `String` (`ConfigItemType.SecretConfigItem`, valued by `ConfigValue.SecretValue`, stored under `ValueType.SECRET`). Renders as a masked input field; validity follows the string rule — present but empty is not valid data. Supports per-task values. Cannot be the script identifier and cannot carry a default value (a default returning a credential would be a hardcoded secret in source); a `List<Secret>` is unsupported.
_Avoid_: password field, secure field, credential item

**reveal**:
The verb for obtaining the plaintext out of a `Secret` — `configuration.apiKey().reveal()`. Deliberately a distinct verb rather than a property, so every unwrapping site is findable with one search. Unguarded by design: what a script does with the plaintext afterwards is the script's business. What the type prevents is the *accidental* leak, not a deliberate one.
_Avoid_: unwrap, get, value, plaintext, decrypt (nothing is decrypted here — the value is already in memory)

**Text → Secret migration**:
A shipped script changing an item's return type from `String` to `Secret` while keeping its key name. The stored value is *coerced* to a secret on read and relabelled `SECRET` on the next save; no ciphertext is decrypted, re-encrypted, or moved. One-directional by design — a `SECRET` stored value against a text definition does **not** coerce back, because a downgrade would start printing a value the user was told is protected.
_Avoid_: data migration (nothing is rewritten), conversion, upgrade
