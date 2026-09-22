# Third-party components

Everything this repository redistributes or links against, with its licence.
Notices that the licences require us to propagate are reproduced in
[`NOTICE`](NOTICE); this file is the complete inventory behind them.

**This file is maintained by hand.** Update it when a dependency is added,
removed, or changes licence — a Dependabot bump that only moves a version does
not need a change here.

Last verified against `runtimeClasspath`: 2026-09-03.

---

## Licences at a glance

| Licence | Count | Notes |
|---|---|---|
| Apache-2.0 | most of the tree | Kotlin, Compose Multiplatform, AndroidX, Ktor, OkHttp, Koin, … |
| MIT | 4 groups | SLF4J, Sentry, semver, kittinunf Result |
| BSD-2-Clause | 1 | zstd-jni |
| EPL-2.0 **or** LGPL-2.1-only | 1 | Logback — see [Copyleft and dual licences](#copyleft-and-dual-licences) |
| LGPL-2.1-or-later **or** Apache-2.0 | 1 | JNA — we elect Apache-2.0 |
| EPL-2.0 **or** GPL-2.0-with-Classpath-Exception **or** EDL-1.0 | 4 | Jakarta Mail / Angus Mail |
| SIL OFL-1.1 | 1 | Montserrat typeface |
| Proprietary | 1 | Cereal SDK (our own closed binary dependency) |

No dependency carries a licence that restricts distributing this client under
Apache-2.0.

---

## Copyleft and dual licences

These are the only entries that need a deliberate decision rather than a
mention. Everything else is permissive.

### Logback (`ch.qos.logback:logback-classic`, `logback-core`) — EPL-2.0 **or** LGPL-2.1-only

Dual-licensed; either may be chosen. Both are *file-level* copyleft: they reach
modifications to Logback's own source files, not code that merely depends on
Logback. We consume it as an unmodified binary dependency, so no obligation
propagates to this project's source. Replacing it would only become necessary
if we forked Logback itself.

### JNA (`net.java.dev.jna:jna`) — LGPL-2.1-or-later **or** Apache-2.0

Dual-licensed since JNA 4.0.0, with the choice left to the user. **We elect the
Apache License, Version 2.0**, which removes the LGPL from consideration
entirely. Recorded explicitly because an automated scanner reading only the
first `<license>` element in JNA's POM will report this project as containing
LGPL code, and that reading is wrong.

### Jakarta Mail / Angus Mail — EPL-2.0 **or** GPL-2.0-with-Classpath-Exception **or** EDL-1.0

`jakarta.mail:jakarta.mail-api`, `org.eclipse.angus:angus-mail`,
`org.eclipse.angus:angus-activation`, `jakarta.activation:jakarta.activation-api`.

Triple-licensed. The GPL-2.0 option carries the Classpath Exception, which
exempts code that links against these libraries — so even that option would not
reach this project. EPL-2.0 or EDL-1.0 are available regardless.

### Bundled Java runtime

Release distributables are packaged with `jpackage`, which embeds a trimmed Java
runtime built from the JDK used at build time. That runtime is licensed
separately from this project — for an OpenJDK build, GPL-2.0 with the Classpath
Exception. It is not part of this repository and does not affect the licensing
of the source here, but it does travel inside the installers we publish.

---

## Committed native binaries

Ten native libraries are checked into this repository. Building them is not
possible on a single CI runner (each target needs its own toolchain), so they
are committed as binaries. Their provenance is documented below to the extent
it can be established from the artifacts themselves; rebuilding them from source
in CI is tracked separately.

### Discord RPC — third-party, MIT

Upstream: [discord/discord-rpc](https://github.com/discord/discord-rpc).
Loaded through JNA (`DiscordRPC.INSTANCE`). All five vendor RapidJSON 1.1.0
statically — see `NOTICE`.

| File | Target | Provenance |
|---|---|---|
| `cereal-client/src/main/resources/linux-x86-64/libdiscord-rpc.so` | Linux x86-64 | Built on Discord's own Buildkite CI (build paths embedded in the binary), consistent with the official `discord-rpc-linux` release archive. Latest upstream release is v3.4.0 (2018). |
| `cereal-client/src/main/resources/win32-x86-64/discord-rpc.dll` | Windows x86-64 | Official Discord release archive. MSVC strips build paths, so this cannot be confirmed from the binary. |
| `cereal-client/src/main/resources/win32-x86/discord-rpc.dll` | Windows x86 (32-bit) | As above. |
| `cereal-client/resources/macos-x64/libdiscord-rpc.dylib` | macOS x86-64 | Built from a working tree at `/Users/adam/git/discord-rpc`, macOS SDK 11.1, minimum macOS 10.9. |
| `cereal-client/resources/macos-arm64/libdiscord-rpc.dylib` | macOS arm64 | Same build tree, macOS SDK 11.1, minimum macOS 11.0. |

**Known gap:** no exact upstream version or commit is recorded for any of these,
and none can be recovered from the binaries. The macOS pair post-dates upstream
v3.4.0 (Apple Silicon did not exist when v3.4.0 shipped), so they are a rebuild
from source by a third party rather than an official release artifact. Treat the
version as indeterminate until they are rebuilt from a pinned commit.

### Sekret — first-party build output

| File | Target |
|---|---|
| `cereal-client/resources/linux-x64/libsekret.so` | Linux x86-64 |
| `cereal-client/resources/linux-arm64/libsekret.so` | Linux arm64 |
| `cereal-client/resources/macos-x64/libsekret.dylib` | macOS x86-64 |
| `cereal-client/resources/macos-arm64/libsekret.dylib` | macOS arm64 |
| `cereal-client/resources/windows/sekret.dll` | Windows x86-64 |

**These are not third-party redistributions.** They are Kotlin/Native
compilations of this repository's own `cereal-client:sekret` subproject, whose
source is in-tree at `cereal-client/sekret/src/`. Their exported JNI symbols
(`Java_com_cereal_client_Sekret__1databaseEncryptionKeyEncrypted` and siblings)
match that source. They are listed here because an auditor is entitled to know
what every committed binary is, not because attribution is owed.

They are produced by the `dev.datlag.sekret` Gradle plugin (Apache-2.0) and
statically link the Kotlin/Native runtime (Apache-2.0). They were last rebuilt
in commit `f1329d42a` ("Set JDK version to 17 and recompiled Sekret binaries");
the plugin version in use at that time is not recorded, and the current pin is
`dev.datlag.sekret` 2.3.0.

---

## Bundled assets

| Asset | Source | Licence |
|---|---|---|
| `Montserrat-{Regular,Medium,SemiBold,Bold}.ttf` v9.000 | [The Montserrat Project](https://github.com/JulietaUla/Montserrat) | SIL OFL-1.1 ("Montserrat" is a Reserved Font Name) |
| `ic_material_logout.svg`, `ic_cancel.svg` | Google Material Symbols | Apache-2.0 |
| `ic_discord.svg` | Discord logo | Trademark of Discord, Inc. — used nominatively, see `NOTICE` |
| `ic_google.svg` | Google "G" logo | Trademark of Google LLC — used nominatively, see `NOTICE` |
| `ic_plus`, `ic_stop`, `ic_play`, `ic_edit`, `ic_overview`, `ic_activity_indicator`, `application` | This project | Apache-2.0 |

---

## Runtime dependencies

Grouped by licence. Versions are resolved by the version catalog at
[`gradle/libs.versions.toml`](gradle/libs.versions.toml); transitive
dependencies are included where they reach the runtime classpath.

### Apache-2.0

| Group | What it is |
|---|---|
| `org.jetbrains.kotlin` | Kotlin standard library and reflection |
| `org.jetbrains.kotlinx` | Coroutines, serialization, immutable collections |
| `org.jetbrains.compose.*` | Compose Multiplatform (runtime, UI, foundation, material, material3, desktop, components) |
| `org.jetbrains.skiko` | Skiko — the Skia binding Compose renders through. Bundles Skia itself (BSD-3-Clause, Google LLC) as a native library inside the jar. |
| `org.jetbrains.androidx.*`, `androidx.*` | Room, SQLite, Lifecycle, SavedState, Collection, Annotation, Arch Core, NavigationEvent |
| `org.jetbrains` | `annotations`, `markdown` |
| `org.jetbrains.runtime` | `jbr-api` (JetBrains Runtime API stubs) |
| `io.insert-koin` | Koin dependency injection |
| `com.squareup.okhttp3`, `com.squareup.okio` | HTTP client and I/O |
| `io.ktor` | Ktor client (used by kdriver and the SDK) |
| `dev.kdriver` | Chrome DevTools Protocol client |
| `media.kamel` | Async image loading for Compose |
| `io.github.reactivecircus.cache4k` | In-memory cache |
| `com.mikepenz` | Multiplatform Markdown renderer |
| `dev.datlag.sekret` | Secret obfuscation runtime and annotations |
| `com.github.doyaaaaaken` | `kotlin-csv-jvm` |
| `co.touchlab` | Stately concurrency primitives |
| `org.apache.httpcomponents`, `commons-codec`, `commons-logging` | Apache HttpComponents stack |
| `org.jspecify` | Nullness annotations |

### MIT

| Group | What it is |
|---|---|
| `org.slf4j` | `slf4j-api` — logging facade |
| `io.sentry` | `sentry`, `sentry-kotlin-multiplatform` — crash reporting |
| `net.swiftzer.semver` | Semantic version parsing |
| `com.github.kittinunf.result` | `Result` / `SuspendableResult` |

### BSD-2-Clause

| Group | What it is |
|---|---|
| `com.github.luben` | `zstd-jni` — Zstandard compression (transitive) |

### Copyleft or dual-licensed

See [Copyleft and dual licences](#copyleft-and-dual-licences) above.

| Group | Licence |
|---|---|
| `ch.qos.logback` | EPL-2.0 or LGPL-2.1-only |
| `net.java.dev.jna` | LGPL-2.1-or-later or Apache-2.0 (we elect Apache-2.0) |
| `jakarta.mail`, `jakarta.activation` | EPL-2.0 or GPL-2.0-with-CPE or EDL-1.0 |
| `org.eclipse.angus` | EPL-2.0 or GPL-2.0-with-CPE or EDL-1.0 |

### Proprietary

| Group | What it is |
|---|---|
| `com.cereal-automation` | The Cereal SDK — a closed-source binary dependency of this project, published to a public Maven repository so this client and third-party scripts can both build against it. Not covered by this repository's licence. |
