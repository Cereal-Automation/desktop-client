# Self-installing Linux update — implementation plan

Status: **Option A (AppImage) implemented** (client + CI). The CI/packaging path still needs
validation on a real runner (FUSE, app-image launcher name, native-lib bundling) — see
"Implementation status" below.

## Implementation status

Client (unit-tested, `:cereal-client:test` + `koverVerify` green):
- `UpdateInstallResult` enum + `SystemRepository.installUpdate(installer)`
  ([UpdateInstallResult.kt](../src/main/java/com/cereal/client/domain/model/app/UpdateInstallResult.kt),
  [SystemRepository.kt](../src/main/java/com/cereal/client/domain/repository/SystemRepository.kt)).
- `AppImageInstaller` datasource — resolves `$APPIMAGE`, stages the download in the target's
  directory and does a single atomic rename (avoids `ETXTBSY` + cross-fs issues), then relaunches
  ([AppImageInstaller.kt](../src/main/java/com/cereal/client/infrastructure/data/datasource/os/AppImageInstaller.kt)).
- `SystemRepositoryImpl.installUpdate` — Linux/AppImage self-replace+relaunch, reveal-folder
  fallback; macOS/Windows delegate to `open()`
  ([SystemRepositoryImpl.kt](../src/main/java/com/cereal/client/infrastructure/data/repository/SystemRepositoryImpl.kt)).
- `InstallUpdateInteractor`; DI wiring; `BootstrapViewModel.installUpdate` (exit via `shouldExitApp`)
  and `ApplicationSettingsViewModel.openInstallerFile` (exit on `Relaunching`).

CI/packaging (needs a real run to validate):
- AppDir assets `cereal-client/appimage/{AppRun,cereal.desktop}` + `icon-linux.png`.
- `build-appimage` composite action: `createReleaseDistributable` → AppDir → `appimagetool`
  (`APPIMAGE_EXTRACT_AND_RUN=1` for FUSE-less runners).
- `build-linux` job now produces `cereal-client-latest.AppImage` as the signed auto-update artifact
  (`latest-linux.json` → AppImage). `TargetFormat.Deb` has been **dropped** from `targetFormats` and
  the `.deb` is no longer built or published (Phase 3 — no parallel deb).

Open validation items: confirm the jpackage launcher is `bin/Cereal`, that `libsekret.so` is bundled
and loads inside the AppImage, and FUSE behaviour on Ubuntu 22.04/24.04.

## 1. Problem & current architecture

The in-app updater downloads a new build into the OS temp dir and "opens" it to install.
On Linux the artifact is a `.deb`; `xdg-open file.deb` on stock Ubuntu routes to GNOME
Software (snap), which cannot install local `.deb` files, so nothing happens. PRs #581
(prefer `xdg-open` over `java.awt.Desktop`) and #582 (reveal the download folder + "your
download is here" message) only soften the symptom. We want updates that actually install.

### End-to-end flow today (all platforms)

| Stage | Code |
| --- | --- |
| Release metadata | `latest-<os>.json` on the R2 CDN, fields `version`, `min_version`, `download_url`, `download_sha256`, `download_signature` |
| Metadata model | [LatestAppVersionJsonResponse.kt](../src/main/java/com/cereal/client/infrastructure/data/datasource/network/models/LatestAppVersionJsonResponse.kt) |
| Fetch + verify | [DigitalOceanSpacesApiClient.kt](../src/main/java/com/cereal/client/infrastructure/data/datasource/network/DigitalOceanSpacesApiClient.kt) → [ReleaseMetadataVerifier.kt](../src/main/java/com/cereal/client/infrastructure/data/datasource/network/security/ReleaseMetadataVerifier.kt) (RSA-SHA256 over `version\|min_version\|download_url\|download_sha256`) |
| Domain model | [Version.kt](../src/main/java/com/cereal/client/domain/model/app/Version.kt) (`downloadUrl`, `downloadSha256`, `storeUrl`) |
| Pick destination | [FileSystemTempDataSource.kt](../src/main/java/com/cereal/client/infrastructure/data/datasource/filesystem/FileSystemTempDataSource.kt) — `java.io.tmpdir` + URL last path segment |
| Download + integrity | [FileDownloader.kt](../src/main/java/com/cereal/client/infrastructure/data/datasource/network/FileDownloader.kt) — streamed, SHA-256 checked, cert-pinned host |
| Orchestration | [ApplicationRepositoryImpl.kt](../src/main/java/com/cereal/client/infrastructure/data/repository/ApplicationRepositoryImpl.kt) `downloadVersion()` |
| Install (the broken step) | [SystemRepositoryImpl.kt](../src/main/java/com/cereal/client/infrastructure/data/repository/SystemRepositoryImpl.kt) `open()` → [BrowserDataSource.kt](../src/main/java/com/cereal/client/infrastructure/data/datasource/os/BrowserDataSource.kt) `attemptXdgOpen` |
| UI entry points | [BootstrapViewModel.kt](../src/main/java/com/cereal/client/presentation/bootstrap/BootstrapViewModel.kt) `openInstaller`/`openFile`, [ApplicationSettingsViewModel.kt](../src/main/java/com/cereal/client/presentation/settings/ApplicationSettingsViewModel.kt) `onConfirmUpdate`/`openInstallerFile` |

### Build / release pipeline

- Packaging: Compose Desktop / jpackage. `targetFormats(Dmg, Exe, Deb, Msi)` and the `linux { }`
  block in [cereal-client.gradle.kts](../cereal-client.gradle.kts) (~L206, ~L258). `OPERATING_SYSTEM`
  BuildConfig (~L40-48) drives [OperatingSystemType](../src/main/java/com/cereal/client/domain/model/OperatingSystemType.kt).
- Native libs ship via `appResourcesRootDir = resources/` — `resources/linux-x64/libsekret.so`,
  `resources/linux-arm64/libsekret.so` (and the macOS Discord/sekret dylibs). Any new packaging
  must keep these inside the shipped image.
- CI: [release-client.yml](../../.github/workflows/release-client.yml) → `build-linux` job →
  [build-and-upload](../../.github/actions/build-and-upload/action.yml) →
  [build-client](../../.github/actions/build-client/action.yml) runs
  `gradlew cereal-client:packageReleaseDeb`, copies the artifact to
  `dist/client/cereal-client-latest.deb`, then
  [create-latest-version-json](../../.github/actions/create-latest-version-json/action.yml) hashes
  it with `openssl dgst -sha256`, signs the canonical message with `RELEASE_PRIVATE_KEY`, and uploads
  to R2. Download URL is fixed: `${CDN_URL}/client/cereal-client-latest.deb`.

### Key constraint

The hashed/signed artifact must be **byte-identical** to what the client downloads (the macOS
notarize/staple comments in `build-client` exist precisely because stapling rewrites the file after
hashing). Whatever Linux artifact we choose, the file that `create-latest-version-json` hashes must
be the exact file the client fetches.

---

## 2. Option A — AppImage (single self-contained executable)

"Install" = the app *is* one executable file the user runs. Auto-update = download a new AppImage,
verify it, atomically replace the running file, relaunch. No package manager, **no root**.

### Build / packaging changes
- jpackage (and therefore Compose's `targetFormats`) **does not emit AppImage**. It does emit a
  Linux *app-image* (a directory: `bin/Cereal`, `lib/`, `lib/runtime/`, `lib/app/...`). The plan is
  to run Compose's `createReleaseDistributable` (app-image) task and wrap that directory with
  `appimagetool`.
- New CI step in the `build-linux` job (or a dedicated Gradle task / shell action):
  1. `gradlew cereal-client:createReleaseDistributable` → produces
     `build/compose/binaries/main-release/app/Cereal/`.
  2. Assemble an `AppDir`: the app-image contents + top-level `AppRun` (exec `bin/Cereal`),
     `cereal.desktop`, and `cereal.png` (reuse `icon-linux.png`).
  3. `appimagetool AppDir Cereal-x86_64.AppImage` → copy to
     `dist/client/cereal-client-latest.AppImage`.
- Native libs: because the whole app-image is wrapped, `resources/linux-x64/libsekret.so` travels
  inside the AppImage automatically (verify it lands under `lib/app/resources/` and is loadable at
  runtime). No OS code-signing on Linux, so the macOS-signing memory note does not apply here.
- `targetFormats` can keep `Deb` during transition or drop it once AppImage is primary.

### Download artifact + release metadata
- No schema change. `download_url` becomes `…/cereal-client-latest.AppImage`; `download_sha256` and
  `download_signature` are produced over the AppImage exactly as today. `create-latest-version-json`
  is artifact-agnostic — just point `binary-path`/`download-url` at the AppImage.

### Invoking the install (replace running process)
- The AppImage runtime exports `$APPIMAGE` (absolute path of the running image) and `$APPDIR`. Read
  `APPIMAGE` to know which file to replace.
- New Linux-only branch (do **not** call `xdg-open`):
  1. Download to temp + SHA-256 verify (unchanged `FileDownloader`).
  2. `chmod 0755` the downloaded file.
  3. Atomically replace: `Files.move(temp, Path.of($APPIMAGE), ATOMIC_MOVE, REPLACE_EXISTING)`.
     Replacing a running executable's path is safe on Linux — the running process keeps the old
     inode until exit.
  4. `ProcessBuilder($APPIMAGE).start()` then `exitProcess(0)`.
- If `$APPIMAGE` is unset (running un-packaged, e.g. dev), fall back to the reveal-folder path.

### Auto-update UX
- Download → verify → swap file → relaunch, no password prompt, no external installer. The cleanest
  possible flow and it eliminates the root cause (no dependence on any system package handler).

### Privilege escalation
- None. The AppImage lives wherever the user put it (home dir, `~/Applications`, `~/.local/bin`),
  all user-writable.

### Integrity / signatures
- Unchanged and fully preserved — SHA-256 + RSA metadata signature over the AppImage, cert-pinned
  download. Optionally add embedded AppImage GPG signing later, but it is redundant given our chain.

### Failure handling / fallbacks
- Target path not writable (rare; e.g. user placed it under `/opt`) → fall back to reveal-folder
  + manual-replace message.
- `$APPIMAGE` unset → reveal-folder fallback.
- FUSE: classic AppImages need libfuse2, which **Ubuntu 24.04 dropped**. Mitigate by building with
  a static-runtime / type-2 appimagetool or instructing relaunch via `--appimage-extract-and-run`;
  validate on 22.04 and 24.04.

### Pros / cons
- **+** No root, atomic self-replace, single-file, root cause gone, no schema change, integrity intact.
- **−** New CI packaging path (appimagetool); migration story for existing `.deb` users (they keep
  the old `.deb`-installed copy until they switch); no automatic desktop-menu integration unless we
  drop a `.desktop` on first run; FUSE caveat on newer distros.

---

## 3. Option B — `.deb` + explicit privileged install

Keep building `.deb`; replace `xdg-open` with a privileged installer invocation.

### Build / packaging changes
- Essentially none — keep `TargetFormat.Deb` and the existing `build-linux` pipeline. The `.deb`
  installs to `/opt/cereal/` with a jpackage-generated launcher and `.desktop` entry (good native
  integration).

### Download artifact + release metadata
- No change. `download_url` stays `…/cereal-client-latest.deb`; same hash/signature.

### Invoking the install
- Linux-only branch replacing `attemptXdgOpen` for installers:
  - Preferred: `pkexec apt-get install -y /abs/path/to/file.deb` — `apt-get` (≥1.1, present on all
    supported Ubuntu/Debian) installs a local `.deb` **and resolves dependencies**; `pkexec` shows
    a polkit GUI auth dialog.
  - Alternatives: `gdebi <file>` (resolves deps, but gdebi is often not installed) or
    `pkexec dpkg -i <file>` (no dependency resolution — avoid).
- After a 0 exit, relaunch the upgraded binary at `/opt/cereal/bin/Cereal` and `exitProcess(0)`.

### Auto-update UX
- Download → verify → polkit password prompt → apt installs in place → relaunch. One password
  prompt per update.

### Privilege escalation
- Requires root via polkit/`pkexec` (`policykit-1`). Present on mainstream desktops; absent on
  minimal/headless setups.

### Integrity / signatures
- Our SHA-256 + RSA metadata signature still gates the file before we invoke apt (we are installing
  a local `.deb`, not using an apt repo, so there is no apt-GPG layer — our signature is the
  guarantee). Preserved.

### Failure handling / fallbacks
- `pkexec` dismissed/unauthorized (exit 126/127) or missing → fall back to reveal-folder (PR #582).
- `apt-get` non-zero → surface stderr, fall back to reveal-folder.

### Pros / cons
- **+** Tiny change, no new build path, keeps `.deb` system integration, no migration of installed
  users, fast to ship.
- **−** Password prompt every update (worse UX than AppImage); depends on `pkexec`/`apt` presence;
  `apt-get` local-deb behavior varies slightly across versions; still bound to the `.deb` world that
  caused the original snap/xdg-open breakage.

---

## 4. Comparison

| Dimension | A — AppImage | B — `.deb` + pkexec |
| --- | --- | --- |
| Root cause fixed | Yes (no system handler involved) | Partially (bypasses xdg-open, still `.deb`) |
| Update UX | Best — silent self-replace + relaunch | Password prompt each update |
| Privilege needed | None | Root (polkit) |
| Build changes | New appimagetool step | ~None |
| Metadata/integrity | Unchanged | Unchanged |
| Migration of installed users | Needed (deb→AppImage) | None |
| External deps at runtime | FUSE (newer-distro caveat) | pkexec/apt present |
| Desktop integration | Manual `.desktop` | Native (jpackage) |
| Effort | Medium | Low |

---

## 5. Recommendation

**Decision: go straight to AppImage as the primary Linux auto-updating format (no interim step).**
It is the only option that removes the root cause entirely (no package handler, no snap/xdg-open
dependency), needs no privilege escalation, and gives a true silent download→verify→swap→relaunch
experience while keeping the existing signature/SHA-256 integrity chain untouched.

Option B (`.deb` + `pkexec`) is documented above as the rejected alternative and as the emergency
fallback if AppImage hits a blocker — but the plan below commits to AppImage directly rather than
shipping the privileged-install path first.

---

## 6. Phased rollout (AppImage)

### Phase 0 — Install abstraction
- Introduce a Linux install abstraction instead of routing installers through
  `SystemRepository.open()`/`xdg-open`. Add e.g. `SystemRepository.installUpdate(file): InstallResult`
  with an `OperatingSystemType.Linux` implementation, and a new `LinuxUpdateInstaller` datasource
  alongside `BrowserDataSource`. Keep macOS/Windows on today's `open()` path untouched (gated by
  `OperatingSystemType.getCurrentOs()`, as `shouldAttemptXdg` already is in `SystemRepositoryImpl`).
- Wire `BootstrapViewModel.openFile`/`ApplicationSettingsViewModel.openInstallerFile` to call the
  new install path on Linux; on failure fall back to the existing reveal-folder behavior (#582).

### Phase 1 — AppImage build in CI
- Add a step to the `build-linux` job: `gradlew cereal-client:createReleaseDistributable` → assemble
  an `AppDir` (app-image contents + top-level `AppRun`, `cereal.desktop`, `cereal.png` from
  `icon-linux.png`) → `appimagetool` → `dist/client/cereal-client-latest.AppImage`.
- Verify `resources/linux-x64/libsekret.so` (and arm64) lands inside the AppDir and loads at
  runtime; re-verify the glibc/SQLite locale workaround (`-Djava.locale.providers=COMPAT,SPI`).
- Validate launch on Ubuntu 22.04 and 24.04 (FUSE: use static runtime / `--appimage-extract-and-run`
  if needed). Keep `.deb` building in parallel for now.

### Phase 2 — Metadata + client self-replace
- Metadata: point `latest-linux.json` at the AppImage (hash/sign via existing
  `create-latest-version-json`, unchanged). During transition, publish a separate
  `latest-linux-appimage.json` (or add a `format` field) so existing `.deb` clients aren't handed an
  AppImage they can't self-replace.
- Client: implement the `$APPIMAGE` atomic-replace (`Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`)
  + `chmod 0755` + relaunch + `exitProcess(0)` path in `LinuxUpdateInstaller`. Fall back to
  reveal-folder when `$APPIMAGE` is unset or the target path is read-only.
- Tests: in-memory installer fake asserting replace-target resolution, exit/relaunch handling, and
  fallback (follow the prefer-in-memory-over-mocks convention).

### Phase 3 — Migration & cleanup
- Nudge users still on the `.deb`-installed copy (in-app message linking to the AppImage, or a
  one-time bridge build). Once adoption is sufficient, make AppImage the only Linux target and retire
  `TargetFormat.Deb` (or keep `.deb` as a download-only, non-auto-updating artifact).

---

## 7. Risks
- **FUSE on Ubuntu 24.04+** breaks classic AppImage launch — must validate and possibly use
  extract-and-run / static runtime.
- **Native lib loading** inside the AppImage (`libsekret.so`) and the existing glibc/SQLite locale
  workaround (gradle `-Djava.locale.providers=COMPAT,SPI`) must be re-verified in AppImage form.
- **pkexec/polkit absence** on minimal desktops (Option B) → must fall back gracefully.
- **Atomic replace** must target `$APPIMAGE` exactly; replacing the wrong path or a read-only
  location must fall back, never corrupt the install.
- **Dual-format window** risks pushing the wrong artifact to the wrong client — guard with separate
  metadata files or a format field.

## 8. Open questions
1. Do we keep `.deb` as a manual download indefinitely, or fully migrate to AppImage?
2. Acceptable to require FUSE / extract-and-run on 24.04, or do we need a fully static runtime?
3. Where should the canonical AppImage live on disk, and do we want first-run `.desktop`
   integration (menu entry/icon)?
4. Add a `format`/`artifact_type` field to `latest-linux.json` to disambiguate deb vs AppImage
   during migration, or use separate metadata filenames?
5. Should we adopt zsync/AppImageUpdate delta updates later, or is full-file replacement sufficient?
