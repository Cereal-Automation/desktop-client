# Native libraries on the headless path: Linux ARM64 (aarch64)

Research for #11 (map #8). Question: which native libraries does the client load on the headless
path (Skiko/Compose UI excluded), does a Linux ARM64 build exist or can one be produced, and what can a
headless distribution drop?

**Short answer:** every native library the headless path *requires* already has a Linux aarch64 build.
Sekret's is committed to the repo and the others ship inside their Maven jars. The one library without
an aarch64 build is Discord RPC. It is optional and the code already degrades to a no-op without it, so a
headless distribution drops it.

## Method

- Read the boot path: `presentation/main.kt` → `App.initialize()` (Sekret, Sentry, Koin, cert pinning),
  `DatabaseConnector.kt` (Room), and every `System.load*` / JNA `Native.load*` call site in
  `cereal-client/src/main`.
- Resolved `:cereal-client` `runtimeClasspath` (131 files, via a throwaway Gradle init script printing
  `configurations.runtimeClasspath.files`) and listed every `.so/.dylib/.dll/.jnilib/.a` inside each
  jar with `unzip -Z1`.
- Ran `file` on the binaries, and `strings | grep GLIBC_` for the minimum glibc.
- Checked upstream only where the repo and jars don't settle it (Discord RPC, Chrome).

## Inventory

| # | Library | Loaded by | Required headless? | Linux aarch64 build | Evidence |
|---|---------|-----------|--------------------|---------------------|----------|
| 1 | `libsekret.so` (Sekret secrets, Kotlin/Native) | `App.initialize()` → `dev.datlag.sekret.NativeLoader.loadLibrary("sekret", <compose.application.resources.dir>)`; throws `"Unable to load secrets."` on failure (skipped in the `mock` flavor) | **Yes**, hard failure at boot | **Yes, committed:** `cereal-client/resources/linux-arm64/libsekret.so` (`ELF 64-bit LSB shared object, ARM aarch64`, needs only libc/libm/libdl/libpthread/libgcc_s, GLIBC ≤ 2.17) | `cereal-client/sekret/build.gradle.kts` declares `linuxArm64 {}` next to `linuxX64`/`macos*`/`mingwX64`, so it is rebuilt by `generateSekret`/`nativeCopy` into `resources/linux-arm64/` |
| 2 | `libsqliteJni.so` (`androidx.sqlite:sqlite-bundled-jvm:2.7.0`) | Room via `BundledSQLiteDriver()` in `DatabaseConnector.buildRoomDatabase` | **Yes**, needed for the DB | **Yes, in the jar:** `natives/linux_arm64/libsqliteJni.so` (aarch64, GLIBC ≤ 2.17). `NativeLibraryLoader` reads `os.arch` and maps `aarch64` → `arm64` | Jar listing. Jar also has `linux_x64`, `osx_arm64`, `windows_x64` (no `osx_x64`, not relevant here) |
| 3 | `libjnidispatch.so` (`net.java.dev.jna:jna:5.19.1`) | Needed by any JNA use: Discord RPC (below), `MacFileSwap` (`Native.load("c")`, macOS-only self-update) | Only if something JNA-based runs. On headless Linux nothing requires it once Discord is dropped | **Yes, in the jar:** `com/sun/jna/linux-aarch64/libjnidispatch.so` | Jar listing |
| 4 | `libzstd-jni-1.5.7-4.so` (`com.github.luben:zstd-jni`) | Pulled in transitively by `dev.kdriver:core-jvm:0.5.11` (used in `dev/kdriver/core/network/Extensions*`) | Only if browser automation (kdriver) runs headless | **Yes, in the jar:** `linux/aarch64/libzstd-jni-1.5.7-4.so` | `dependencyInsight --dependency zstd-jni` → `dev.kdriver:core-jvm` |
| 5 | `libdiscord-rpc.so` (Discord RPC via JNA) | `DiscordRPC.INSTANCE = Native.loadLibrary("discord-rpc", …)`, reached from `DiscordRpcDataSource.create()` | **No.** `create()` catches `Error` and returns a no-op data source ("Discord support will be disabled") | **No.** Only `src/main/resources/linux-x86-64/libdiscord-rpc.so` (x86-64), `win32-x86*` DLLs, and macOS dylibs exist. Upstream [discord/discord-rpc](https://github.com/discord/discord-rpc) is deprecated in favour of the GameSDK and publishes no aarch64 build. It is plain C++/CMake, so an aarch64 `.so` *could* be built from source, but it isn't worth it for headless | Repo tree; upstream README |

Checked and found **no native code** on the runtime classpath for: OkHttp 5.4.0 (TLS through the JDK),
Ktor OkHttp engine, Sentry 8.41.0 / sentry-kotlin-multiplatform 0.27.0, Koin, kotlinx-serialization,
Logback/SLF4J. No Netty native transport, Conscrypt, or BouncyCastle jar is on `runtimeClasspath`.

### External binary (not a JNI lib, but a native dependency)

- **Chrome/Chromium for kdriver.** kdriver looks up `/usr/bin/google-chrome*`, `/opt/google/chrome/chrome`,
  `/usr/bin/chromium*`, `/usr/bin/brave*`, `/usr/bin/microsoft-edge*` (strings in `core-jvm-0.5.11.jar`),
  and the app maps `NoBrowserExecutablePathException` → `ChromeNotInstalledException`
  (`presentation/tasks/UserInteractionWindow.kt`). Distro Chromium packages exist on aarch64. Google
  announced official Chrome for ARM64 Linux for Q2 2026
  ([Google blog](https://blog.google/chromium/bringing-chrome-to-arm64-linux-devices/)). Today the
  only caller sits in a presentation-layer window, so this matters only if headless mode keeps browser
  automation.

## Loader mechanics that matter for a headless build

- Sekret and Discord both resolve their library from the system property
  `compose.application.resources.dir`. The Compose launcher sets it to the per-platform
  `appResourcesRootDir` folder (`cereal-client/resources/<os-arch>/`, where Compose names aarch64 Linux
  `linux-arm64`). A headless launcher that isn't a Compose `createDistributable` output has to set this
  property itself, or put `libsekret.so` on `java.library.path`, because `NativeLoader` falls back to
  `System.loadLibrary`.
- sqlite-bundled, JNA and zstd-jni extract their own binaries from the jar to a temp dir. They need a
  writable, non-`noexec` tmp (`java.io.tmpdir`), which is worth knowing for locked-down containers.
- All the aarch64 binaries checked need glibc ≥ 2.17 and none are musl builds, so Alpine needs
  `gcompat` or a glibc base image.

## What a headless distribution can drop

- **Skiko / Compose UI runtime:** out of scope for this ticket, but it is the largest native payload.
  Note that `compose.desktop.currentOs` makes the classpath host-dependent: the resolved classpath on
  this macOS arm64 host even contains `skiko-awt-runtime-linux-x64`.
- **Discord RPC:** all `libdiscord-rpc.*` files plus the `discord/*` JNA bindings. The no-op path
  already exists.
- **JNA:** only if Discord is dropped and `MacFileSwap` (macOS self-update) isn't on the Linux path.
- **zstd-jni / kdriver:** only if headless mode drops browser automation.
- **Non-Linux-arm64 natives:** `resources/{linux-x64,macos-*,windows}`, `src/main/resources/win32-*` and
  `linux-x86-64`, plus other-arch entries inside the jars (JNA ships ~25 platforms). Trimming these saves
  size only, not correctness.

**Must keep:** `resources/linux-arm64/libsekret.so` and `sqlite-bundled-jvm` (with its `linux_arm64` entry).

## Open questions surfaced

1. Is kdriver/Chrome in scope for headless mode? If so, the distribution needs a documented Chromium
   dependency on aarch64.
2. How will the headless ARM64 artifact be built? `compose.desktop.currentOs` and `appResourcesRootDir`
   pick natives for the *build host*, so this needs an aarch64 CI runner or an explicit target
   override.
3. Does the headless launcher set `compose.application.resources.dir`, or does it rely on
   `java.library.path`? This decides where `libsekret.so` goes.
4. Should the target include musl (Alpine)? None of the natives are built for musl.
