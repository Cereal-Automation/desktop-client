# Which types cross the script↔client boundary?

Research for [#151](https://github.com/Cereal-Automation/desktop-client/issues/151), part of the map
[#150 Isolate script jars at runtime](https://github.com/Cereal-Automation/desktop-client/issues/150).
Researched 2026-10-18.

## Answer

- **Only `kotlin-stdlib` (plus `com.cereal.sdk` itself) is in the SDK's public API.** The SDK has no runtime
  dependencies besides the stdlib. Its API dump contains no `kotlinx.*` type at all: no coroutines, no
  kotlinx-datetime, no serialization.
- **`kotlinx.coroutines` is an implicit boundary type.** It appears in no signature. But the client calls the script's
  `suspend` functions from its own coroutines, so the script's `delay`, `withContext` and `Job` lookups read the
  client's `CoroutineContext` elements. Those elements are keyed by `kotlinx.coroutines` classes, so script and client
  have to share one copy. That's why it's parent-first today.
- **`kotlin-reflect` is a host-side requirement, not a shared type.** The client reads script configuration classes
  with `kotlin.reflect.full`, which needs `kotlin.Metadata` and `KClass` from the same stdlib the script was linked
  against.
- **So the shared set must be at least `kotlin.*` (stdlib), `com.cereal.sdk.*`, and `kotlinx.coroutines.*`.** Every
  other overlapping library is something the script bundles for its own use, and it never crosses the API. Those
  libraries are the collision risk the map wants isolated, not candidates for sharing.
- **Real scripts overlap with the client on ktor, okhttp/okio, kotlinx-serialization, kotlinx-io, kotlinx-datetime,
  kdriver, slf4j, commons-logging, Apache httpcomponents and (older jars) logback**, usually at a different version
  (tables below).
- **The client's release ProGuard keeps `kotlin.**`, `kotlinx.coroutines.**` and `com.cereal.sdk.**` fully**, so the
  minimum shared set already survives obfuscation. Most of the other overlapping libraries (ktor, okhttp3,
  kotlinx-serialization, kotlinx-io, kotlinx-datetime) are renamed into the root package in release builds. A script
  that relied on falling back to the client's copy of those would work under `./gradlew run` and break in release.

## 1. Third-party types in the SDK public API

Source: `sdk@b12571a` (origin/HEAD, 2026-10-18), `cereal-sdk/api/cereal-sdk.api` (binary-compatibility-validator
dump, 1207 lines). I extracted every JVM type descriptor that isn't `java/*` or `com/cereal/*`:

| Type | Uses | Library | Where |
|---|---|---|---|
| `kotlin.jvm.functions.Function0/1/2` | 80 | kotlin-stdlib | builder lambdas, `statusUpdate: suspend (String) -> Unit` in `Script.execute` |
| `kotlin.coroutines.Continuation` | 31 | kotlin-stdlib | every `suspend fun` (`Script.onStart/execute/onFinish`, all `*Component` methods) |
| `kotlin.jvm.internal.DefaultConstructorMarker` | 22 | kotlin-stdlib | default-argument constructors |
| `kotlin.sequences.Sequence` | 5 | kotlin-stdlib | `ScriptConfigValue.SequenceScriptConfigValue` |
| `kotlin.enums.EnumEntries` | 5 | kotlin-stdlib | `getEntries()` on SDK enums |
| `kotlin.time.Duration` | 3 | kotlin-stdlib | `LlmException` retry-after |
| `kotlin.reflect.KClass` | 1 | kotlin-stdlib | `ScriptConfigurationItem.stateModifier` annotation parameter (`Configuration.kt:82`; annotation members aren't in the dump) |

`grep -c kotlinx cereal-sdk.api` → `0`. The non-`com.cereal` imports in `cereal-sdk/src/main` are only
`java.io.Closeable`, `java.io.IOException`, `java.time.Instant`, `kotlin.reflect.KClass` and `kotlin.time.Duration`.

`cereal-sdk/cereal-sdk.gradle.kts` declares no `implementation`/`api` dependencies. Coroutines, mockk and the compiler
are `testImplementation` only, so the published SDK depends on nothing but kotlin-stdlib (Kotlin 2.4.21,
`gradle/libs.versions.toml`).

### Implicit sharing outside the signatures

- **kotlinx.coroutines.** `Script` is a set of `suspend` functions the client drives. Inside them, script code reaches
  `coroutineContext[Job]`, `Delay` and so on, all keyed by `kotlinx.coroutines` classes. A script-private copy would
  not see the client's `Job` (so cancellation wouldn't reach it) or dispatcher. The client forces it parent-first
  (`ScriptClassLoader.parentClassloaderPackages = ["com.cereal.sdk", "kotlinx.coroutines"]`). The AI agent build says
  the same: "the host's coroutines link against its own" (`Script-AI-Browser-Agent@f4f7155:build.gradle.kts:22-23`).
- **kotlin-reflect / `kotlin.Metadata`.** `ScriptConfigurationDefinitionBuilder` and `FileSystemScriptsDataSource` use
  `kotlin.reflect.full.{findAnnotation, memberFunctions, memberProperties, isSubclassOf}` on script classes. That needs
  the script's `@kotlin.Metadata` to resolve to the client's stdlib class, which is another reason the stdlib must be
  shared. The AI agent restores unobfuscated config classes after ProGuard for exactly this reason
  (`build.gradle.kts`, `scriptJar` `doLast`).
- **The stdlib arrives by fallback, not by rule.** It isn't in `parentClassloaderPackages`. Scripts exclude it from the
  shadow jar, so `findClass` misses and `loadClass` falls through to the parent.

## 2. What real Script-* jars bundle that the client also ships

Client versions come from `./gradlew :cereal-client:dependencies --configuration runtimeClasspath` at `3b8caee`
(= origin/master). Script versions come from the same task run on a `git archive origin/HEAD` copy of each repo, or
from the version catalog where the build couldn't be resolved (marked *declared*). Everything below is what ends up in
the shadow jar (`runtimeClasspath` minus the excluded `kotlin-stdlib` and `cereal-sdk`).

Active repos only (not archived, pushed since 2025): Script-Private (= the old Script-BSTN, same history),
Script-Webshop-Monitor (same build as Script-Monitoring, which Private also pulls in as a submodule), Script-Template,
Script-AI-Browser-Agent, Script-Playground, Script-FootpatrolLaunchesRaffle. Script-TGTG-Monitor has no Gradle build
at origin/HEAD (it moved into Webshop-Monitor `script-tgtg`). Script-Toolkit has no reachable remote.

### Overlap matrix (version in the script jar; client version in the header)

| Library (client version) | Webshop-Monitor `d65e09b` (zalando / rental) | AI-Browser-Agent `f4f7155` | Template `b3f7e4c` | Private `e301ff1` (*declared*) | Footpatrol `bd66502` (*declared*) |
|---|---|---|---|---|---|
| kotlinx-coroutines-core (1.11.0) | **1.11.0, bundled** | excluded | – | 1.9.0 (catalog; likely raised to 1.11.0 by the monitoring submodule) | 1.7.3 |
| kotlinx-serialization-core/json (1.11.0) | 1.11.0 / **1.9.0** | 1.11.0 | **1.8.1** (via cereal-licensing 1.7.1) | **1.6.3** | – |
| kotlinx-datetime (0.7.1) | **0.8.0** | – | – | **0.6.1** | – |
| kotlinx-io-core/bytestring (0.9.1) | 0.9.1 / **0.8.2** | **0.8.2** | – | via ktor | – |
| ktor-client-core, -http, -io, -utils, -network, -events, -sse, -websockets, -serialization (3.6.0) | **3.5.2** / **3.4.0** | **3.4.0** | – | **3.2.3** | – |
| ktor-client-content-negotiation, -serialization-kotlinx(-json) (3.4.0 *) | **3.5.2** / 3.4.0 | 3.4.0 | – | 3.2.3 | – |
| ktor-client-apache, -cio, -network-tls (3.4.0) | – / 3.4.0 | 3.4.0 | – | via kdriver | – |
| okhttp (5.5.0) | **5.4.0** | – | – | via ktor-okhttp | **4.12.0** (retrofit 2.11, logging-interceptor 4.12) |
| okio (3.18.2) | **3.17.0** | – | – | via okhttp | via okhttp 4 |
| dev.kdriver core/cdp (0.6.1) | – / **0.5.11** | 0.6.1 | – | **vendored source** (`kdriver/` included build; ktor 3.4.0) | – |
| slf4j-api (2.0.19) | **2.0.18** / **2.0.17** | **2.0.17** | – | – | – |
| commons-logging (1.2) | **1.4.0** | 1.2 | – | – | – |
| httpclient / httpcore (4.5.13 / 4.4.15) | **4.5.14 / 4.4.16** | same | – | – | – |
| httpasyncclient, httpcore-nio, commons-codec, zstd-jni | same | same | – | – | – |
| kotlinx-coroutines-slf4j (1.11.0) | 1.11.0 | **1.10.2** | – | – | – |
| logback | – | – | – | – | bundled, names kept (`proguard-rules/logback.pro`) |
| javax.mail / com.sun.mail | – | – | – | 1.6.2 (client ships **jakarta.mail** 2.1.5, different package, no clash) | – |

\* The client mixes ktor versions itself: `ktor-client-okhttp` 3.6.0 pulls core 3.6.0, while
content-negotiation and serialization-kotlinx stay at 3.4.0 through kdriver 0.6.1.

Kotlin compiler versions the scripts are built with: Private 2.2.0, Playground 2.2.0, AI agent 2.3.21, Webshop and
Template 2.4.10. All are at or below the client's stdlib 2.4.21, so the stdlib fallback holds for today's jars. (The AI
agent's catalog comment "desktop-client ships 2.3.21" is out of date.)

SDK versions scripts compile against: AI agent 1.14.0 (= client), Webshop and Template 1.12.0, Private 1.7.3,
Playground and Footpatrol 1.6.x.

### What survives the script's own ProGuard

Every repo publishes the ProGuard-ed jar (`scriptJar`, `-repackageclasses ''`), not the raw shadow jar. A bundled
class keeps its original name only if a script rule keeps it. Everything else moves to the root package (`a`, `b`,
...). Classes keep their original FQN in:

| Repo | Bundled packages kept with original names |
|---|---|
| Template | `kotlinx.coroutines.**` (names; not actually bundled) |
| Webshop-Monitor | `kotlinx.coroutines.**`, `okio.**`, `dev.kdriver.cdp.**`, `dev.kdriver.core.**`, `io.ktor.network.**`, 4 commons-logging classes |
| AI-Browser-Agent | `kotlinx.serialization.**`, `dev.kdriver.core.**`, `org.apache.commons.logging.**`, ktor `HttpClientEngineContainer` impls |
| Private | `kotlinx.coroutines.**`, `io.ktor.{client,http,util,utils.io,serialization,network}.**`, `kotlinx.io.**`, `okio.**` (class only), `dev.kdriver.**`, `org.slf4j.**`, `org.apache.commons.logging.**`, `org.apache.log4j.**`, `javax.mail.**`, `com.sun.mail.**` |
| Footpatrol | `kotlinx.coroutines.**`, `ch.qos.logback.**` |

So the overlap that matters by name is mostly ktor, kotlinx-io, okio, kdriver, slf4j, commons-logging, logback and
(AI agent) kotlinx-serialization. Child-first loading already gives the script its own copy of those classes. What
still collides is what the map lists: resources and `ServiceLoader` lookups (ktor engine containers, slf4j providers,
logback config) resolve against the client, because `findResource` isn't overridden.

Bundled coroutines (Webshop, Private, Footpatrol) are dead weight: the names are kept, the client forces
`kotlinx.coroutines` parent-first, and the script links against the client's 1.11.0 whatever it was built with.

## 3. What the client's release ProGuard keeps unobfuscated

Release config: `cereal-client/cereal-client.gradle.kts` `buildTypes.release.proguard` (`obfuscate = true`,
`joinOutputJars = true`) plus `cereal-client/proguard-rules/*.pro`. The Compose plugin also always includes its own
`default-compose-desktop-rules.pro` (compose-gradle-plugin 1.12.2, `AbstractProguardTask.kt:141-147`). `cereal-client.pro`
sets `-repackageclasses ''`, so anything not kept gets renamed into the root package.

| Package | Release status | Rule |
|---|---|---|
| `kotlin.**` (stdlib, kotlin-reflect) | **kept** (names + members) | Compose default rules `-keep class kotlin.** { *; }` |
| `kotlinx.coroutines.**` | **kept** | `cereal-client.pro` |
| `com.cereal.sdk.**` | **kept** | `cereal-client.pro` |
| `okio.**` | **kept** | `cereal-client.pro` |
| `kotlinx.atomicfu.**` | **kept** | `cereal-client.pro` |
| `dev.kdriver.**` | **kept** | `kdriver.pro` |
| `ch.qos.**`, `org.slf4j.**` | **kept** | `logback.pro` |
| `jakarta.mail.**` (public), `jakarta.activation.**`, `org.eclipse.angus.mail.**` | **kept** | `jakarta.pro` |
| `com.sun.jna.**`, `androidx.sqlite.**` | **kept** | `jna.pro`, `androidx-sqlite.pro` |
| `org.apache.commons.logging` (`LogFactory`, `LogFactoryImpl`, `Jdk14Logger` only) | kept (3 classes) | `commons-logging.pro` |
| `androidx.compose.**` | names only (can still be shrunk) | `cereal-client.pro` `-keepnames` |
| `io.ktor.**` | **obfuscated** (only volatile fields and engine-container classes kept) | `ktor.pro` |
| `okhttp3.**` | **obfuscated** (only the `publicsuffix` package name kept) | `okhttp.pro` |
| `kotlinx.serialization.**` | **obfuscated** (only `$$serializer` / `Companion` members of `@Serializable` classes kept) | `kotlinx-serialization.pro` |
| `kotlinx.io.**` | **obfuscated** (volatile field names only) | `kotlinx-io.pro` |
| `kotlinx.datetime`, `kotlinx.collections.immutable`, koin, room, httpcomponents, everything else | **obfuscated** | no keep rule |

Of the minimum shared set (stdlib, SDK, coroutines), all three are already fully kept. The client's copies of
overlapping libraries a script bundles (ktor, okhttp3, kotlinx-serialization, kotlinx-io, kotlinx-datetime) aren't
reachable by name in release builds.

## Implications for #150

- Shared set (parent-first, one class identity): `kotlin.*`, `com.cereal.sdk.*`, `kotlinx.coroutines.*`. Nothing else
  appears in the API or the call contract. Making the stdlib an explicit parent-first rule would stop a script that
  bundles the stdlib from shadowing it (none do today, by exclusion).
- Everything else should resolve from the script jar only, including resources and services. The client's ProGuard
  rules need no new keeps for that set. They'd only need keeps if the spec chose to share more.
- Version skew on the shared set is real: Footpatrol was built against coroutines 1.7.3 and Private against 1.9.0,
  but both run on the client's 1.11.0. Build-time enforcement should stamp the coroutines and stdlib versions as
  well as `sdkVersion`.
- Collision test fixture candidates: ktor 3.4.0 vs 3.6.0 (`io.ktor.client.HttpClientEngineContainer` service), okhttp
  4.12 vs 5.5 (same `okhttp3` package, a major API break), and kotlinx-serialization 1.6.3 vs 1.11.0.

## Sources

- SDK: `Cereal-Automation/sdk@b12571a`: `cereal-sdk/api/cereal-sdk.api`, `cereal-sdk/cereal-sdk.gradle.kts`,
  `gradle/libs.versions.toml`, `cereal-sdk/src/main/java/com/cereal/sdk/{Script,Configuration}.kt`
- Client: `desktop-client@3b8caee`: `gradle/libs.versions.toml`, `cereal-client/cereal-client.gradle.kts`,
  `cereal-client/proguard-rules/*.pro`, `.../filesystem/reader/ScriptClassLoader.kt`; resolved `runtimeClasspath`
- Compose: `org.jetbrains.compose:compose-gradle-plugin:1.12.2`: `default-compose-desktop-rules.pro`,
  `AbstractProguardTask.kt`
- Scripts (origin/HEAD): `Script-Webshop-Monitor@d65e09b`, `Script-Monitoring@aa02c31`, `Script-Private@e301ff1`,
  `Script-Template@b3f7e4c`, `Script-AI-Browser-Agent@f4f7155` (cerealdevnull fork),
  `Script-Playground@d1a8916`, `Script-FootpatrolLaunchesRaffle@bd66502`: `build.gradle.kts`,
  `gradle/libs.versions.toml`, `proguard-rules/*.pro`; resolved `runtimeClasspath` for Webshop (`script-zalando`,
  `script-rental`, `script-rss`), Template and AI agent. Private couldn't be resolved (its Gradle wrapper is
  incompatible with the newer monitoring submodule build), so its versions are declared ones.
