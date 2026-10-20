# Which client libraries do published script jars load from the client?

Research for [#180](https://github.com/Cereal-Automation/desktop-client/issues/180), part of the
"Isolate script jars at runtime" map ([#150](https://github.com/Cereal-Automation/desktop-client/issues/150)).
The question: under the filtering parent from [ADR 0010](../adr/0010-script-jars-isolated-by-filtering-loader.md)
(platform loader plus the Shared API `kotlin.`, `kotlinx.coroutines.`, `com.cereal.sdk.`), which classes
and resources that script jars get from the client classpath today will stop resolving?

## Answer

**One class dependency breaks, and it is probably latent.** Script-Monitoring's `script-rss` (built from
the same sources in Script-Webshop-Monitor) references `org.slf4j` 206 times without bundling it. The
references come from htmlunit's shaded Jetty classes, and the client provides `org.slf4j` unobfuscated
today. `script-rss` never calls htmlunit itself, so this only fails if those Jetty classes are ever loaded.
Every other shipped jar is closed over everything outside the Shared API. Each one bundles its own okhttp,
okio, ktor, kotlinx-serialization, kotlinx-io, kotlinx-datetime, kdriver, slf4j and so on. The only other
references they leave unresolved are optional dependencies the client doesn't have either (conscrypt,
BouncyCastle, OpenJSSE, Android, `javax.annotation`, apiguardian, Xerces, servlet, groovy/JMS for
logback, log4j, re2j). So for those the filter changes nothing.

**Nothing comes in transitively through `cereal-sdk`.** Its published POMs declare no dependencies from 1.1.0
through 1.7.3, and only `kotlin-stdlib` from 1.8.0 through 1.14.0. The SDK build at `origin/HEAD` (1.15.0)
has no runtime dependencies at all. The only `compileOnly` dependencies any script has are `cereal-sdk`
and `kotlin-stdlib`, and both are Shared API.

**Resources are where behaviour actually changes, and the change mostly fixes things.** `ScriptClassLoader`
doesn't override `findResource`, so today every `getResource` call and every `ServiceLoader` lookup from
script code resolves against the client classpath, never against the jar. The jars carry resources that
their own code expects to find:

- `Script-AI-Browser-Agent` reads `/consent-glue.js` and `/autoconsent/*` with
  `Browser::class.java.getResource(name)!!` (`Browser.kt:684`). In the real client that lookup returns
  null today. Its `ScriptJarTest` passes only because its host-like loader delegates resources to the
  test classpath.
- The okhttp 4 jars (Script-Sample, Script-Zalando, Script-EQLRaffleMonitor, Script-EndStockScrapper,
  Script-FootpatrolLaunchesRaffle, Private's `script-bstn`/`script-end-stockscrapper`/`script-zalando-scrapper`)
  ship `okhttp3/internal/publicsuffix/publicsuffixes.gz`. Today that lookup goes to the client, and the
  client's okhttp 5 names the file differently.
- Jars built with service files (Monitoring, Private, AI-Browser-Agent) ship `META-INF/services` for
  `io.ktor.client.HttpClientEngineContainer`, ktor serialization extensions, `javax.mail.Provider`,
  htmlunit Jetty and kotlin-reflect. Today they see the client's service files instead. Every script
  names its ktor engine explicitly (`HttpClient(OkHttp)`, or kdriver's own engine), so none depends
  on engine discovery.

Under [#157](https://github.com/Cereal-Automation/desktop-client/issues/157) (jar first, then platform,
never the client) these lookups find the jar's own copies. The one service a script could have relied
on the client for is the **SLF4J provider** (the client's logback). Jars that bundle `slf4j-api` but no
binding fall back to SLF4J's no-op logger. That means silent logs, not a crash, and scripts should be
logging through `LoggerComponent` anyway ([#154](https://github.com/Cereal-Automation/desktop-client/issues/154)).
I didn't verify this at runtime.

**There is one Shared API precedence change.** Today `ScriptClassLoader` loads `kotlin.*` child-first,
because only `com.cereal.sdk` and `kotlinx.coroutines` are parent-first. Jars that bundle classes under
`kotlin/reflect/jvm` (the published TGTG 1.2.4, and Private's `bolcom`, `dior-monitor` and
`eql-raffle-monitor`) use their own copy today. Under the filter they
get the client's kotlin-reflect 2.4.21, as ADR 0010 intends.

**Why the list is so short.** Release builds of the client run ProGuard with `-repackageclasses ''`
(`cereal-client/cereal-client.gradle.kts:208`, `proguard-rules/cereal-client.pro`). Only kept packages
keep their names: `com.cereal.sdk`, `kotlinx.coroutines`, `dev.kdriver`, `okio`, `org.slf4j`, `ch.qos`,
`kotlinx.atomicfu`, `jakarta.mail`/`org.eclipse.angus`, `com.sun.jna`, `androidx.sqlite`, and ktor's
`HttpClientEngineContainer` implementations. okhttp3, most of ktor, kotlinx-serialization, kotlinx-io and
kotlinx-datetime are renamed in production, so a published jar could never have relied on the client's
copies of those. That leaves okio, kdriver and slf4j as the realistic leaks, and the jars bundle all
three except for the `script-rss` slf4j case above.

## Method

1. Ran `git fetch` in every repo and read `origin/HEAD` (`git archive origin/HEAD`), never the working tree.
2. Read each build's dependency declarations, runtime exclusions and shadow and ProGuard configuration.
3. Rebuilt every script's shipped artifact from `origin/HEAD` (`scriptJar`, which is shadow plus ProGuard,
   the form that gets uploaded). For Script-Sample I used `shadowJar` on JDK 17, because its Gradle 7.6
   build doesn't run on JDK 21. Script-Private builds against its `script-monitoring` submodule at the
   pinned commit `aa02c31`, using Gradle 9.6.1, since its 8.11.1 wrapper can't configure that submodule.
4. Downloaded the two published binaries that exist as GitHub release assets: Script-TGTG-Monitor
   1.2.4 `release.jar` and Script-Playground 1.0.5 `release-1.0.5.jar`.
5. Ran `jdeps --multi-release 21 -verbose:class -filter:none` on each jar on its own and kept the
   referenced classes it reports as `not found`, minus the Shared API. These are exactly the lookups that
   fall through to the parent today. Matches like `aCv$aCw` are dangling ProGuard inner-class
   references with no package, so I ignored them.
6. Compared the results with the client's resolved `:cereal-client:runtimeClasspath`
   (`gradle/libs.versions.toml` plus `./gradlew :cereal-client:dependencies`) and its ProGuard keep rules.

## Per-script findings

"Client-only" means a package outside the Shared API that the jar references, doesn't bundle, and the
client provides. **That is the breakage set.** "Unbundled, not in client" means optional references that
fail the same way today and after the change.

| Repo / module (origin/HEAD) | Manifest `sdk_version` | cereal-sdk it compiles against | How the SDK and stdlib are left out | Client-only (breaks) | Unbundled, not in client either |
|---|---|---|---|---|---|
| Script-AI-Browser-Agent (`74c80ec`) | `1.14.0` (from `@cereal-sdk-version@`) | 1.14.0 | runtimeClasspath excludes stdlib, sdk, coroutines | none | commons-logging optionals (log4j, avalon, servlet) |
| Script-Template (`b3f7e4c`) | `1.12.0` (from `@cereal-sdk-version@`) | 1.12.0 | runtimeClasspath excludes stdlib, sdk | none | none |
| Script-Playground (`d1a8916`, published 1.0.5) | absent | 1.6.1 (`compileOnly`, `all`) | sdk and stdlib are `compileOnly` | none | none |
| Script-Sample (`5a4b770`) | absent | 1.1.0 (`all`) | shadow excludes `cereal-sdk`, `kotlin/**`, `META-INF/**` | none | `javax.annotation`, conscrypt, BouncyCastle, OpenJSSE, Android |
| Script-Zalando (`a32d133`) | absent | 1.6.0 (`all`) | same as Sample | none | same as Sample |
| Script-EQLRaffleMonitor (`939e184`) | absent | 1.6.0 (`all`) | same as Sample | none | same as Sample |
| Script-EndStockScrapper (`f14c275`) | absent | 1.6.0 (`all`) | same as Sample | none | same as Sample |
| Script-FootpatrolLaunchesRaffle `footpatrol`, `size` (`bd66502`) | absent | 1.6.0 (`all`) | same as Sample | none | logback optionals (groovy, JMS, servlet, janino, `javax.mail`), joda-convert, Guice ASM, okhttp optionals |
| Script-Monitoring `script-tgtg` (`aa02c31`; published as Script-TGTG-Monitor 1.2.4) | absent | 1.12.0 (`compileOnly`) | runtimeClasspath excludes stdlib, sdk | none | conscrypt, BouncyCastle, OpenJSSE, re2j, apiguardian, Xerces, servlet |
| Script-Monitoring `script-bdga-store`, `script-nike`, `script-snkrs`, `script-zalando`, `script-rental` | absent | 1.12.0 (`compileOnly`) | same as tgtg | none | same as tgtg |
| **Script-Monitoring `script-rss`** | absent | 1.12.0 (`compileOnly`) | same as tgtg | **`org.slf4j` (206 refs, from htmlunit's Jetty)** | same as tgtg |
| Script-Webshop-Monitor (`d65e09b`), all modules | absent | 1.12.0 | identical build scripts and version catalog to Script-Monitoring (it's a fork), so same result, including `script-rss` | `org.slf4j` in `script-rss` | as Monitoring |
| Script-Private `script-bolcom`, `-bstn`, `-dior-monitor`, `-end-stockscrapper`, `-eql-raffle-monitor`, `-zalando-scrapper` (`e301ff1`) | absent | 1.7.3 (`compileOnly`, `all`) | runtimeClasspath excludes stdlib, sdk | none | conscrypt, BouncyCastle, OpenJSSE, apiguardian, Xerces, servlet, `javax.annotation`, Android |
| Script-Private `script-epicgames-claimer`, `-flamedeer-hunter`, `-queueboost` | absent | 1.7.3 (`compileOnly`, `all`) | shadow excludes `cereal-sdk`, `kotlin/**`, `META-INF/**` | none | same, plus log4j for `queueboost` |

What the scripts import outside the Shared API, and where it comes from: okhttp3 and okio (bundled
everywhere they're used), `org.json`, jsoup, conscrypt, retrofit/gson, 2captcha, hawk, susstatemachine
(Footpatrol), ktor 3.x, kotlinx-serialization, kotlinx-datetime, htmlunit, rssparser and kdriver
(Monitoring/Webshop/Private/AI agent), `javax.mail` and AWS SDK (Private), kotlinx-io (AI agent). All of
these are `implementation` dependencies and end up in the jar.

### `sdk_version`

Only Script-AI-Browser-Agent (`1.14.0`) and Script-Template (`1.12.0`) put `sdk_version` in the manifest.
Both substitute `@cereal-sdk-version@` at `processResources`. Every other manifest at `origin/HEAD`, and
both published binaries, have no `sdk_version`. The client reads that as "no requirement"
(`ScriptPackageExtensions.kt`, `isClientUpdateRequired`). For those jars, the SDK version in the table is
the one they compile against.

## What the client classpath provides (overlap with scripts)

From the resolved `:cereal-client:runtimeClasspath` and `gradle/libs.versions.toml`: okhttp 5.5.0
(+ logging-interceptor), okio 3.18.2, ktor 3.6.0 (client core/okhttp, plus cio/apache/content-negotiation
3.4.0 via kdriver), kotlinx-serialization 1.11.0, kotlinx-io, kotlinx-datetime, kotlinx-coroutines-slf4j,
atomicfu, kdriver 0.6.1, slf4j-api, logback 1.6.5, kotlin-reflect 2.4.21, jakarta.mail/angus-mail,
`org.jetbrains:annotations`. None of the optional packages flagged above (conscrypt, BouncyCastle,
OpenJSSE, re2j, apiguardian, jsr305, Xerces, servlet, log4j, groovy) are on it. Scripts use `javax.mail`
and the client ships `jakarta.mail`, so those don't overlap either.

JDK modules the jars use (`jdeps -s`): `java.base`, `java.logging`, `java.desktop`, `java.xml`,
`java.naming`, `java.security.jgss`, `java.management`, `java.sql`, `java.datatransfer`,
`java.security.sasl`, `jdk.unsupported`. They come from the platform loader both today and after the
change. The packaged runtime has to keep including them (`nativeDistributions.modules`,
`cereal-client.gradle.kts:240`).

## Coverage and caveats

- **Script-Toolkit**: the local repo has no commits, and `Cereal-Automation/Script-Toolkit` doesn't exist
  on GitHub. Nothing to survey.
- **Script-TGTG-Monitor** contains only a release workflow. It builds `script-tgtg` from Script-Monitoring
  through `reusable-script-release.yml@master`.
- **Script-BSTN**'s remote redirects to Script-Private (same `origin/HEAD`, `e301ff1`). I surveyed it as
  Script-Private.
- **Rebuilt is not the same as published.** Apart from TGTG 1.2.4 and Playground 1.0.5, the GitHub
  releases have no jar assets. The marketplace binaries may come from older commits. The older repos
  (Sample, Zalando, EQL, End, Footpatrol) haven't changed since 2024 or 2025, so the rebuild should match.
- Static analysis covers bytecode references and the resources inside each jar. It doesn't cover
  class names built at runtime. A grep for `Class.forName`, `ServiceLoader`, `loadClass` and
  `contextClassLoader` across all script sources found no such use outside bundled libraries.
- Org repos with no local checkout (Script-Funda-Pararius-Monitor, Script-SneakersnstuffStockScrapper,
  Script-OutlookGenerator, Script-SoleboxStockScrapper, Script-CerealDiscordBot,
  Script-McDonaldsRewardsMonitor, Script-LouisVuittonMonitor, Script-BSTNRaffle, Script-ZalandoEarlyLink,
  Script-KithCheckpointMonitor, Script-NikeImageMonitor) weren't in scope.

## Sources

- `cereal-client/src/main/java/com/cereal/client/infrastructure/data/datasource/filesystem/reader/ScriptClassLoader.kt`
  (origin/master): child-first, `com.cereal.sdk`/`kotlinx.coroutines` parent-first, no resource override.
- `cereal-client/cereal-client.gradle.kts` and `cereal-client/proguard-rules/*.pro` (origin/master):
  dependencies, release ProGuard and keep rules, jlink modules.
- cereal-sdk POMs at `https://maven.cereal-automation.com/releases/com/cereal-automation/cereal-sdk/<v>/`
  for 1.1.0, 1.6.0, 1.6.1, 1.7.3, 1.8.0, 1.10.0 to 1.14.0, plus `sdk/cereal-sdk/cereal-sdk.gradle.kts` at
  `origin/HEAD` (`b12571a`).
- cereal-licensing POMs 1.1.0/1.6.0 (okhttp 4.12.0, moshi) and 1.6.1/1.7.1 (kotlinx-serialization). All
  of these get bundled.
- Each Script-* repo's `build.gradle.kts`, `gradle/libs.versions.toml`, `src/main/resources/manifest.json`
  at the `origin/HEAD` commits listed in the table.
- GitHub release assets: `Cereal-Automation/Script-TGTG-Monitor` 1.2.4 and `Cereal-Automation/Script-Playground` 1.0.5.
- [JDK 21 `jdeps`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jdeps.html).
