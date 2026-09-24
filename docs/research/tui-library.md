# Which JVM TUI library fits headless mode?

Research for [#9](https://github.com/Cereal-Automation/desktop-client/issues/9) (map #8).
Sources were checked on 2026-09-24 against each project's repository, published Maven Central
artifacts and release notes. Every claim links to the source it rests on.

## Recommendation

**Kotter** (`com.varabyte.kotter:kotter-jvm`, Apache-2.0), with **TamboUI** as the fallback if
the TUI turns out to need a full-screen, multi-pane layout.

Why Kotter:

- It is the only candidate that is both Kotlin/coroutine-native and a stable 1.x release.
- It has a masked input field.
- It runs on the JLine terminal stack, which ships Linux x86-64 and ARM64 natives and falls back to
  `stty` when no native library loads.

The price:

- We write list and select widgets ourselves.
- We pin the terminal to `SystemTerminal()`, so a missing TTY fails loudly instead of trying to
  open Swing.
- We add ProGuard keep rules for JLine's reflectively loaded terminal providers.

## Comparison

| Criterion | Kotter 1.4.0 | TamboUI 0.5.0 | Mosaic 0.18.0 | Lanterna 3.1.5 | Jexer 2.0.0 |
|---|---|---|---|---|---|
| Language / model | Kotlin, declarative `section {}` re-render | Java, ratatui-style immediate mode + Toolkit DSL | Kotlin, Compose runtime | Java, retained widgets + window manager | Java, windowed desktop metaphor |
| Coroutines / Flow | Native: run blocks are `suspend`; depends on kotlinx-coroutines 1.11.0 | Bridge: own render thread; post work via `runOnRenderThread` | Native: `LaunchedEffect`, Compose state | Bridge: GUI thread / `invokeLater` | Bridge: own event thread |
| Text input | yes (`input()`) | yes (`TextInput`) | **no** (layout primitives only) | yes (`TextBox`) | yes |
| Masked secret | yes (`input(viewMap = { '*' })`) | yes (`maskedField()` / `.masked()`) | no | yes (`TextBox.setMask`) | yes |
| Lists / selects | not built in; build on `onKeyPressed` | `List`, `Table`, `Tabs`, form `SELECT`/`CHECKBOX`/`TOGGLE` | no | `ActionListBox`, `ComboBox`, `CheckBoxList` | yes |
| ProGuard | JLine provider classes need `-keep` | JLine keep rules + `ServiceLoader` backends | Ships consumer rules; CI tests under ProGuard and R8 | Pure Java, low risk | Pure Java, low risk |
| Linux x86-64 + ARM64 | JLine natives for both, plus `exec` (`stty`) fallback | Same (JLine backend; Panama backend needs JDK 22+) | Bundled JNI `.so` for amd64 + aarch64 | Pure Java, calls `stty` | Pure Java, calls `stty` |
| No-TTY behaviour | Default falls back to a Swing `VirtualTerminal`, so pass `SystemTerminal()` | Backend picked by `ServiceLoader` / `TAMBOUI_BACKEND` | `NonInteractivePolicy` argument | Falls back to a Swing emulator unless AWT is headless or `setForceTextTerminal(true)` | Has a Swing backend |
| Licence | Apache-2.0 | MIT | Apache-2.0 | **LGPL-3.0** | MIT |
| Health | 1.4.0 (2026-07-29), 694★, 2 open issues, single maintainer | 0.5.0 (2026-09-14), 657★, 101 open issues, fast-moving 0.x | 0.18.0 (2025-08-21, no release in 13 months), 2.7k★, "experimental" | 3.1.5 stable, 3.2.0-alpha1 (2026-03), 96 open issues | Personal project, PRs closed, moved to Codeberg |

## Findings per criterion

### Coroutine / Flow friendliness

- **Kotter**: `Section.run` takes a `suspend RunScope.() -> Unit` block
  ([Section.kt](https://github.com/varabyte/kotter/blob/main/kotter/src/commonMain/kotlin/com/varabyte/kotter/runtime/Section.kt)),
  and `kotter-jvm` depends on `kotlinx-coroutines-core-jvm` 1.11.0, the same version this repo
  pins ([POM](https://repo1.maven.org/maven2/com/varabyte/kotter/kotter-jvm/1.4.0/kotter-jvm-1.4.0.pom);
  `gradle/libs.versions.toml`). An interactor's `Flow` can be collected inside a run block and
  written into a `liveVarOf`/`liveListOf`, which re-renders the section.
  Caveat: `session { }` itself is a blocking, non-`suspend` entry point
  ([Session.kt](https://github.com/varabyte/kotter/blob/main/kotter/src/commonMain/kotlin/com/varabyte/kotter/foundation/Session.kt)),
  so the headless `main` runs it on its own thread.
- **Mosaic** is the most natural fit here. It is the Compose runtime, so `LaunchedEffect` and
  Compose state work directly ([README](https://github.com/JakeWharton/mosaic#introduction)).
- **TamboUI** runs its own render loop with an input thread and a scheduler thread, and marshals
  cross-thread work onto the render thread
  ([TuiRunner.java](https://github.com/tamboui/tamboui/blob/main/tamboui-tui/src/main/java/dev/tamboui/tui/TuiRunner.java)).
  It is usable from coroutines, but you need an adapter.
- **Lanterna** and **Jexer** are classic callback GUIs with their own GUI thread. Coroutines need
  a dispatcher bridge.

### Form and input widgets

- **Kotter** has `input()` with validation (`rejectInput`), `Completions` and `multilineInput()`,
  and masks secrets with `input(viewMap = { '*' })`: "user will only ever see "*"s"
  ([README, input section](https://github.com/varabyte/kotter#%EF%B8%8F-user-input)). It has no list or
  select widget. You build them from `onKeyPressed` plus a `liveVarOf` index, which is a small
  amount of code, but we own it.
- **TamboUI** has the richest permissive widget set: `TextInput` with a mask character
  ([TextInput.java](https://github.com/tamboui/tamboui/blob/main/tamboui-widgets/src/main/java/dev/tamboui/widgets/input/TextInput.java)),
  plus `List`, `Table`, `Tabs` and forms with `TEXT`, masked, `CHECKBOX`, `TOGGLE` and `SELECT`
  fields ([forms.adoc](https://github.com/tamboui/tamboui/blob/main/docs/src/docs/asciidoc/forms.adoc);
  [README, Widgets](https://github.com/tamboui/tamboui#widgets)).
- **Mosaic** ships layout primitives only (`Text`, `Row`, `Column`, `Box`, `Spacer`, `Filler`) and
  `Modifier.onKeyEvent`
  ([mosaic-runtime ui/](https://github.com/JakeWharton/mosaic/tree/trunk/mosaic-runtime/src/commonMain/kotlin/com/jakewharton/mosaic/ui);
  [CHANGELOG 0.14.0](https://github.com/JakeWharton/mosaic/blob/trunk/CHANGELOG.md)). There is no
  text field, so we would write every widget, including cursor editing.
- **Lanterna** has a complete set: `TextBox.setMask`, `ComboBox`, `ActionListBox`, `CheckBoxList`,
  plus windows and dialogs
  ([gui2/](https://github.com/mabe02/lanterna/tree/master/src/main/java/com/googlecode/lanterna/gui2)).

### ProGuard / obfuscation

The release build runs ProGuard with per-library rule files in `cereal-client/proguard-rules/`, on
JDK 21 (`jvmToolchain(21)` in `cereal-client/cereal-client.gradle.kts`).

- **JLine** (used by Kotter and TamboUI) finds its terminal provider by reading a class name from
  `META-INF/jline/providers/<name>` and calling `loadClass` on it
  ([TerminalProvider.java](https://github.com/jline/jline3/blob/jline-3.30.16/terminal/src/main/java/org/jline/terminal/spi/TerminalProvider.java)).
  For example, the `jni` file names `org.jline.terminal.impl.jni.JniTerminalProvider`
  ([resource](https://github.com/jline/jline3/blob/jline-3.30.16/terminal-jni/src/main/resources/META-INF/jline/providers/jni)).
  Renaming those classes breaks terminal creation, so we need a `jline.pro` that keeps
  `org.jline.terminal.impl.**` and `org.jline.nativ.**` (the JNI side). This is the same shape of
  problem `jna.pro` already solves.
- **TamboUI** also discovers backends and capabilities through `ServiceLoader`
  ([BackendFactory.java](https://github.com/tamboui/tamboui/blob/main/tamboui-core/src/main/java/dev/tamboui/terminal/BackendFactory.java)),
  which adds keep rules for the provider classes.
- **Mosaic** is the best-prepared. Its JVM jar ships `META-INF/proguard/com.jakewharton.mosaic-tty.pro`,
  and its build runs the test suite against ProGuard- and R8-processed jars
  ([mosaic-tty/build.gradle](https://github.com/JakeWharton/mosaic/blob/trunk/mosaic-tty/build.gradle)).

### Linux x86-64 and ARM64

- **JLine native 3.30.16**, which Kotter 1.4.0 pulls in, contains `Linux/x86_64` and `Linux/arm64`
  `libjlinenative.so` (checked by listing the
  [Maven Central jar](https://repo1.maven.org/maven2/org/jline/jline-native/3.30.16/)).
  Provider order is `ffm, jni, jansi, jna, exec`
  ([TerminalBuilder.java](https://github.com/jline/jline3/blob/jline-3.30.16/terminal/src/main/java/org/jline/terminal/TerminalBuilder.java)).
  On JDK 21 FFM is skipped (it needs 22+), JNI is used, and `exec` (shelling out to `stty`)
  remains as a fallback.
- **Mosaic's** `mosaic-tty-jvm-0.18.0.jar` bundles `jni/amd64/libmosaic.so` and
  `jni/aarch64/libmosaic.so`. FFM is used only on Java 22+
  ([CHANGELOG 0.18.0](https://github.com/JakeWharton/mosaic/blob/trunk/CHANGELOG.md)).
- **Lanterna** and **Jexer** are pure Java and put the terminal into raw mode by running `stty`
  against `/dev/tty`
  ([UnixLikeTTYTerminal.java](https://github.com/mabe02/lanterna/blob/master/src/main/java/com/googlecode/lanterna/terminal/ansi/UnixLikeTTYTerminal.java)).
  Minimal images such as distroless need `stty` (coreutils) present.

### SSH and Docker (TTY behaviour)

Every candidate needs a real TTY: `ssh -t` or `docker run -it` / `docker attach`. The differences
are in what each one does when there is no TTY.

- **Kotter**: the default provider list is `SystemTerminal()` first, then `VirtualTerminal.create()`
  ([SessionSupport.kt](https://github.com/varabyte/kotter/blob/main/kotter/src/jvmMain/kotlin/com/varabyte/kotter/foundation/SessionSupport.kt)).
  `VirtualTerminal` is a Swing window. `SystemTerminal` builds JLine with `.dumb(false)`, so it
  throws when the environment lacks the needed terminal features
  ([SystemTerminal.kt](https://github.com/varabyte/kotter/blob/main/kotter/src/jvmMain/kotlin/com/varabyte/kotter/terminal/system/SystemTerminal.kt)).
  In headless mode, call `session(terminal = SystemTerminal())` and report a clear "attach a TTY"
  error. Also note that `SystemTerminal` redirects `System.out`/`System.err` into a null stream
  while it runs, so Logback's console appender must be off (file appender only).
- **Lanterna**'s `DefaultTerminalFactory` opens a Swing terminal emulator unless AWT is headless, a
  console is present, or `setForceTextTerminal(true)` is set
  ([DefaultTerminalFactory.java](https://github.com/mabe02/lanterna/blob/master/src/main/java/com/googlecode/lanterna/terminal/DefaultTerminalFactory.java)).
- **Mosaic** takes an explicit `NonInteractivePolicy`
  ([CHANGELOG 0.17.0](https://github.com/JakeWharton/mosaic/blob/trunk/CHANGELOG.md)). Its README
  notes it will not run under `./gradlew run` or inside the IDE. This applies to all candidates.
- JDK 24+ warns on restricted native access. Kotter's README recommends
  `--enable-native-access=ALL-UNNAMED` for JLine
  ([README](https://github.com/varabyte/kotter#jvm)). This does not bite on our JDK 21, but it will
  on the next toolchain bump.

### Maintenance and licence

Repository figures are from the GitHub API on 2026-09-24.

- **Kotter**: Apache-2.0, releases v1.3.0 (2026-05-06) and v1.4.0 (2026-07-29), 2 open issues.
  Effectively one maintainer, which is a bus-factor risk, offset by a small, readable codebase.
- **TamboUI**: MIT, four 0.x releases between 2026-04 and 2026-09. The 0.5.0 notes say Apache
  Camel's new TUI runs on it
  ([release](https://github.com/tamboui/tamboui/releases/tag/v0.5.0)). Expect API churn before 1.0.
- **Mosaic**: Apache-2.0. The README calls it "experimental". The last release was 0.18.0 on
  2025-08-21, and trunk is active but unreleased. Unreleased trunk moves to the AndroidX Compose
  runtime ([CHANGELOG, Unreleased](https://github.com/JakeWharton/mosaic/blob/trunk/CHANGELOG.md)),
  which would have to coexist on one classpath with Compose Desktop 1.10.1's runtime.
- **Lanterna**: LGPL-3.0-only, with no permissive option. Today every copyleft entry in
  `THIRD-PARTY.md` is dual-licensed and we elect the permissive side. LGPL-3 section 4 requires
  that users can swap in a modified library. Shipping Lanterna shrunk and obfuscated into the
  ProGuard release jar would conflict with that, so it would have to stay an unobfuscated,
  separate jar. That is a deliberate licence decision, not a mention.
- **Jexer**: the author calls it "a small personal project"; pull requests are closed and the
  project moved to Codeberg
  ([README](https://gitlab.com/AutumnMeowMeow/jexer/-/blob/master/README.md)).
- **JLine** (transitive dependency): BSD-3-Clause.

## Trade-offs of the recommendation

- **We own the selection widgets.** Kotter has no list or select widget. A reusable select and
  multiselect built on `onKeyPressed` plus `liveVarOf` is the main custom UI work.
- **The layout is inline, not windowed.** Kotter re-renders "sections" in the normal scrollback,
  without windows, panes or mouse support. That suits a prompt-driven "operate and configure"
  TUI over SSH. If the design (map #8) calls for a full-screen dashboard (a live task table next to
  logs, tabs), TamboUI's `Table`/`Tabs`/layout covers it, at the cost of Java-style threading
  glue and a 0.x API.
- **ProGuard needs one new rule file.** A `jline.pro` has to keep the provider and native classes,
  and it has to be verified on a `runRelease` build inside a container.
- **Why Mosaic lost despite the best Compose and ProGuard fit.** There are no widgets at all, no
  release in 13 months, and the pending AndroidX runtime switch is a classpath risk next to
  Compose Desktop. It would be worth revisiting if it gains a text field and a 1.0.

## Open questions surfaced

1. **Inline or full-screen?** The TUI design should decide whether the task view is a live,
   full-screen dashboard. That choice alone decides Kotter versus TamboUI.
2. **Logging while the TUI owns the terminal.** Kotter nulls `System.out`/`err`, and Sentry and
   Logback console output would corrupt any TUI. Headless mode needs a file-only logging config.
3. **Docker entrypoint without a TTY.** Should `docker run` without `-it` start a
   non-interactive "run tasks unattended" path while the TUI waits for `docker attach`? Or should
   headless mode require a TTY?
4. **Packaging.** Headless targets a Linux server/container, not a Compose-packaged desktop
   installer. Does the headless build share the ProGuard pipeline of `compose.desktop` release
   builds, or does it need its own distribution task? That decides where `jline.pro` is applied.
