# Local sandbox scripts

Drop `*.jar` script packages in this folder to have them available in the sandboxed `mock` flavor.

## How it works

When you build/run with `flavor=mock` (the repo default), every `*.jar` here is bundled into the
app's resources under `sandbox-scripts/`. On startup [`SandboxScriptSeeder`](../src/main/java/com/cereal/client/infrastructure/data/repository/SandboxScriptSeeder.kt)
copies each one into the sandbox user's script directory, where
[`FileSystemScriptRepository`](../src/main/java/com/cereal/client/infrastructure/data/repository/FileSystemScriptRepository.kt)
reads it like any installed script. Subscriptions are derived from the installed scripts, so the
periodic sync keeps them instead of pruning them.

This wiring is gated to the `mock` flavor in `cereal-client.gradle.kts`, so these JARs **never ship
in production** (`-Pflavor=prod`).

## Requirements per JAR

- **Plaintext / unencrypted** — the sandbox runs `environment=local`, which reads JAR entries as
  plain bytes. Use a plain `jar` output, not an encrypted/release artifact.
- **Valid `manifest.json` at the JAR root** — e.g.
  ```json
  { "package_name": "com.you.myscript", "name": "MyScript", "version_code": 1,
    "script": "com.you.myscript.MyScript" }
  ```
- **Filename ≠ `release.jar`** — that name is reserved for the bundled `:cereal-script-sample`.

## Notes

- The JARs in this folder are git-ignored on purpose; keep them local.
- Run the sandbox with `./gradlew :cereal-client:run` (defaults to `flavor=mock`, `environment=local`).
