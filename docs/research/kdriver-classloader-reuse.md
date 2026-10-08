# Can a script reuse the host's kdriver in obfuscated release builds?

Ticket: #48 (map #47). Follows up on §5 of `docs/research/ai-automation-transformation.md`
(branch `research/ai-automation-transformation`).

## Answer

**Partly, and not as something to rely on.** In a release build, a script can resolve
`dev.kdriver.*` from the host by its original names. Calls whose signatures use only kdriver, `kotlin.*`,
`kotlinx.coroutines.*` and JDK types will link. Any API that exposes `kotlinx.serialization.json.*`,
`kotlinx.io.files.Path` or `io.ktor.*` breaks, and so do the `inline reified` helpers such as
`Tab.evaluate<T>()`. ProGuard renames those dependency types in the host. Nothing in the code treats
kdriver as script-facing API, so a ProGuard-rule edit or a kdriver bump can break scripts without
anyone noticing.

Confidence: **high**. I checked this against a real `proguardReleaseJars` output and its mapping
file, not by reading the rules alone. Linkage failures at runtime are inferred from JVM linking rules.
I did not load a script against the release jar.

## How a script class reaches the host

- `ScriptClassLoader` is built with the no-arg `ClassLoader()`, so its parent is the system (app)
  class loader (`cereal-client/src/main/java/com/cereal/client/infrastructure/data/datasource/filesystem/reader/ScriptClassLoader.kt:7-11`).
- `com.cereal.sdk` and `kotlinx.coroutines` load **parent-first**. These are the only packages the
  host intends to share. The code comment says these packages "should not be obfuscated"
  (`ScriptClassLoader.kt:12-17,38-40`).
- Every other class loads **child-first**: the loader looks in the encrypted script JAR first, then
  falls back to the parent for anything not under `com.cereal` (`ScriptClassLoader.kt:41-53`). So
  `dev.kdriver.*` comes from the host only when the script JAR does not bundle it. In practice that
  means the script declares kdriver `compileOnly`. The sample script ships a thin jar with no bundled
  dependencies (`cereal-script-sample/cereal-script-sample.gradle.kts`).

## What ProGuard keeps in the release build

Release config: `cereal-client/cereal-client.gradle.kts:207-228`. It sets `obfuscate.set(true)` and
`joinOutputJars`, uses ProGuard 7.8.2, and lists the rule files in `cereal-client/proguard-rules/`.
The Compose plugin's built-in `default-compose-desktop-rules.pro` also applies (compose-gradle-plugin
1.10.1).

| Package | Rule | Names survive? |
|---|---|---|
| `dev.kdriver.**` (core + cdp) | `-keep class dev.kdriver.** { *; }` (`proguard-rules/kdriver.pro:7`) | yes, classes and members |
| `kotlin.**` | `-keep class kotlin.** { *; }` (Compose default rules) | yes |
| `kotlinx.coroutines.**` | `-keep class kotlinx.coroutines.** { *; }` (`cereal-client.pro`) | yes |
| `org.slf4j.**` | `logback.pro` | yes |
| `kotlinx.serialization.**` | only `$$serializer`/`Companion` members (`kotlinx-serialization.pro`) | **no**, renamed and repackaged to root |
| `kotlinx.io.**` | only volatile fields (`kotlinx-io.pro`) | **no** |
| `io.ktor.**` | volatile fields + engine containers (`ktor.pro`) | **no** |

The kdriver keep rule was added so CDP serializers resolve by reflection (comment in
`kdriver.pro:3-6`). It was not added to give scripts an API.

## Evidence from an actual release build

I ran `./gradlew :cereal-client:proguardReleaseJars` (default mock flavor). Output:
`cereal-client/build/compose/tmp/main-release/proguard/cereal-client-2.0.0.jar`, with the mapping
in `cereal-client/proguard.map`.

Mapping (`proguard.map`):

```
dev.kdriver.core.tab.Tab -> dev.kdriver.core.tab.Tab
kotlinx.coroutines.CoroutineScope -> kotlinx.coroutines.CoroutineScope
kotlinx.io.files.Path -> NW
kotlinx.serialization.json.JsonElement -> Rb
kotlinx.serialization.json.Json -> QT
kotlinx.serialization.SerializersKt__SerializersKt -> Os
io.ktor.client.HttpClient -> vc
```

`javap` on the release jar shows that kept kdriver members get their descriptors rewritten to the
obfuscated dependency names:

```
// dev.kdriver.core.browser.ExtensionsKt
createBrowser(CoroutineScope, NW, boolean, String, NW, List, ...)        // was kotlinx.io.files.Path -> NoSuchMethodError
createBrowser(CoroutineScope, Config, Continuation)                     // intact
createBrowser(CoroutineScope, Function1<ConfigBuilder,Unit>, Continuation) // intact
// dev.kdriver.core.tab.Tab
rawEvaluate(String, boolean, Continuation<? super Rb>)                   // erased descriptor links, but returns an `Rb`
get(String, boolean, boolean, Continuation<? super Tab>)                 // intact
select(String, long, Continuation<? super Element>)                     // intact
```

## What breaks for a script built against `dev.kdriver:core:0.5.11`

1. **Signatures that mention a renamed type** (`Path`, `JsonElement`, `HttpClient`): the descriptor
   the script compiled against no longer exists, so the call fails with `NoSuchMethodError`. Of
   roughly 1,100 public members in kdriver core, 38 take or return one of these types directly. That
   count leaves out serializer plumbing. The cdp module's `callCommand(String, JsonElement, ...)` and
   `Message.Event.getParams()` are affected too.
2. **Return values of renamed types**: `rawEvaluate` links because generics erase to `Object`. But
   the value is the host's `Rb`, so a script that treats it as `JsonElement` gets
   `NoClassDefFoundError`, or `ClassCastException` if the script bundles its own
   kotlinx-serialization (different class identity).
3. **Inline/reified helpers**: `dev.kdriver.core.tab.ExtensionsKt.evaluate<T>()` is inlined into the
   script and calls `kotlinx.serialization.SerializersKt.serializer(...)`,
   `Json.decodeFromJsonElement(...)` and `JsonElement` directly (bytecode of
   `core-jvm-0.5.11.jar`). None of these exist under those names in the release jar.
4. **Version coupling**: kdriver is 0.5.11, pre-1.0, pinned in `gradle/libs.versions.toml:36`. A host
   bump that changes kdriver's binary API breaks every script compiled against the old one. Nothing
   catches this: the release smoke gate (`cereal-client.gradle.kts:342-380`) boots the host and does
   not load scripts.
5. **Debug vs. release**: in debug or `run` builds nothing is renamed, so all of this works. A script
   tested locally can pass and then fail only in the shipped app.

## What would make it a contract (if wanted)

Reuse could become a real contract, but it is a product decision with costs:

- Keep the names of every type that appears in kdriver's public API, either with
  `-keep class kotlinx.serialization.json.**`, `kotlinx.io.files.**` and `io.ktor.client.HttpClient`,
  or more broadly with `-keepnames`. To also support `inline reified` helpers, keep
  `kotlinx.serialization.**` entirely. Each of these weakens obfuscation and adds size.
- Add `dev.kdriver` (and the kept dependency packages) to `parentClassloaderPackages`, so the host
  copy always wins. Otherwise a script that bundles kdriver gets a split class graph.
- Treat the kdriver version as part of the SDK's versioned surface, and add a release-jar check that
  links a sample script against it.

The cleaner alternative, which fits the existing SDK component pattern (ADR-0002 precedent, research
doc §1.3), is a narrow `provider.browser()` SDK component under `com.cereal.sdk`. That package is
already kept and loaded parent-first, and the host stays free to change or obfuscate kdriver
internally.

## Gaps

- I did not run a script against the release jar. The runtime errors above are inferred from the
  rewritten descriptors and standard JVM linkage. **[INFERENCE]**
- The build used the `mock` flavor. kdriver keep rules don't depend on flavor, but `prod` was not
  built.
