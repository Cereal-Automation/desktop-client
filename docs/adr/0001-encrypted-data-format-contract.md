# 1. Encrypted-data format contract: persisted ciphertext is forward-readable

Date: 2026-06-06

## Status

Accepted

## Context

The client encrypts data at rest in exactly one place — `Encryption`
(`infrastructure/data/datasource/filesystem/security/Encryption.kt`). Everything
that persists encrypted bytes funnels through it:

- **Room field values** of type `EncryptedString`, via the
  `*EncryptedStringConverter` type converters (application key and user key).
- **Script JARs** on the filesystem, via `Encryption.writeJar` / `readJarEntry`.

These bytes outlive the build that wrote them. A user upgrades the app and the
new build must still read the database and script jars the *old* build encrypted.
That makes the on-disk ciphertext a **compatibility surface**, exactly like the
Room schema — but unlike the schema it had no versioning, no migration discipline,
and no test that exercised real bytes from a previous release.

This bit us in #618. The key-derivation fix #488 changed AES key material from
a low-entropy ASCII string to the raw SHA-256 digest. It correctly kept a "legacy
key" to read old data — but only wired it to the AES/CBC path, on the assumption
that *cipher mode* implied *key era* ("GCM = new = strong key, CBC = old = legacy
key"). That assumption was false: release 1.10.0 already wrote **AES/GCM** keyed
with the **old ASCII key**. After the upgrade those GCM blobs failed authentication
(`AEADBadTagException`), the converter degraded the value to `null`, and the
non-null Room column assertion crashed the app on boot.

Two structural gaps let it through:

1. **Round-trip tests can't catch this.** Every encryption test did `encrypt(x)`
   then `decrypt(...)` with the *current* key. Both sides move together when key
   derivation or format changes, so the break is invisible.
2. **Cipher mode and key derivation were treated as one axis** when they are two
   independent ones. Any blob on disk may use either key, regardless of mode.

## Decision

Treat persisted ciphertext as a versioned compatibility surface with the same
ceremony as a database migration.

1. **Forward-readability is an invariant.** The current code MUST decrypt any
   ciphertext any shipped release wrote. A change that cannot is a breaking change
   and must add a new read path — never remove or alter an existing one.

2. **Readers for every historical (format, key) era are retained forever.** New
   schemes are added as new writers plus a new reader; old readers stay. The
   detected format (e.g. the GCM magic prefix) selects *how* to decrypt; the reader
   then tries the set of keys valid for that era. Mode never implies key.

3. **A frozen backward-compatibility corpus guards the invariant in CI.**
   `EncryptionBackwardCompatibilityTest` holds Base64 ciphertext blobs captured once
   from each era (CBC+legacy-key, 1.10.0 GCM+legacy-key, current GCM+strong-key) and
   only ever *reads* them. The fixtures are immutable: never regenerate or edit an
   existing one — if a change makes a fixture fail, the change is breaking on-disk
   compatibility. New eras are *appended*.

4. **The decryption failure contract is explicit, not incidental.** A value that
   genuinely cannot be decrypted (e.g. a truly foreign key) must not silently become
   `null` into a non-null column. Callers reconcile the converter's nullable result
   with the column's nullability — let `null` flow only where the column is nullable;
   otherwise fail loudly or reset to a default. This is asserted by test.

5. **Changing the format is an ADR-worthy decision.** Any change to key derivation,
   cipher, or the ciphertext layout updates this ADR and adds a corpus fixture for
   the new era in the same change.

## Consequences

**Positive**

- A key/format regression fails in CI against real bytes, before release.
- The "I forgot old data could still be on disk" mistake is structurally removed:
  the rule is append-a-reader, never-mutate-a-reader.
- On-disk encryption now has the same explicit, reviewable lifecycle as the Room
  schema.

**Negative / costs**

- `Encryption` accretes read paths over time. They are cheap and well-isolated, but
  must not be deleted without a real migration that re-encrypts affected data.
- Every format change now carries mandatory work: a fixture and an ADR touch.

**Notes**

- The whole-database SQLCipher passphrase is *not* part of this surface: the
  `encryptionKey` parameter `DatabaseConnector` accepted at 1.10.0 was never applied
  (the DB opened with a plain `BundledSQLiteDriver`), so the database file is plain
  SQLite. Only field-level `EncryptedString` values and script jars are encrypted.
- Migration is read-old / write-new: a legacy value re-encrypts with the strong key
  on its next write. Data that is never rewritten (e.g. script jars) stays on the
  legacy read path indefinitely — which is why the old reader must never be removed.

<!-- #618 and #488 are issues in the pre-open-source tracker, which is not public. -->
