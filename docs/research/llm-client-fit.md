# Research: which LLM client fits inside a marketplace script JAR?

Ticket: #50 (map #47). Background: `docs/research/ai-automation-transformation.md` §2.2, §2.4, §5 on
branch `research/ai-automation-transformation`. This note reports facts and trade-offs. The choice
itself is ticket #56.

Checked 2026-10-08. Versions: `com.anthropic:anthropic-java:2.70.0`, `com.openai:openai-java:4.78.1`
(latest `<release>` in Maven Central `maven-metadata.xml`), OkHttp 5.4.0 and kotlinx-serialization
1.11.0 (the versions in this repo's `gradle/libs.versions.toml`), Ollama `main` @ `31547a6`
(2026-10-03, latest release v0.40.1).

## Answer

Only raw HTTP (OkHttp + kotlinx-serialization) fits as-is. **anthropic-java** and **openai-java** each
pull in Jackson and kotlin-reflect. After ProGuard they still add **12–14 MB** and **30–33 MB** to a
script, against **0.64 MB** for raw HTTP. Both also **crash inside Cereal's script class loader** until
`kotlin.reflect` is served by the host. One OpenAI-compatible client covers Ollama, but **not with
strict tools**. Ollama drops `strict`, and Anthropic's OpenAI-compatible layer ignores `strict`,
`response_format` and prompt caching.

## How a script JAR gets its dependencies today (from source)

- `gradle/proguard.gradle` passes **only the script's own `release.jar`** to `injars`. Dependencies go
  in as `libraryjars`, so they are not bundled. A script that uses a third-party library has to shade
  it into the JAR itself.
- `ScriptClassLoader.kt` loads `com.cereal.sdk` and `kotlinx.coroutines` from the parent first. For
  everything else it tries the script JAR first. If a class is missing there and is not `com.cereal.*`,
  it falls back to the parent.
- The host release build is ProGuard-obfuscated with `-repackageclasses ''` (`cereal-client.pro`).
  Only some names are kept stable: `com.cereal.sdk.**`, `kotlinx.coroutines.**`, `okio.**`,
  `kotlinx.atomicfu.**` and `kotlin.Metadata` (`cereal-client.pro:79,114,136,150,151`). The host's
  `okhttp3`, `kotlinx.serialization`, `io.ktor` and `kotlin.reflect` are not kept by name. So **a
  script can't rely on the host's OkHttp or kotlinx-serialization** through the parent fallback
  unless the host adds keep rules. Each option below assumes the script bundles its own client stack.
- The loader caches every decrypted class's bytes in a `HashMap` (`classCache`), so a 12–30 MB SDK
  also costs that much heap per loaded script. **[INFERENCE from source; not measured]**

## Size and transitive dependencies

The full runtime closure was resolved with Gradle. The core JAR sizes come from the Gradle module
metadata on Maven Central.

| | anthropic-java 2.70.0 | openai-java 4.78.1 | raw: OkHttp 5.4.0 + kotlinx-serialization-json 1.11.0 |
|---|---|---|---|
| Core JAR | `anthropic-java-core` **37.2 MB**, 15,362 classes | `openai-java-core` **68.0 MB**, 33,800 classes | n/a |
| Runtime closure, unshrunk | **46.5 MB**, 24 JARs | **77.5 MB**, 24 JARs | **3.8 MB**, 6 JARs |
| JSON stack | Jackson 2.19.4 (databind, core, annotations, module-kotlin, jdk8, jsr310) | Jackson 2.18.9 (same set) | kotlinx-serialization (the stack Cereal already uses) |
| Other notable deps | **kotlin-reflect** (3.2 MB), OkHttp **4.12.0**, victools jsonschema-generator + swagger-annotations, standardwebhooks, slf4j-api | the same, apart from standardwebhooks | okio 3.17 |
| **After ProGuard** (measured, see below) | **14.1 MB** with kotlin-reflect bundled; **12.1 MB** with it from the host | **33.0 MB**; **30.4 MB** | **0.64 MB** |

Both vendor SDKs depend on OkHttp 4.12, while the host is on 5.4.0. Bundled together with a
script's own copy, that makes a third OkHttp copy in the process.

## ProGuard

- **anthropic-java** ships `META-INF/proguard/anthropic-java-core.pro`. It covers Jackson attributes,
  `kotlin.Metadata`, the kotlin-reflect `ServiceLoader` interfaces, enums and annotated members. Its
  keeps use `allowobfuscation`, and an `-if` keeps every `com.anthropic.**` class that has an annotated
  constructor. The file's own comment notes the `-if` condition always holds under ProGuard, unlike
  R8. That rule is why the shrunk JAR stays at about 14 MB.
- **openai-java** ships `META-INF/proguard/openai-java-core.pro`, which is stricter. It has
  `-keep class kotlin.reflect.** { *; }` and full `-keep` on every `@JsonSerialize`/`@JsonDeserialize`
  `com.openai.**` class. Little shrinks and names stay unobfuscated (61 MB uncompressed `com/openai`
  left after shrinking).
- **Raw** needs only the kotlinx-serialization rules this repo already has
  (`cereal-client/proguard-rules/kotlinx-serialization.pro`).
- **Measured gap in anthropic-java's rules:** `kotlin.reflect.jvm.internal.ReflectionFactoryImpl` is
  not kept. Once shrunk and obfuscated, the stdlib can't find it by name and falls back to
  `ClassReference`, and the first call fails with `ClassCastException: kotlin.jvm.internal.ClassReference
  cannot be cast to kotlin.reflect.jvm.internal.<obf>`. Adding `-keep class
  kotlin.reflect.jvm.internal.ReflectionFactoryImpl { *; }` fixed it when run standalone.

### The class-loader blocker (measured)

A copy of `ScriptClassLoader`'s delegation rules was used for this test. The script JAR was loaded
child-first, with `kotlin-stdlib` 2.3.21 on the parent classpath, as in the host. The shrunk JARs were
run against a local fake server that returns canned Messages and Chat Completions responses:

| Script JAR | Host parent has stdlib only | Host parent has stdlib + kotlin-reflect 2.3.21 | `kotlin.reflect` parent-first and not bundled |
|---|---|---|---|
| anthropic-java | `ClassCastException` (`ClassReference` → script's KClassImpl) | `ClassCastException` (host `KClassImpl` → script's) | **works** (tool call parsed) |
| openai-java | `ClassCastException` (same) | `ClassCastException` (`KClassImpl` cannot be cast to `KClassImpl`, two loaders) | **works** |
| raw | works | works | works |

The cause is that kotlin-stdlib's `Reflection` looks up the reflection factory from the **parent**
loader. jackson-module-kotlin inside the script then casts the result to the script's own copy of
kotlin-reflect. Either vendor SDK therefore needs two host changes. First, add `"kotlin.reflect"` to
`parentClassloaderPackages`. Second, keep `kotlin.reflect.**` by name in the host release build. The
host already depends on `kotlin-reflect`; it is in `libs.versions.toml`. The script then has to treat
kotlin-reflect as `compileOnly`. **[UNVERIFIED]:** this was not run against a real obfuscated
`runRelease` host. Also untested: jackson-module-kotlin 2.18/2.19 against host kotlin-reflect 2.3.21
beyond the single request/response round trip above, which did pass for both SDKs.

## Tool use with strict schemas

- **anthropic-java:** `Tool.builder()...strict(true)` exists, and
  `.putAdditionalProperty("additionalProperties", JsonValue.from(false))` puts the schema keyword in
  the body. Captured on the wire after shrinking: `"strict":true`, `"additionalProperties":false` and
  `"cache_control":{"type":"ephemeral"}` were all sent.
  Structured outputs use `OutputConfig` / `JsonOutputFormat` or `.outputConfig(Class)`. The automatic
  loop, `BetaToolRunner`, builds tool schemas reflectively from Jackson-annotated classes through the
  victools generator. That means more reflection to keep under ProGuard. A manual loop with
  `Tool.builder()` avoids it.
  Source: `claude-api` skill, Java reference (tool-use.md, README), cross-checked against classes in
  the 2.70.0 JAR (`BetaToolRunner`, `StructuredMessageCreateParams`, `CacheControlEphemeral`,
  `OutputConfig`).
- **openai-java:** `FunctionDefinition.builder().strict(true)` with free-form `FunctionParameters`.
  Captured on the wire: `"strict":true` and `"additionalProperties":false` both sent.
  `ResponseFormatJsonSchema` exists for structured output.
- **Raw:** you write the schema JSON yourself. The Claude API requires `additionalProperties: false`
  and a non-recursive schema (background doc §2.2). Validating the arguments, or trusting `strict`, is
  the script's job.
- Current Claude models (Opus 5.5, Sonnet 5.5) return a 400 on forced `tool_choice` `any`/`tool`. Use
  `auto` with `strict: true`, or structured outputs. This applies to all three options (`claude-api`
  skill).

## Prompt caching

- **anthropic-java:** `CacheControlEphemeral` (TTL 5m or 1h) can go on system blocks and tools, or at
  the top level of `MessageCreateParams`. Verified in the request body above.
- **Raw:** you add `cache_control` to the JSON yourself.
- **openai-java → Claude** through Anthropic's OpenAI-compatible endpoint
  (`https://api.anthropic.com/v1/`): prompt caching is **not supported**, `strict` is **ignored**,
  `response_format` is **ignored**, and thinking isn't returned. The docs describe the layer as
  intended "to test and compare model capabilities" and not production-ready for most uses
  (https://platform.claude.com/docs/en/cli-sdks-libraries/libraries/openai-sdk).
  **Result: one openai-java client can't serve Claude with strict tools and caching.**
- Ollama has no `cache_control`; its prefix/KV reuse is internal. **[not researched further]**

## OpenAI-compatible local endpoints (Ollama)

From `openai/openai.go` and `api/types.go` on Ollama `main`:

- **`response_format.json_schema` is accepted.** `type: "json_schema"` forwards
  `json_schema.schema` unchanged as the native `format` (structured output). `json_object` maps to
  `"json"`. The `name` and `strict` fields of `json_schema` are not read (`JsonSchema` has only
  `Schema`). This closes the **[UNVERIFIED]** item in background §2.4 and §5.
- **`tools` is accepted** (`ChatCompletionRequest.Tools []api.Tool`), but:
  - `api.ToolFunction` has only `name`, `description` and `parameters`, so **`strict` is dropped**.
  - `ToolFunctionParameters` keeps only `type`, `$defs`, `items`, `required` and `properties`, and
    `ToolProperty` keeps `anyOf`, `type`, `items`, `description`, `enum`, `properties` and `required`.
    So **`additionalProperties` is dropped**.
  - There is **no `tool_choice` field**, so it is ignored.
  - Tool arguments are therefore not schema-guaranteed on Ollama, and the client has to validate them.
- llama.cpp server: the background doc §2.4 already covers it (`--jinja` tools, `json_schema` and
  `response_format`, GBNF). It was not re-checked here.

## Comparison

| | anthropic-java | openai-java | raw OkHttp + kotlinx-serialization |
|---|---|---|---|
| Added script size (shrunk) | ~12–14 MB | ~30–33 MB | ~0.6 MB |
| Second JSON stack | yes (Jackson) | yes (Jackson) | no |
| Works in today's `ScriptClassLoader` | **no**, needs host `kotlin.reflect` delegation + keep | **no**, same | **yes** |
| Bundled ProGuard rules | yes; needs one extra keep | yes; keeps almost everything | not needed beyond existing |
| Claude strict tools / structured output | typed | only via compat layer, where both are ignored | hand-written JSON |
| Claude prompt caching | typed | not supported via compat layer | hand-written JSON |
| Ollama / llama.cpp / OpenAI | no | yes (Chat Completions + `baseUrl`) | yes, if you also write an OpenAI-shaped request |
| Effort to cover both wire formats | + a second client for local | + a second client for Claude | two small request/response models; the agent loop is yours |
| Agent loop helper | `BetaToolRunner` (reflective, beta) | none in Java | none |

## Unverified / gaps

- Neither vendor SDK was run inside a real `runRelease` host. The class-loader result comes from a
  loader that copies `ScriptClassLoader`'s delegation rules.
- Streaming (SSE) was not measured for any option.
- Shrunk sizes assume `kotlin-stdlib` comes from the host. They depend on what the script actually
  calls; the minimal programs each made one create call with one tool.
- Whether the Cereal SDK should provide an LLM client itself (host-side, so scripts get a stable API
  with no bundled deps) is outside this ticket. It would remove the size and class-loader problems for
  every script. **[INFERENCE]**

## Method

A throwaway Gradle project (outside the repo) held three minimal Kotlin `main()` scripts, one per
option, each making one create call with one strict tool. The scripts were shrunk and obfuscated with
ProGuard 7.8.2, the same version `gradle/proguard.gradle` uses. The SDKs' bundled rules were included
and `kotlin-stdlib` was a library JAR. The scripts ran against a local fake HTTP server, both plainly
and through a copy of `ScriptClassLoader`'s delegation rules.
