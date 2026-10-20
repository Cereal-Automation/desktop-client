# Script jars are isolated by a filtering class loader, not relocation

---
Status: accepted
---

A script jar bundles its own libraries (ktor, okhttp, kotlinx-serialization and so on), and
they often clash in version with the client's copies, for example okhttp 4 against 5. Today
`ScriptClassLoader` loads child-first with the system class loader as its parent, so every
client library leaks into the script. We prevent collisions **in the client's loader only**.
Each script jar gets its own loader. Its parent is the platform class loader, and the only
client packages it can see are the **Shared API** (`kotlin.`, `kotlinx.coroutines.`,
`com.cereal.sdk.`), loaded parent-first. The filter applies to classes and resources alike.
Script builds don't relocate (shade) anything.

## Considered Options

- **Filtering parent only (chosen).** Works for jars that are already published, which can't
  be rebuilt. Once a script loader no longer sees the client's libraries, a version clash
  can't happen in either direction.
- **Filtering parent plus relocation in Script-Template (rejected).** The only names a
  script still shares with the client are the Shared API, and you can't relocate those
  because their types appear in SDK signatures (JVMS §5.3.4 loader constraints). Relocation
  would add nothing on top of the filter. It would also break reflection that uses string
  names and `META-INF/services` entries (kotlinx-serialization and ktor are fragile here), and
  make Script-Template's ProGuard step more complicated.
- **Relocation with a permissive loader (rejected).** Published jars won't be rebuilt, so it
  can't protect them.
- **OSGi / JPMS (rejected).** Both need metadata in every jar, which breaks published jars.

## Consequences

- Script authors may still shade dependencies for their own reasons. The client neither
  needs nor checks it.
- A script's bundled copy of a Shared API package is silently ignored, so mismatches have
  to be caught at build time or by version checks.
- Old jars that relied on client libraries they never bundled (slf4j, kdriver, okio and
  others) need a legacy path. Widening the Shared API for them isn't one.
- Adopting relocation later would mean changing Script-Template, the SDK and every script.
- Decided in the "Isolate script jars at runtime" wayfinder map
  (Cereal-Automation/desktop-client#150, tickets #154 and #155).
