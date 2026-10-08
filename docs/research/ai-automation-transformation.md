# Research: making Cereal the easy way to use AI for automation

Date: 2026-10-08. Question: how can the Cereal desktop client become something that makes it easy
for users to use AI for automation?

Paths in this document are relative to the repo root. `client/` abbreviates
`cereal-client/src/main/java/com/cereal/client/`. External claims cite official docs, specs or
official repos. **[UNVERIFIED]** marks claims that no primary source confirmed. **[INFERENCE]** marks
my own reasoning.

---

## 1. What Cereal is today (from source)

### 1.1 The automation model

- **A script is a compiled Kotlin JAR built against the closed-source Cereal SDK.**
  - The SDK is a binary Maven dependency, `com.cereal-automation:cereal-sdk:1.12.0`
    (`gradle/libs.versions.toml:43,118`; `README.md`, the "Cereal SDK ... closed-source" paragraph).
  - A script implements `Script<T : ScriptConfiguration>` with three suspend hooks: `onStart`,
    `execute` and `onFinish`. This comes from `javap` on `cereal-sdk-1.12.0.jar`, class `com.cereal.sdk.Script`.
  - `execute` returns `ExecutionResult.Success`, `Error` or `Loop(delay)`. The host keeps calling
    `execute` until it gets something other than `Loop` (`client/application/task/TaskExecutor.kt:77-104`).
- **A script declares its configuration as an annotated Kotlin interface.**
  - Each accessor carries `@ScriptConfigurationItem(keyName, name, description, valuePerTask, stateModifier, ...)`
    (`cereal-script-sample/src/main/java/com/cereal/script/sample/SampleConfiguration.kt:15-207`).
  - The host reads the interface by reflection
    (`client/infrastructure/data/datasource/filesystem/ScriptConfigurationDefinitionBuilder.kt:113`).
  - At runtime the host serves values through a dynamic proxy (`TaskExecutor.kt:167-183`,
    `ConfigInvocationHandler`).
  - The closed set of supported types is Boolean, String, Secret, Int, Float, Double, typed List
    rows, Enum, Proxy, ProxyGroup and Grouped datasets
    (`client/domain/model/script/configuration/ScriptConfigurationItemDefinition.kt:21-61`).
- **The SDK gives scripts seven host channels and nothing else.**
  - `logger`, `license`, `notification`, `preference`, `scriptLauncher`, `userInteraction` and
    `artifact` (`client/infrastructure/sdkcomponent/ComponentProviderImpl.kt:12-34`).
  - **There is no browser, HTTP or AI component.** The full class list of `cereal-sdk-1.12.0.jar` has no
    browser types, so scripts that automate websites bring their own libraries. **[INFERENCE]** from the
    jar listing.
- **A user runs an automation in five steps:**
  1. Subscribe in the marketplace (`client/domain/provider/MarketplaceProvider.kt:9-42`).
  2. The script syncs down as an encrypted JAR (`client/application/script/ScriptSyncManager.kt:74-100`;
     `ScriptInstallProvider.kt:12-22`).
  3. Fill in the configuration form.
  4. Start it. `StartScriptInteractor` validates the config, the license entitlement, capacity and
     concurrency, then creates tasks (`client/application/interactor/script/StartScriptInteractor.kt:34-85`).
  5. Watch status, notifications (Discord, Telegram, email) and artifacts.
- **There is no local sideloading.**
  - Even the "development scripts" setting only switches the sync to the team's *draft marketplace
    releases* (`ScriptSyncManager.kt:80-97`; `client/application/interactor/settings/developers/SetDevelopmentScriptsInteractor.kt:13-29`).
  - JARs on disk are encrypted per user, and the class loader decrypts them
    (`client/infrastructure/data/datasource/filesystem/reader/ScriptClassLoader.kt:66-80`).
- **Scripts run in-process with no sandbox.**
  - Each runs on its own thread inside the client JVM (`TaskExecutor.kt:44-47`).
  - The class loader delegates `com.cereal.sdk` and `kotlinx.coroutines` to the host. Any other
    non-`com.cereal` class falls back to the parent loader (`ScriptClassLoader.kt:16-54`).
  - So a script has whatever the client JVM has: filesystem and network.

### 1.2 Where the browser is used today

- kdriver `dev.kdriver:core:0.5.11` (`gradle/libs.versions.toml:36,115`) is used only by the **host**.
  - The checkout flow uses it in `client/infrastructure/provider/CheckoutProviderImpl.kt:36-72`.
  - The `userInteraction().showUrl/showHtml` window uses it in
    `client/presentation/tasks/UserInteractionWindow.kt:110-200`. That window opens a visible Chrome,
    sets headers, and resolves when a request matches the script's predicate.
- The second use reaches kdriver directly from the **presentation** layer. That breaks the layering in
  `CLAUDE.md` ("Never violate layer boundaries"). Any AI browser work should go through a domain
  provider rather than copy this pattern.

### 1.3 Extension points where AI could plug in

| Seam | Where | Why it matters |
|---|---|---|
| Typed config schema | `ScriptConfigurationItemDefinition.kt:21-61`; validator `client/application/interactor/script/validator/ScriptConfigurationValuesValidator.kt` | Maps one-to-one to JSON Schema, so an LLM can fill a task config with structured outputs and the existing validator checks the result |
| Script and task interactors | `StartScriptInteractor`, `StartTaskInteractor`, `StopTaskInteractor`, `ObserveTasksInteractor`, `GetScriptsInteractor`, `GetScriptConfigDefinitionInteractor` (all in `client/application/interactor/{script,task}/`) | A ready-made use-case surface to expose as LLM or MCP tools |
| `TaskManager` | `client/application/task/TaskManager.kt:78,105,175` (create, start, stop) | Lifecycle for any AI-driven task |
| SDK component pattern | ADR-0002 added `ArtifactComponent` as a new SDK channel (`docs/adr/0002-script-artifacts.md:61-80`) | Precedent for adding `provider.browser()` or `provider.ai()` |
| Secrets | `Secret` config type, masked and encrypted at rest (`CONTEXT.md` "Secret"; ADR-0001) | Bring-your-own API keys already have a safe home |
| User interaction | `UserInteractionComponent.requestInput/showUrl/showContinueButton` (SDK `javap`) | Human-in-the-loop approval for agent actions already exists |
| Feature flags | ADR-0006 (`docs/adr/0006-feature-flags-are-compile-time-debug-keyed.md`) | Gate AI features in debug builds first |
| Networking | OkHttp 5.4.0 and ktor-client-okhttp 3.5.1 (`gradle/libs.versions.toml:19,35,114`; `cereal-client/cereal-client.gradle.kts:140`) | LLM HTTP clients need no new transport. An MCP *server* would need a Ktor server engine |

### 1.4 Product constraints that shape the answer

- **Privacy is a product position.** ADR-0009 removed all analytics because the audience is a
  "privacy-conscious automation audience" (`docs/adr/0009-no-product-analytics.md:21-24`). The README
  pitches auditability (`README.md`, "Why this is public"). AI features should send nothing to Cereal
  servers and should offer bring-your-own-key and local models.
- **Distribution is marketplace-only, and scripts are encrypted and license-checked.** Any "generate a
  script" feature runs into the build → upload → sign → sync pipeline (§1.1).

---

## 2. AI building blocks (primary sources)

### 2.1 Model Context Protocol

- **The latest spec is 2026-07-28** (https://modelcontextprotocol.io/specification/latest).
  - It made the protocol **stateless**: no `initialize`, no `Mcp-Session-Id`, and a new `server/discover`.
  - State across calls uses explicit handles passed as tool arguments.
  - Sampling, Roots and Logging are deprecated, and server→client requests became "multi round-trip
    requests" (https://modelcontextprotocol.io/specification/2026-07-28/changelog).
- **Tools** (https://modelcontextprotocol.io/specification/2026-07-28/server/tools):
  - Each has a JSON Schema `inputSchema`, an optional `outputSchema` and `structuredContent`, and `isError`.
  - Annotations are `readOnlyHint`, `destructiveHint`, `idempotentHint` and `openWorldHint`. Clients must
    treat annotations as untrusted.
  - The spec says a human SHOULD be able to deny tool calls.
- **Transports** are stdio and Streamable HTTP (https://modelcontextprotocol.io/specification/2026-07-28/basic/transports).
  OAuth 2.1 authorization applies to HTTP only. stdio servers take credentials from the environment
  (https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization).
- **Official Kotlin SDK**, `io.modelcontextprotocol:kotlin-sdk`:
  - Latest release is **0.15.0 (2026-07-28)**, still pre-1.0. I checked this with `gh api .../releases/latest`.
  - It covers both client and server, with stdio, SSE, Streamable HTTP and WebSocket transports.
  - It targets JVM 11 and is built on Ktor 3.5.x. It does not bring a Ktor engine, so you add one
    (https://github.com/modelcontextprotocol/kotlin-sdk).
  - **Gap:** `main` still declares protocol `2025-11-25` and does not yet implement the stateless
    2026-07-28 spec (`kotlin-sdk-core/.../types/common.kt` in that repo).
- **Wiring it into Claude Code:**
  - `claude mcp add --transport http cereal http://localhost:PORT/mcp`, or stdio via
    `claude mcp add --transport stdio <name> -- <cmd>` (https://code.claude.com/docs/en/mcp).
  - MCPB desktop bundles support only `node`, `python` and `binary` server types
    (https://github.com/modelcontextprotocol/mcpb).
  - The MCP Registry is still in preview and has no Maven package type
    (https://github.com/modelcontextprotocol/registry).
- **Hosted connectors can't reach local servers.** Anthropic's MCP connector needs a publicly reachable
  HTTP server and says stdio is not supported
  (https://platform.claude.com/docs/en/agents-and-tools/mcp-connector). OpenAI's remote MCP tool needs a
  public URL or its tunnel (https://developers.openai.com/api/docs/guides/tools-connectors-mcp).
  **Implication:** if Cereal runs the agent loop itself, its local tools should be client-side tools, not MCP.

### 2.2 Claude API, and OpenAI for comparison

- **Claude models and pricing** (https://platform.claude.com/docs/en/about-claude/models/overview):
  - `claude-opus-5-5` costs $4 / $20 per million tokens and is the recommended default.
  - `claude-sonnet-5-5` costs $2 / $10.
  - `claude-haiku-5-5` starts at $0.10 / $0.50.
- **Structured outputs are GA.** Use `output_config.format` with a `json_schema`, and `"strict": true`
  on tools. The schema may not be recursive, and `additionalProperties` must be `false`
  (https://platform.claude.com/docs/en/build-with-claude/structured-outputs).
- **Computer use** is `computer_toolset_20260801`, GA with no beta header
  (https://platform.claude.com/docs/en/agents-and-tools/tool-use/computer-use-tool). There is also a
  separate "browser use tool" (https://platform.claude.com/docs/en/agents-and-tools/tool-use/browser-use-tool).
  I did not read the browser tool's details. **[UNVERIFIED]**
- **The Agent SDK is Python and TypeScript only**, and it runs the Claude Code binary. For other
  languages the docs point to the CLI subprocess with `-p --output-format json`
  (https://code.claude.com/docs/en/agent-sdk/overview). **There is no JVM Agent SDK.**
- **Java SDK** `com.anthropic:anthropic-java:2.70.0` (2026-10-08, checked with `gh api`;
  https://github.com/anthropics/anthropic-sdk-java):
  - Requires Java 8+ and is written in Kotlin, but the public API is Java-style builders.
  - Uses OkHttp and **Jackson**. Cereal uses kotlinx-serialization, so this adds a second JSON stack.
  - Ships ProGuard keep rules and includes `BetaToolRunner`, an agent loop over your own tools.
  - Its MCP module is built on the MCP *Java* SDK, not the Kotlin SDK.
- **Prompt caching**: cache reads cost 0.1× base input or less, the default TTL is 5 minutes with an
  optional 1 hour, and the minimum cacheable prompt is 512 tokens on 5.x models
  (https://platform.claude.com/docs/en/build-with-claude/prompt-caching). This matters for agent loops
  that resend the same tool definitions and page snapshot every step.
- **OpenAI:**
  - Function calling with `strict` structured outputs, and the Responses API as the primary API
    (https://developers.openai.com/api/docs/guides/function-calling).
  - Agents SDK is Python and TypeScript only (https://developers.openai.com/api/docs/guides/agents/sdk).
  - JVM client: `com.openai:openai-java:4.78.1` (https://github.com/openai/openai-java).
  - A `computer` tool exists (https://developers.openai.com/api/docs/guides/tools-computer-use). Its GA
    status was not stated. **[UNVERIFIED]**
- **JVM takeaway:** on the JVM, Cereal writes its own agent loop on top of a vendor SDK or raw HTTP.
  It's a small loop (call model → run tools → append results → repeat), and `BetaToolRunner` covers it
  for Claude.

### 2.3 LLM-driven browser automation over CDP

- **kdriver** (https://github.com/cdpdriver/kdriver) is Apache-2.0.
  - Latest is **0.6.1**, checked against Maven Central `maven-metadata.xml`. Cereal is on 0.5.11.
  - `Tab` offers `find`, `findElementByText`, `select`, `querySelector`, `mouseClick`, `screenshotB64`,
    `getContent` and `rawEvaluate`. `Element` offers `click`, `sendKeys`, `insertText`, `clearInput`
    and more (`core/src/commonMain/kotlin/dev/kdriver/core/tab/Tab.kt`, `.../dom/Element.kt` on `main`).
  - `Tab` implements `CDP`, with typed bindings for 56 domains, including
    `Accessibility.getFullAXTree/queryAXTree` (`cdp/src/commonMain/kotlin/dev/kdriver/cdp/domain/Accessibility.kt`).
  - That means accessibility snapshots need **no new browser dependency**.
  - I read these from `main`, not the 0.5.11 tag. **[UNVERIFIED]** whether every method exists in 0.5.11.
- **CDP stability** (https://raw.githubusercontent.com/ChromeDevTools/devtools-protocol/master/json/browser_protocol.json):
  - The `Accessibility` domain (`getFullAXTree`, `queryAXTree`) and the `DOMSnapshot` domain are
    **experimental**.
  - `Page.captureScreenshot` and `Input.dispatchMouseEvent/dispatchKeyEvent` are stable.
  - Snapshot code will need to track Chrome versions.
- **Playwright MCP** (https://github.com/microsoft/playwright-mcp, Apache-2.0):
  - Works from an accessibility snapshot, not pixels. Action tools take an element `target` ref from
    the snapshot, and vision/coordinate tools are opt-in via `--caps=vision`.
  - The README now recommends its CLI over MCP for coding agents, to avoid loading verbose trees into context.
- **browser-use** (https://github.com/browser-use/browser-use, MIT):
  - Combines CDP `Accessibility.getFullAXTree` with `DOMSnapshot.captureSnapshot` (`browser_use/dom/service.py`)
    and shows the LLM an indexed list of interactive elements, plus an optional annotated screenshot
    (`browser_use/agent/system_prompts/system_prompt.md`).
  - Loop settings include `max_actions_per_step` and `max_failures`
    (https://docs.browser-use.com/customize/agent/all-parameters).
  - `rerun_history` replays recorded actions and re-matches elements (`agent/service.py`).
- **Stagehand** (https://github.com/browserbase/stagehand, MIT):
  - Primitives are `act`, `extract` (schema-validated), `observe` (returns concrete selectors you can
    feed to `act`) and `agent` (https://docs.stagehand.dev/v3/basics/act).
  - The local action cache replays actions with no LLM call (https://docs.stagehand.dev/v3/best-practices/caching).
  - "Self-healing" is described only in marketing terms. **[UNVERIFIED]** how it works.
- **The pattern they share:**
  1. Take a compact indexed snapshot of interactive elements.
  2. The LLM emits tool calls that reference the indexes.
  3. Execute, re-snapshot, and repeat.
  4. Cache the resolved actions and replay them without the LLM; call the LLM again only on a miss
     ("observe once, replay cheaply").

### 2.4 Local models

- **Ollama** (MIT; https://github.com/ollama/ollama/blob/main/docs/api.md):
  - `/api/chat` supports `tools` and `format` (a JSON schema, for structured output).
  - It also serves OpenAI-compatible `/v1/chat/completions` with tools and `response_format`
    (https://docs.ollama.com/api/openai-compatibility).
  - **[UNVERIFIED]** whether `response_format.json_schema` is accepted on the OpenAI endpoint.
- **llama.cpp server** (MIT; https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md):
  - Serves OpenAI-compatible `/v1/chat/completions` with function calling (pass `--jinja`).
  - Supports GBNF `grammar`, `json_schema` and `response_format` for constrained output.
- **Fit [INFERENCE]:** a single OpenAI-compatible provider implementation would cover Ollama,
  llama.cpp and OpenAI. That makes "local model" a URL setting, not a separate integration. Small
  local models are adequate for filling a config form; multi-step browser agents need frontier models.

### 2.5 What an LLM needs to write valid Cereal scripts

- **Today an LLM cannot learn the SDK from public sources.**
  - The SDK is closed-source (`README.md`), and the local Gradle cache holds only the binary jar, with
    no `-sources.jar` (checked `~/.gradle/caches/.../cereal-sdk/1.12.0/`).
  - An LLM would need: the `Script`/`ExecutionResult` contract; `@ScriptConfigurationItem` and the
    supported return types (§1.1); `StateModifier`; the `ComponentProvider` channels; the
    `manifest.json` format (`cereal-script-sample/src/main/resources/manifest.json`); and the build
    setup (`cereal-script-sample/cereal-script-sample.gradle.kts`: JVM 21, `-jvm-default=enable`, ProGuard).
  - `cereal-script-sample` is a good few-shot example, but it is a component test harness, not a
    realistic web-automation script.
- **Even valid generated code can't run on its own.**
  - Publishing it requires the marketplace upload, encryption and license pipeline (§1.1).
  - Running AI-written code in-process with full JVM rights (§1.1, "no sandbox") is a security
    problem for an app whose README promises auditability.
  - **[INFERENCE]** Generating scripts *inside the app* is the highest-risk option.

---

## 3. Transformation options

Effort: S is about 1–2 weeks, M about 3–6 weeks, L is a quarter or more, for one engineer.
**[INFERENCE]** These are rough estimates.

### A. Natural language → task configuration ("describe it, we fill the form")

- **User view:** on the configure screen, type "watch these three sneakers in size 42 under €150 and
  ping me on Discord". The form fills in, including list rows and enums, and the user reviews it and
  presses Start. This works for every existing marketplace script with no script changes.
- **What it takes:**
  - Domain: an `LlmProvider` interface. It's an adapter to an external service, so `*Provider` naming
    applies (`CLAUDE.md`, domain naming rule).
  - Application: `FillScriptConfigurationInteractor`. It builds a JSON Schema from
    `ScriptConfigurationItemDefinition` (names, descriptions, enum constants, list columns), calls the
    model with structured outputs, maps the result to `ConfigValue`s, and runs the existing
    `ScriptConfigurationValuesValidator`.
  - Infrastructure: one OpenAI-compatible HTTP implementation on the existing OkHttp and
    kotlinx-serialization stack (covers OpenAI, Ollama and llama.cpp), plus a Claude implementation
    (raw HTTP, or anthropic-java at the cost of Jackson). The API key is stored like other secrets.
  - Presentation: a prompt box on the configure screen. Gate it behind a FeatureFlag (ADR-0006).
- **Model choice:** schema limits rule out recursion (§2.2), but Cereal configs are flat lists, so that
  doesn't bite. Secrets and Proxy items should be excluded from the schema, so credentials never go to
  a model.
- **Effort and risk:** S–M, low risk. Nothing executes without user review, and the validator already
  exists.
- **Open questions:**
  - Are descriptions in existing marketplace scripts good enough for the model? Many sample
    descriptions are placeholders (`SampleConfiguration.kt:19`).
  - Is the API key per user or per Brand?

### B. Cereal as an MCP server ("drive Cereal from Claude/ChatGPT")

- **User view:** in Claude Desktop or Claude Code, ask "start my Nike monitor with the proxies from
  group X and tell me what it found". The assistant lists scripts, reads config schemas, creates and
  starts tasks, and reads status, notifications and artifacts.
- **What it takes:**
  - Infrastructure: an MCP server on Streamable HTTP bound to localhost, running inside the Compose
    app, which owns the task state. It uses the Kotlin SDK plus a Ktor server engine.
  - Application: no new logic. The tools wrap existing interactors (§1.3).
    - Read-only tools: `list_scripts`, `get_config_schema`, `list_tasks`, `get_task_status`,
      `list_notifications`, `get_artifact`.
    - Mutating tools: `create_task` and `start_task`/`stop_task`, marked `destructiveHint` and
      confirmed in-app, because annotations are untrusted (§2.1).
  - Security: localhost binding plus a per-install token. stdio is not an option because the GUI
    process owns the state. **[INFERENCE]**
- **Effort and risk:** M, medium risk.
  - The spec just changed to stateless and the Kotlin SDK lags it (§2.1), so expect churn.
  - Hosted connectors can't reach a local server (§2.1), so this serves desktop assistants only.
  - Packaging for one-click install needs an MCPB `binary` bundle.
- **Open questions:**
  - Should start/stop require in-app confirmation every time?
  - How should license and capacity errors surface to the assistant (`isError` text)?

### C. AI browser-agent task ("tell it the goal, it does the clicking")

- **User view:** a task whose configuration is a goal in plain language plus a URL, for example "log in
  and check whether my order shipped; if so notify me". The agent drives Chrome step by step, asks via
  `userInteraction` before irreversible actions, and reports through notifications and artifacts.
- **Two ways to build it:**
  - **C1, a first-party marketplace script.** No client change. Config: goal (String), API key
    (Secret), model URL, max steps. The script bundles its own LLM client and CDP library.
    - This reuses distribution, licensing, the scheduler, notifications and human-in-the-loop as they are.
    - Cost: every agent script ships its own browser stack. The in-process sandbox concern from §1.1
      applies, but the code is first-party.
  - **C2, a new SDK channel such as `provider.browser()` and/or `provider.agent()`**, following
    ADR-0002's precedent.
    - Domain `BrowserProvider`; an infra kdriver implementation that also absorbs the presentation-layer
      kdriver use in `UserInteractionWindow.kt`; and an agent loop in application.
    - The loop takes an AX-tree snapshot via kdriver's `Accessibility` bindings (§2.3), sends it to the
      LLM with tools, executes, and repeats.
    - It needs a closed-source SDK release and a client release in lockstep (`sdkVersion` gate,
      `StartScriptInteractor.kt:35-39`).
- **Effort and risk:**
  - C1 is M, medium risk: prompt injection from web pages, and cost per run.
  - C2 is L, higher risk: the SDK contract becomes permanent, and the AX and DOMSnapshot APIs are
    experimental in CDP (§2.3).
- **Open questions:**
  - Who pays for tokens: bring-your-own-key or Cereal-billed?
  - Is a looping agent affordable? Monitoring scripts run every few seconds via `ExecutionResult.Loop`
    (`TaskExecutor.kt:93-96`), so an LLM call on every loop is not viable. Use observe-once, replay-cached
    (§2.3).

### D. AI-assisted script authoring (NL → SDK script) for script developers

- **User view:** a script author asks Claude Code or Copilot "write a Cereal script that monitors X"
  and gets a buildable project.
- **What it takes:** no client change.
  1. Publish SDK reference material an LLM can use: a sources/KDoc jar, an `llms.txt`, or an agent
     skill that bundles the `Script` contract, config types, `manifest.json` schema, and Gradle setup (§2.5).
  2. Add a realistic web-automation sample next to `cereal-script-sample`.
  3. Optionally use B's MCP tools for "upload draft and run".
- **End-user generation inside the app is not recommended now.** It needs a compile pipeline, signing
  and upload, and it would execute unreviewed code in-process with full JVM rights (§2.5).
- **Effort and risk:** S for docs and skill, low risk. Generating scripts inside the app would be L,
  high risk.
- **Open question:** is the SDK team willing to publish sources or KDoc, given that the SDK is closed?

### E. Self-healing selectors and action caching

- **User view:** marketplace scripts keep working when a shop changes its HTML.
- **What it takes:** script selectors live inside closed scripts, so the host can only help if scripts
  ask it to find elements. That depends on C2 (`provider.browser().act("click add to cart")` with a
  cached selector and an LLM call only on a miss, the Stagehand/browser-use pattern in §2.3).
- **Effort and risk:** L, and it depends on C2. Stagehand's own self-healing mechanism is
  **[UNVERIFIED]**.

---

## 4. Recommendation: a sequenced path

1. **A — NL → task configuration.** Smallest valuable step. It works for the whole existing catalog,
   runs no new code, keeps a human review step, and supports local models for the privacy-minded
   audience. Ship it behind a FeatureFlag. Build `LlmProvider` once (OpenAI-compatible plus Claude) and
   later steps reuse it.
2. **D (docs and skill only)**, in parallel. It's cheap and grows the catalog by making AI coding
   assistants good at Cereal scripts, but it needs the SDK owners' agreement to publish reference material.
3. **C1 — a first-party "AI browser agent" marketplace script.** It tests demand and real token
   costs with zero client or SDK contract changes.
4. **B — MCP server**, once the Kotlin SDK supports the 2026-07-28 stateless spec. Start with
   read-only tools, then add confirmed start/stop.
5. **C2 and E — SDK browser/agent channel with cached, self-healing actions**, only if C1 shows demand.
   This is the step that turns Cereal from a script runner into an AI automation platform, and it is
   the most expensive and permanent.

Cross-cutting rules for every step:
- Bring-your-own-key or local model, with nothing routed through Cereal servers (ADR-0009 spirit).
- Never send `Secret` or proxy credentials to a model.
- Confirm in the UI before irreversible actions.
- Treat page content as untrusted input to the model.

## 5. Unverified claims and gaps

- Whether all kdriver `Tab`/`Element` methods listed exist in 0.5.11 (they were read on `main`; 0.6.1
  is the latest release). I found no changelog for 0.5.11 → 0.6.1.
- Whether scripts can use the host's kdriver or other libraries through the parent-loader fallback in
  `ScriptClassLoader.kt:48-49` after ProGuard obfuscation of release builds. Not tested.
- Claude's "browser use tool" details; OpenAI computer-use GA status; whether openai-java has typed MCP
  or computer helpers.
- Whether Ollama's OpenAI-compatible endpoint accepts `response_format.json_schema`.
- How Stagehand's self-healing works internally, and Playwright MCP's exact ref string format.
- Whether the Kotlin MCP SDK ships ProGuard rules (relevant to `runRelease`). Which spec version the MCP
  Java SDK 2.0.1 implements.
- The effort estimates in §3 are judgement, not measured.
- How good config descriptions are across real marketplace scripts, which decides how well option A
  works. Only the sample script was inspected.
