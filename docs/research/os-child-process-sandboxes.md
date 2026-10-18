# How strong a sandbox can each OS give a child process?

Research for [#159](https://github.com/Cereal-Automation/desktop-client/issues/159), part of the map
[#158 Run scripts in their own sandboxes](https://github.com/Cereal-Automation/desktop-client/issues/158).
Researched on 2026-10-18 against vendor docs (Apple, Microsoft, kernel.org, GraalVM), Chromium's
sandbox design docs, and the bubblewrap README. Facts marked **(unverified)** came from a non-primary
source or couldn't be confirmed against the owning doc; facts marked **(prototype)** have no
documented answer and need a test.

**Gist:** every OS can confine a script JVM well enough to keep it away from the user's files and
the client's memory without admin rights, and every mechanism can still leave outbound network open.
Each OS needs a different trick, though, and none of them can be reached through `ProcessBuilder`
alone:

- **macOS**: launch the script JVM as a separately signed executable that carries
  `com.apple.security.app-sandbox` + `network.client` (no `inherit`). This is the only *supported*
  route. `sandbox-exec`/SBPL is undocumented, called "unsupported" by Apple DTS, and the
  `sandbox_init(3)` man page now says its profiles get processes killed when built against SDK ≥ 27.
- **Windows**: launch through `CreateProcessW` with an AppContainer (`internetClient` capability) plus
  a Job Object. It needs no admin and no MSIX package. The open questions are loopback (kdriver ↔
  Chrome CDP) and Chrome's own sandbox inside an AppContainer.
- **Linux**: Landlock (kernel ≥ 5.13, unprivileged, no userns needed) for the filesystem, plus seccomp.
  bubblewrap is stronger (mount/PID/IPC namespaces) but depends on unprivileged user namespaces, which
  Ubuntu 24.04 neuters by default for unconfined apps like ours. Landlock < ABI 9 (kernel 7.1) doesn't
  control `connect()` to pathname UNIX sockets, so the D-Bus session bus stays reachable.
- **GraalVM in-process** is not an alternative today: the documented `ISOLATED`/`UNTRUSTED` policies
  cover JavaScript and Wasm only, and `CONSTRAINED` forbids process creation and host sockets, which
  scripts need for Chrome and their own HTTP clients.

## Per-OS comparison

"Deny" columns: can the mechanism stop the script from doing this? "Allow" columns: can it still
let the script do this? All rows need no admin rights and no user-installed software unless noted.

| OS | Mechanism | FS outside scratch | Parent memory | Arbitrary spawn | Network | Still allows outbound net | Still allows spawning Chrome | Maturity / status | JVM effort |
|---|---|---|---|---|---|---|---|---|---|
| macOS | App Sandbox on a separately signed helper executable (own entitlements) | Deny (container only, plus entitlement exceptions) | Deny | Children always inherit the sandbox; can't stop exec, but they're confined too | Deny unless `network.client` | Yes (`network.client`) | Yes, but Chrome runs inside our sandbox and can't install its own (prototype) | Supported, Apple's recommended route | Medium: extra signed launcher in the bundle, notarized |
| macOS | Seatbelt via `sandbox_init`/`sandbox-exec` + custom SBPL | Deny (fine-grained, path rules) | Deny | Deny (`process-exec` rules) | Deny or allow per rule | Yes | Yes (allowlisted path) | Deprecated, "unsupported" per DTS, kill switch at SDK ≥ 27 | Low-medium, but a shipping risk |
| Windows | AppContainer (`SECURITY_CAPABILITIES` on `CreateProcessW`) | Deny (user profile not ACL'd for app packages; grant scratch dir via ACL) | Deny (Low IL, no-read-up on process objects) | Children stay in the AppContainer | Deny unless `internetClient` | Yes (`internetClient`); loopback across processes is the open risk | Yes, inside the container (prototype) | Mature, documented for Win32 launchers | Medium: JNA `CreateProcessW` + `STARTUPINFOEX`, ACL grants for JRE + scratch |
| Windows | LPAC (AppContainer + opt out of ALL APPLICATION PACKAGES) | Stronger deny (no registry/COM without capabilities) | Deny | As above | As above | Yes | Unlikely without many capabilities (prototype) | Win10 RS2+ | Higher: JRE/Chrome need explicit grants |
| Windows | Restricted token + Untrusted/Low IL (Chrome renderer style) | Deny with deny-only user SID; Low IL alone blocks writes only, not reads | Deny | Job object can cap to 1 process | Not controlled (sockets aren't securable) | Yes | Hard: JVM and Chrome barely run on a fully restricted token | Mature, Chrome uses it | High |
| Windows | Job Object | No | No | Yes (active-process limit 1), plus kill-on-close for the whole tree | No | n/a | n/a | Mature | Low: complement, not a sandbox |
| Linux | Landlock (+ `no_new_privs`) | Deny (path allowlist; ABI 1, kernel 5.13+) | Deny (Landlock ptrace rule) | Restrict `EXECUTE` to the JRE and Chrome paths | TCP by port only (ABI 4, 6.7+); no IP/host rules | Yes (don't handle net rights) | Yes, but `no_new_privs` disables Chrome's setuid helper | Upstream since 5.13; ABI varies by distro kernel | Medium: tiny native launcher that restricts then `execve`s java |
| Linux | seccomp-bpf | No (syscall-level only) | Deny `ptrace`/`process_vm_readv` | Can deny `execve`, but Chrome needs it | Can deny `socket` families | Yes | Yes if `execve` allowed | Mature | Medium: same launcher |
| Linux | bubblewrap (user/mount/PID/IPC/net namespaces) | Deny (empty tmpfs root, bind what you need) | Deny (PID namespace) | Can't block, PID namespace contains it | `--unshare-net` = no net | Yes (share host net) | Nested userns for Chrome (prototype) | Mature (Flatpak), but needs unprivileged userns; Ubuntu 24.04 blocks capabilities in userns without an AppArmor profile (root to install) | Medium + must ship/require `bwrap` |
| Any | GraalVM Espresso / polyglot sandbox | In-process | Same process | `CONSTRAINED` forbids it | `CONSTRAINED` forbids host sockets | No | No | Policies documented for JS/Wasm only | Not viable |

## macOS

### App Sandbox on a helper executable (supported)

- Our app isn't sandboxed today (`cereal-client/entitlements.plist` has hardened-runtime exceptions
  and `network.client`, but no `com.apple.security.app-sandbox`).
- A non-sandboxed parent **can** launch a child that has its own sandbox. Apple DTS lists the fixes for
  "Process is not in an inherited sandbox" as "Sandbox the parent. Unsandbox the child. Run the child
  in its own sandbox by removing the `com.apple.security.inherit` entitlement."
  ([DTS, Resolving App Sandbox Inheritance Problems](https://developer.apple.com/forums/thread/706390)).
  So the script JVM launcher is signed with `app-sandbox` + `network.client` and **without** `inherit`.
- Children of a sandboxed process can't escape: "If a sandboxed process runs another program as a child
  process, that child process always inherits its sandbox from the parent," and "A process is not
  allowed to change its sandbox" (same thread). This covers arbitrary spawning (spawned processes are
  confined, not blocked). It also means **Chrome launched by the script runs inside the script's sandbox
  and can't apply its own Seatbelt profile.** Whether Chrome then needs `--no-sandbox` or just fails to
  start its renderers is **(prototype)**. Electron only supports App Sandbox with a dedicated MAS build:
  "The standard darwin build of Electron will fail to launch when run under App Sandbox"
  ([Electron MAS guide](https://www.electronjs.org/docs/latest/tutorial/mac-app-store-submission-guide)),
  which hints that stock Chrome may fail too.
- Network: `com.apple.security.network.client` is "Network socket for connecting to other machines"
  ([Entitlement Key Reference](https://developer.apple.com/library/prerelease/mac/documentation/Miscellaneous/Reference/EntitlementKeyReference/Chapters/EnablingAppSandbox.html)).
  Loopback to Chrome's CDP port counts as an outgoing connection on the same machine (same page:
  "or on the same machine").
- Filesystem: the sandboxed child gets a container at `~/Library/Containers/<bundle id>/`, which is the
  natural scratch dir. Everything else in the home folder is denied unless granted (**unverified**
  against the live App Sandbox page, which is JS-rendered; consistent with the archived design guide).
- Signing: entitlements only exist in a code signature, so the helper must be signed with our Developer
  ID and notarized with hardened runtime. The JVM still needs `cs.allow-jit` and friends. jpackage signs
  "executables and libraries in the bundle" with one `--mac-entitlements` file
  ([jpackage man page](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html)), so a
  second launcher with *different* entitlements means re-signing it after jpackage
  (`codesign --entitlements`), or shipping a second runtime copy. It doesn't need the Mac App Store.
- Caveat: the child's container is keyed on its signing identifier, so all script JVMs signed the same
  share one container unless we split them by directory under it (granularity decision for #158).

### Seatbelt / `sandbox-exec` (works today, not supported)

- Chromium uses the `sandbox(7)` API ("Seatbelt") with `(deny default)` profiles compiled and applied
  per process ([Chromium mac sandbox README](https://chromium.googlesource.com/chromium/src/+/main/sandbox/mac/README.md)).
  Bazel, Codex and Claude Code use `sandbox-exec` too (**unverified**, secondary source).
- Apple DTS: "What you're doing here is unsupported," SBPL "has never been documented for third-party
  use, and thus is not considered API. That's why everything within `<sandbox.h>` is deprecated"
  ([DTS forum thread 777509](https://developer.apple.com/forums/thread/777509)). DTS points to App Sandbox,
  Endpoint Security (`es_new_descendants_client`, macOS 27, needs a privileged entitlement) or
  Virtualization.framework instead.
- The `sandbox_init(3)` man page now says the named profiles "must not be used when building against
  macOS SDK >= 27.0" and "Processes opting into them will be killed"
  ([openssh-unix-dev, Aug 2026](https://lists.mindrot.org/pipermail/openssh-unix-dev/2026-August/042691.html),
  quoting the man page). That covers the `kSBX*` named profiles; whether custom SBPL via
  `sandbox_init_with_parameters` is also affected isn't stated **(unverified)**.
- Strengths if used anyway: path-level filesystem rules, `process-exec` allowlists, network rules,
  no signing changes. It's the most expressive option on macOS and the least durable.

## Windows

### AppContainer (main candidate)

- A Win32 app can launch any executable into an AppContainer itself: `CreateAppContainerProfile`, then
  `CreateProcess` with `PROC_THREAD_ATTRIBUTE_SECURITY_CAPABILITIES` in `STARTUPINFOEX`. No package or
  manifest is needed; "in the case of an application launching an AppContainer process itself, the
  application needs to determine the name (moniker)"
  ([Launch an AppContainer](https://learn.microsoft.com/en-us/windows/win32/secauthz/implementing-an-appcontainer)).
  Chromium's sandbox is "user-mode only" and "the user does not need to be an administrator"
  ([Chromium sandbox design](https://chromium.googlesource.com/chromium/src/+/main/docs/design/sandbox.md)).
- Filesystem: access is the intersection of the user's and the AppContainer's rights. Anything not ACL'd
  for the package SID or ALL APPLICATION PACKAGES is denied, which includes the user profile. The
  profile gets its own `LOCALAPPDATA`/`TEMP` under `AppData\Local\Packages\<moniker>\AC`, which is the
  scratch dir (same page). The JRE and Chrome install dirs need read/execute for the package SID. The
  user can set those ACLs on their own files, no admin needed.
- Memory/processes: AppContainers run at Low IL and are isolated "from accessing processes and windows
  belonging to other applications" (same page).
- Network: "without the *network* capability, an AppContainer cannot access the network" (same page);
  grant `internetClient` for outbound. Chromium: "network checks are enforced if the token is a Low Box
  token and the INTERNET_CLIENT Capability is not present."
- **Loopback risk (prototype):** for UWP apps, loopback "cannot be used for interprocess communication
  (between two different apps)" and exemptions go through `CheckNetIsolation`, which is for development
  ([MS archived doc](https://learn.microsoft.com/en-us/previous-versions/windows/apps/hh780593(v=win.10))).
  The doc only mentions loopback "within the same process" being allowed. Whether a script JVM and the
  Chrome it spawns (same AppContainer, different processes) can talk over `127.0.0.1` CDP isn't
  documented. Host ↔ script IPC should use inherited pipes, not TCP.
- Chrome inside an AppContainer would need to create its own nested sandbox (restricted tokens, its own
  AppContainers). Whether that works from a parent AppContainer is **(prototype)**.
- LPAC is stricter: it can't open registry keys without `registryRead` or use COM without `lpacCom`
  (same page). A JVM plus Chrome under LPAC is likely more effort than it's worth.

### Restricted tokens, integrity levels, Job Objects

- Chromium's renderer token: logon SID only, all other SIDs deny-only, no privileges, Untrusted IL. It
  can't protect "TCP/IP sockets" or FAT volumes (Chromium design doc). Low IL alone only blocks
  *writes* up; reading user files needs deny-only SIDs, which break most of what a JVM touches.
- Job Objects aren't a sandbox but are the stability half: all processes `CreateProcess`'d from a job
  member join it, a job can cap active processes and memory, and `JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE`
  kills the whole tree when the host's handle closes
  ([Job Objects](https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects)). Chromium
  uses a one-process cap to block child creation. We can't use that cap (Chrome), but kill-on-close
  covers "client crashes, scripts linger."

## Linux

The Linux release is an AppImage (`cereal-client/appimage/AppRun`), so the JRE lives on a per-launch
FUSE mount (`/tmp/.mount_*`). Any path policy has to be computed at launch.

### Landlock

- Unprivileged: "For unprivileged processes, setting the no_new_privs attribute is required by
  Landlock" ([kernel Landlock docs](https://docs.kernel.org/userspace-api/landlock.html)). It needs no
  user namespaces, so the Ubuntu restriction doesn't apply.
- ABI by kernel ([landlock(7)](https://man7.org/linux/man-pages/man7/landlock.7.html)): 1 = 5.13 (fs),
  2 = 5.19 (refer), 3 = 6.2 (truncate), 4 = 6.7 (TCP bind/connect by port), 5 = 6.10 (device ioctl),
  6 = 6.12 (scope abstract UNIX sockets and signals), 7 = 6.15, 8 = 7.0 (TSYNC: apply to all
  threads), 9 = 7.1 (pathname UNIX socket resolution). Probe the ABI, not the kernel version.
- What it can't do: restrict chdir/stat/chmod/chown and similar, files opened before sandboxing,
  sockets/pipes via `/proc/<pid>/fd` (kernel docs). Network is ports only, so "outbound anywhere" just
  means not handling the net rights. **Bypass to plan for:** before ABI 9, `connect()` to pathname UNIX
  sockets isn't mediated, so the script can reach the D-Bus session bus (Secret Service, systemd user
  manager) unless seccomp or a network namespace blocks it. Before ABI 6, abstract sockets (X11) and
  signals to other processes are open.
- Inheritance: "Every new thread resulting from a clone(2) inherits Landlock domain restrictions" and
  they can't be removed (kernel docs). A JVM can't apply it to itself cleanly before ABI 8: the JVM
  already has GC/JIT/Finalizer threads when `main` runs. So the practical shape is a small native
  launcher that restricts itself and then `execve`s java, the pattern of the kernel's
  `samples/landlock/sandboxer.c` (that restrictions survive `execve` follows from them being bound to
  credentials; the docs don't say it outright **(unverified)**).
- Parent memory: the kernel docs say a sandboxed process may only ptrace targets whose access rights
  are a subset of its own, so a landlocked child can't ptrace or `process_vm_readv` the unconfined
  client (**unverified**: the ptrace section of the docs couldn't be fetched (HTTP 429); this is from memory
  of the upstream text).
- Chrome: `no_new_privs` disables setuid binaries, so Chrome's setuid `chrome-sandbox` helper can't
  work. Chrome needs unprivileged userns for its own sandbox, or `--no-sandbox`.

### seccomp-bpf

- A filter "evaluated every time the sandboxed process makes a syscall"; it needs "an exhaustive list
  of syscalls" ([Chromium Linux sandbox README](https://chromium.googlesource.com/chromium/src/+/HEAD/sandbox/linux/README.md)).
  Useful as a deny-list on top of Landlock: `ptrace`, `process_vm_readv/writev`, `socket(AF_UNIX)`
  (keep `socketpair`, which Chrome uses), `mount`, `bpf`, `keyctl`. It can't see paths, so it isn't a
  filesystem sandbox. It's inherited across `execve` and needs `no_new_privs`, same as Landlock.

### bubblewrap

- Unprivileged sandbox used by Flatpak. It builds an empty tmpfs root plus user/IPC/PID/net/UTS
  namespaces and seccomp; "not a complete, ready-made sandbox with a specific security policy". The
  setuid mode "has been removed", so it requires unprivileged user namespaces
  ([bubblewrap README](https://github.com/containers/bubblewrap)). Caveats from the README: anything
  bind-mounted (e.g. a D-Bus socket) can be used to escalate, `--new-session` is needed against
  `TIOCSTI` (CVE-2017-5226), and apps with their own sandbox (browsers) may break.
- Strongest Linux option: the PID namespace hides the client process, the mount namespace hides `$HOME`,
  and sharing the host netns keeps outbound network.
- **Availability:** Ubuntu 24.04's default AppArmor policy "allows the use of user namespaces for
  unprivileged and unconfined applications but will deny the subsequent use of any capabilities within
  the user namespace" (24.04 release notes, via search; Ubuntu's
  [23.10 announcement](https://ubuntu.com/blog/ubuntu-23-10-restricted-unprivileged-user-namespaces)
  names the sysctls `kernel.apparmor_restrict_unprivileged_userns` and `..._unconfined`). The fix is an
  AppArmor profile under `/etc/apparmor.d` (root) or the sysctl (root). AppImages already hit this
  with Chromium's sandbox and fall back to `--no-sandbox`. `bwrap` also isn't guaranteed to be
  installed, so we'd ship it in the AppImage, and it still won't run on stock Ubuntu 24.04 without root.
- Qualys reported bypasses of the Ubuntu restriction itself (`aa-exec`, permissive profiles)
  (**unverified**, secondary write-up).

## GraalVM in-process alternative

- Policies: `TRUSTED`, `CONSTRAINED` (no native access, no process creation, no `System.exit`, no host
  file/socket IO), `ISOLATED` (separate VM and heap, CPU/heap limits), `UNTRUSTED` (adds output limits,
  no multithreading). The guide covers JavaScript and WebAssembly guests only. `ISOLATED`/`UNTRUSTED`
  need the `js-isolate` artifacts, available in Community from GraalVM 25.1
  ([GraalVM sandboxing guide](https://www.graalvm.org/latest/security-guide/sandboxing/)).
- Espresso (Java on Truffle) advertises "improved isolation of the host Java VM and the Java program
  running on Truffle" ([Espresso docs](https://www.graalvm.org/latest/reference-manual/espresso/)), but
  no documented sandbox policy covers it.
- Even under `CONSTRAINED`, scripts couldn't spawn Chrome or open sockets for OkHttp/Ktor, so scripts
  would lose capabilities #158 says they keep. It could only work if the host owned all IO (browser and
  HTTP brokered through the SDK).

## Facts that bear on the #158 decisions

- **Nothing here is reachable through `ProcessBuilder`.** Windows needs `CreateProcessW` +
  `STARTUPINFOEX` (JNA is already a dependency, `libs.versions.toml` `jna = 5.19.1`; FFM is still
  preview on our JDK 21 toolchain). Linux needs a tiny native launcher (Landlock + seccomp, then
  `execve`). macOS needs a separately signed launcher executable. Plan for one small native launcher
  per OS.
- **Chrome is the common pain point.** On all three OSes the script's Chrome ends up inside the
  script's sandbox, and its own sandbox either can't nest (macOS: "not allowed to change its sandbox";
  Linux: `no_new_privs` kills the setuid helper) or is undocumented (Windows AppContainer). A
  host-owned browser that hands the script a CDP endpoint avoids nesting, keeps Chrome's own sandbox,
  and sidesteps Windows loopback-in-AppContainer. This is input to "Browser ownership".
- **Outbound network stays open on every mechanism** (App Sandbox `network.client`, AppContainer
  `internetClient`, Landlock without net rights). Narrowing later only works by port on Linux, by
  capability class on Windows, and not at all with App Sandbox. Host-level allowlists would need a
  proxy.
- **Scratch dir falls out of the mechanism** on macOS (`~/Library/Containers/<id>`) and Windows
  (`AppData\Local\Packages\<moniker>\AC`). On Linux we pick it.
- **Strength differs per OS:** macOS App Sandbox and Windows AppContainer are vendor-maintained and
  roughly equal. Linux depends on the kernel (Landlock ABI) and on whether userns works. On Ubuntu
  24.04 with a 6.8 kernel we'd get Landlock ABI 4 + seccomp. That's weaker: no signal or
  abstract-socket scoping, and D-Bus must be blocked by seccomp.
- **Prototype list:** (1) stock Chrome under a child App Sandbox on macOS; (2) Chrome under an
  AppContainer with `internetClient`, including CDP over loopback between JVM and Chrome; (3) JVM +
  Chrome under Landlock ABI 4 on Ubuntu 24.04 from inside the AppImage; (4) the hostile acceptance
  fixture against each.
