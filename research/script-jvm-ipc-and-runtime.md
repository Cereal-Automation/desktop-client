# How would a script JVM talk to the client, and how do we ship one?

Research for [#160](https://github.com/Cereal-Automation/desktop-client/issues/160) (map: [#158](https://github.com/Cereal-Automation/desktop-client/issues/158)).
Researched 2026-10-18 against desktop-client `origin/master` (3b8caee), sdk `origin/HEAD` (b12571a), Compose Multiplatform Gradle plugin `v1.12.2` (the version in `gradle/libs.versions.toml`), and JDK 21 (`jvmToolchain(21)`).

## Answer

- **IPC:** use a **bidirectional JSON-RPC 2.0 protocol over the child's stdin/stdout**. Frame messages LSP-style with a `Content-Length` header, encode them with kotlinx-serialization JSON, and add LSP's `$/cancelRequest` for cancellation. Either side can send requests, which covers reverse calls such as `shouldFinish`. Binary payloads go as base64 for now. Rule out Java RMI, because the host would deserialize Java objects sent by an untrusted process. Rule out gRPC as well: it doesn't fit, it pulls a large dependency tree into the script JVM, and grpc-java only supports Unix sockets on Linux.
- **Runtime:** **keep `bin/java` in the existing jlink runtime.** The native commands cost about 1 MB (the `java` launcher alone is 70 KB). A second jlink runtime costs about 78 MB. Compose 1.12.2 provides no DSL switch for this, so the cheapest route is a `doLast` on `createRuntimeImage` that copies `bin/java` from the same JDK. That was verified locally (see below). On macOS, set `runtimeEntitlementsFile` so the child `java` is signed with the JIT entitlements; Compose already re-signs every executable under `Contents/runtime`. Notarization rules don't change: every executable needs a Developer ID signature, the hardened runtime and a secure timestamp.

## Part 1: IPC

### What has to cross the boundary (sdk `origin/HEAD`)

| SDK surface | Shape | Direction over IPC |
|---|---|---|
| `Script.onStart/execute/onFinish` | `suspend`, returns data | host → script request |
| `execute(..., statusUpdate: suspend (String) -> Unit)` | lambda created by the host | script → host request (the lambda is a stub in the child) |
| `ComponentProvider.*()` components (logger, notification, preference, llm, artifact, license, userInteraction, scriptLauncher) | `suspend` calls | script → host requests |
| `UserInteractionComponent.showUrl/showHtml(shouldFinish: (WebResourceRequest) -> Boolean)` | lambda created by the script, called by the host for each request | **host → script request** while the outer script → host call is still pending |
| `ArtifactComponent.emit(bytes: ByteArray)`, `UserPart.Image(bytes)` | binary | script → host |
| `ScriptLauncherComponent.start(Class<out Script<*>>)` → live `ScriptHandle` | class plus a remote object | needs redesign: send the class name and return a handle id. Owned by the map's "SDK boundary redesign" item |

The SDK has no runtime dependencies (`cereal-sdk.gradle.kts` only declares test dependencies), and its data classes carry no `@Serializable`. The wire DTOs therefore have to live in the IPC layer, either as a mirror of the SDK types or with `@Serializable` added in a new SDK version.

### Comparison

| | JSON-RPC over stdio + kotlinx-serialization | gRPC (grpc-kotlin) | Java RMI |
|---|---|---|---|
| suspend calls | Natural fit: each request id maps to a `CompletableDeferred` | Unary RPCs are `suspend` functions in generated stubs ([grpc.io Kotlin basics](https://grpc.io/docs/languages/kotlin/basics/)) | Blocking calls only. Needs a thread per call wrapped in `withContext(IO)` |
| Cancellation | Not in JSON-RPC 2.0. LSP adds `$/cancelRequest`, and a cancelled request "still needs to return from the server and send a response back" ([LSP 3.17](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/)). The receiver cancels the coroutine `Job` that runs the handler | Built in. Client cancel, deadline expiry and I/O errors all cancel the call, but gRPC "does not have a mechanism to interrupt the application-provided server handler" ([gRPC cancellation](https://grpc.io/docs/guides/cancellation/)) | None. Interrupting the caller thread doesn't reach the remote side |
| Reverse calls (host → script lambda) | Symmetric: both peers send requests. LSP servers already request things from clients (e.g. `window/workDoneProgress/create`), and LSP4J documents that the approach "works nicely in both directions" ([LSP4J jsonrpc.md](https://github.com/eclipse-lsp4j/lsp4j/blob/main/documentation/jsonrpc.md)) | Client → server only. Callbacks need a bidirectional stream used as a hand-rolled RPC, or a second server inside the script JVM | Export a callback `Remote` object, which opens a listening socket in the script JVM and requires a stub per lambda |
| Streaming | Notifications (logger, `statusUpdate`) or LSP-style `$/progress` partial results | First class (`Flow` for server, client and bidirectional streams) | None |
| Binary | JSON has no bytes type, so base64 (+33%) through a custom serializer. VS Code's RPC protocol carries raw `VSBuffer`s beside JSON args (`ReplyOKJSONWithBuffers`, `RequestMixedArgs` in [`rpcProtocol.ts`](https://github.com/microsoft/vscode/blob/main/src/vs/workbench/services/extensions/common/rpcProtocol.ts)) if this ever matters. kotlinx CBOR has byte strings via `@ByteString` but is still experimental ([formats.md](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/formats.md)) | Native `bytes` | Native `byte[]` |
| Transport | Inherited pipes. Nothing listens, so there's no port and no auth token. Works under any OS sandbox that allows inherited file descriptors | HTTP/2 over loopback TCP. grpc-java's Unix-socket transport needs Netty epoll, so it's Linux only (`UdsNettyChannelProvider.isAvailable()` checks `EPOLL_DOMAIN_CLIENT_CHANNEL_TYPE`, [source](https://github.com/grpc/grpc-java/blob/master/netty/src/main/java/io/grpc/netty/UdsNettyChannelProvider.java)). Loopback TCP is reachable by every local process, so it needs a token, and it breaks if a later network policy puts the script in a network namespace | TCP plus the RMI registry. Same exposure as gRPC |
| Security of the host decoder | kotlinx-serialization only decodes declared `@Serializable` types: no reflection, no gadget chains | Protobuf: declared types only | **Java serialization of attacker-controlled input.** "An application that accepts untrusted data and deserializes it is vulnerable to attacks" ([Oracle, serialization filtering](https://docs.oracle.com/en/java/javase/21/core/serialization-filtering1.html)). [JEP 290](https://openjdk.org/jeps/290) filters only mitigate this. RMI Activation was removed in [JEP 407](https://openjdk.org/jeps/407) |
| Footprint inside the script JVM | kotlinx-serialization-json plus coroutines, which can be relocated in the bootstrap jar | grpc-netty, Netty, protobuf and Guava. Scripts already bundle their own OkHttp, Ktor and Netty, so this brings back the collisions #150 is about | JDK only (`java.rmi`, not in the current jlink module list) |

**Verdict:** JSON-RPC over stdio. gRPC's real advantages are streaming and deadlines, and the SDK has no streaming API today. Its costs are the client → server-only model, which fights the `shouldFinish` reverse call, plus its dependency weight and the Linux-only Unix sockets. RMI fails on security alone.

### Prior art

- **LSP**: JSON-RPC with `Content-Length` header framing ("The length of the content part in bytes. This header is required."). Both peers issue requests, cancellation goes through `$/cancelRequest`, and partial results through `$/progress`. Its usual transport is the server's stdio. This is the closest model to ours.
- **LSP4J** (Eclipse, JVM): an off-the-shelf bidirectional JSON-RPC library. Calling `cancel(true)` on a returned `CompletableFuture` sends `$/cancelRequest` (`RemoteEndpoint.java`). It uses Gson and `CompletableFuture`, not coroutines. A coroutine-native version written in-house is a few hundred lines and avoids reflective Gson decoding in the host. LSP4J is still a good reference.
- **VS Code extension host**: extensions run in a separate host so that "misbehaving extensions should not impact the user experience" ([docs](https://code.visualstudio.com/api/advanced-topics/extension-host)). Its `RPCProtocol` has explicit `Cancel` messages, JSON args with out-of-band binary buffers, and an `Acknowledged` / unresponsive check, which is a ready-made pattern for hang detection.
- **Gradle daemon**: "Communication between the client and the Daemon happens via a local socket connection." It reuses a daemon only when the Java home, version and JVM args match exactly ([docs](https://docs.gradle.org/current/userguide/gradle_daemon.html)). That matters if script JVMs are ever pooled: the pool key has to include the JVM args.

### Protocol sketch (for the implementation tickets)

- Framing: `Content-Length: N\r\n\r\n` followed by a JSON body. The host enforces a maximum N (for example 32 MB), because the peer is untrusted.
- Messages: JSON-RPC 2.0 `request`, `response`, `notification` and `error`, plus `$/cancelRequest {id}`.
- Callbacks: a lambda crosses as `{"callbackId": n}`. The receiving side calls `callback/invoke {callbackId, args}` back on the peer. Callbacks are released when the owning call completes.
- Errors: map to a closed set of SDK exception names (`UserInteractionCanceledException`, `LlmException`, `StartScriptException`, `WebResourceException`, generic) and never to arbitrary class names. Logger `Throwable`s cross as `{type, message, stackTrace: String}`.
- Child bootstrap: call `System.setOut`/`setErr` to point at the logger **before** loading script classes, and keep the original `FileDescriptor.out` stream private for the protocol. A hostile script can still write raw frames to fd 1, but it can only speak as itself, so it gains nothing.
- Ultimate cancel and hang handling: `Process.destroyForcibly()` from the host.
- Windows: `ProcessBuilder` spawns with `CREATE_NO_WINDOW` unless stdio is inherited ([`ProcessImpl_md.c`](https://github.com/openjdk/jdk21u/blob/master/src/java.base/windows/native/libjava/ProcessImpl_md.c) l.320–328). With piped stdio, `java.exe` therefore doesn't pop up a console, so `javaw.exe` isn't needed.

## Part 2: shipping a launchable JVM

### Facts

- jpackage's default jlink options are `--strip-native-commands --strip-debug --no-man-pages --no-header-files` ([jpackage man, `--jlink-options`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html)).
- Compose doesn't use jpackage's internal jlink. It runs its own `createRuntimeImage` (`AbstractJLinkTask`) and passes the result as `--runtime-image`. In v1.12.2, `stripNativeCommands` is an `internal` property with default `true` and there's no DSL for it ([AbstractJLinkTask.kt@v1.12.2](https://github.com/JetBrains/compose-multiplatform/blob/v1.12.2/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/desktop/application/tasks/AbstractJLinkTask.kt)). On `master`, it's set to `false` only for the new AOT/CDS mode ("`java` is needed to generate the JRE CDS archive"). That isn't a public switch either.
- Compose's `configureJvmApplication.kt` still has `// todo: multiple launchers`. There's no launcher DSL, but `AbstractJvmToolOperationTask.freeArgs` lets you pass raw jpackage flags such as `--add-launcher`.
- jpackage `--add-launcher name=props` accepts the keys `main-jar`, `main-class`, `arguments`, `java-options` and a few others, all fixed at package time.

### Measured (JDK 21.0.10, macOS arm64, our module list plus Compose's defaults)

```
jlink ... --strip-native-commands  → 78 MB, no bin/
jlink ... (without the flag)       → 79 MB, bin/ = java (70 KB), keytool, jwebserver
cp rt-full/bin/java rt/bin/java; rt/bin/java -version   → works
otool -L bin/java → @rpath/libjli.dylib, which stays in lib/ even when stripped
```

### Options

| Option | Size | Child classpath and JVM args | Effort | Notes |
|---|---|---|---|---|
| **A. Keep `bin/java` in the app runtime** | +70 KB | Free: `java -Xmx… -cp bootstrap.jar …` per sandbox | `tasks.named("createRuntimeImage") { doLast { copy bin/java (bin/java.exe on Windows) from javaHome } }` for main and release. Alternatively set the internal `stripNativeCommands` by reflection, which is brittle | Same JDK and same modules as the client, so scripts get exactly the runtime they get today in-process. That's the right compatibility for legacy jars |
| B. Second jlink runtime in `appResources` | +~78 MB per OS | Free | Custom `Exec` jlink task. Compose's `createRuntimeImage` re-signing only walks `Contents/runtime`, so on macOS the second runtime has to be signed by hand | Only worth it if scripts need a different module set or JDK than the client. Nothing requires that today |
| C. jpackage secondary launcher (`--add-launcher` via `freeArgs`) | ~0 | **Fixed** at package time: the classpath is the app's jars (the client's own jars sit on the script JVM's classpath) and `java-options` are fixed, so per-sandbox `-Xmx` would need `JAVA_TOOL_OPTIONS` | Untested with Compose's two-step `createDistributable` → package flow and its re-signing | Main class must be ProGuard-kept. Inflexible, and couples the script host to the client's classpath |

**Recommendation: A.** It's the smallest diff and the smallest artifact, and it gives full control over the child's command line. Windows needs `bin/java.exe` (plus `jli.dll`, which stays in the runtime because the jpackage launcher uses it). On Linux the AppImage built from `createReleaseDistributable` will contain `bin/java` automatically once the runtime has it. A spike should confirm both on CI runners.

### macOS signing and notarization consequences

- The notary service requires every distributed executable to be code-signed with a Developer ID certificate, with "the Hardened Runtime capability for your app and command line targets" and a secure timestamp ([Notarizing macOS software](https://developer.apple.com/documentation/security/notarizing-macos-software-before-distribution)). A `bin/java` inside the bundle is one of those executables.
- Compose already handles signing. `AbstractJPackageTask.modifyRuntimeOnMacOsIfNeeded()` walks `Contents/runtime` and signs **every executable or dylib** with `--options runtime` and `macRuntimeEntitlementsFile`, then re-signs the runtime and the `.app` ([source@v1.12.2](https://github.com/JetBrains/compose-multiplatform/blob/v1.12.2/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/desktop/application/tasks/AbstractJPackageTask.kt) l.646–671). A copied `bin/java` is therefore signed and notarizable with no extra steps.
- **Gap to fix:** `cereal-client.gradle.kts` sets `entitlementsFile` but not `runtimeEntitlementsFile`, so runtime binaries are signed without entitlements. That's harmless today, because `libjvm.dylib` loads into the main launcher process, which has `cereal-client/entitlements.plist`. It breaks for a separately exec'd `bin/java`: entitlements belong to the executable's own signature, so the child gets none. A HotSpot child under the hardened runtime needs at least `com.apple.security.cs.allow-jit`, and in practice it needs the same JVM keys as our `entitlements.plist` (`allow-unsigned-executable-memory`, and `disable-library-validation` because scripts load native libs). Set `runtimeEntitlementsFile` to a JVM-only plist. This is also the place to apply per-child restrictions later, for example leaving out `network.server` or `debugger`.
- **App Sandbox:** `entitlements.plist` doesn't set `com.apple.security.app-sandbox`, so the client isn't App-Sandboxed today. Apple's documented route for a sandboxed helper is `app-sandbox` plus `com.apple.security.inherit`, with the helper placed in `Contents/MacOS`, and `get-task-allow` is incompatible with `inherit` ([Embedding a helper tool in a sandboxed app](https://developer.apple.com/documentation/xcode/embedding-a-helper-tool-in-a-sandboxed-app)). That ties the child's sandbox to the parent's. If the mechanism ticket picks App Sandbox for scripts, `bin/java` needs its own sandbox entitlements, and our `/Cereal/` home-relative exception has to be revisited. Choosing the macOS mechanism is out of scope here. The point for this ticket is that a child binary's entitlements are set at build time through `runtimeEntitlementsFile`, so whatever the mechanism needs is a signing-config change, not a packaging change.
- Windows and Linux: there's no equivalent per-executable entitlement gate for a child process. Authenticode signing of `java.exe` is optional, and the runtime's binaries are already signed by the JDK vendor.

## Open questions and spikes

1. Spike: add the `doLast` copy, build a signed and notarized DMG on CI, and check that `Contents/runtime/Contents/Home/bin/java -version` runs from the installed app with the runtime entitlements set. Do the same for the MSI/EXE and the AppImage.
2. Check whether the `smokeTest`/ProGuard release flow needs the child bootstrap jar kept out of obfuscation. It probably should be a separate, unobfuscated jar.
3. Measure base64 cost on real artifacts and LLM images. Add VS Code-style out-of-band binary frames only if it shows up.

## Sources

- JDK 21 [jpackage](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html) and [jlink](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jlink.html) man pages; [JEP 380](https://openjdk.org/jeps/380) (Unix-domain sockets, incl. Windows 10); [JEP 290](https://openjdk.org/jeps/290); [JEP 407](https://openjdk.org/jeps/407); [serialization filtering](https://docs.oracle.com/en/java/javase/21/core/serialization-filtering1.html); [`ProcessImpl_md.c`](https://github.com/openjdk/jdk21u/blob/master/src/java.base/windows/native/libjava/ProcessImpl_md.c)
- Compose Multiplatform Gradle plugin v1.12.2: `AbstractJLinkTask.kt`, `AbstractJPackageTask.kt`, `AbstractJvmToolOperationTask.kt`, `configureJvmApplication.kt`, `MacSigner.kt`, `dsl/PlatformSettings.kt`
- Apple: [Notarizing macOS software before distribution](https://developer.apple.com/documentation/security/notarizing-macos-software-before-distribution), [Resolving common notarization issues](https://developer.apple.com/documentation/security/resolving-common-notarization-issues), [Embedding a helper tool in a sandboxed app](https://developer.apple.com/documentation/xcode/embedding-a-helper-tool-in-a-sandboxed-app)
- [LSP 3.17 spec](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/), [LSP4J](https://github.com/eclipse-lsp4j/lsp4j), [VS Code extension host](https://code.visualstudio.com/api/advanced-topics/extension-host) and [`rpcProtocol.ts`](https://github.com/microsoft/vscode/blob/main/src/vs/workbench/services/extensions/common/rpcProtocol.ts), [Gradle daemon](https://docs.gradle.org/current/userguide/gradle_daemon.html)
- [gRPC cancellation](https://grpc.io/docs/guides/cancellation/), [gRPC Kotlin basics](https://grpc.io/docs/languages/kotlin/basics/), [grpc-java `UdsNettyChannelProvider`](https://github.com/grpc/grpc-java/blob/master/netty/src/main/java/io/grpc/netty/UdsNettyChannelProvider.java), [kotlinx-serialization formats](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/formats.md)
- Repo: `cereal-client/cereal-client.gradle.kts` (nativeDistributions, l.232–310), `cereal-client/entitlements.plist`; sdk `origin/HEAD` `Script.kt`, `ComponentProvider.kt`, `UserInteractionComponent.kt`, `ScriptLauncherComponent.kt`, `ArtifactComponent.kt`, `LlmRequest.kt`
