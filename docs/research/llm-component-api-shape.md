# Research: one request/response shape for Anthropic Messages and OpenAI-compatible Chat Completions

Ticket: #94 (map #93). Builds on #50 (`docs/research/llm-client-fit.md` on branch
`research/llm-client-fit`) and #56, which settled raw OkHttp + kotlinx-serialization with two adapters:
native Anthropic Messages and OpenAI-compatible Chat Completions. This note covers the shape the SDK's
`LlmComponent` can expose on top of those two adapters. It reports facts and trade-offs. The interface
itself is designed in later tickets.

Checked 2026-10-16. Sources:

- **Anthropic:** platform.claude.com docs: Messages API reference, structured outputs, streaming, vision,
  handling stop reasons, prompt caching.
- **OpenAI:** platform.openai.com returned 403 to the fetcher, so the OpenAI facts come from the
  first-party `openai/openai-python` types on `main`. They are generated from OpenAI's OpenAPI spec
  (`src/openai/types/chat/*`, `completion_usage.py`, `shared_params/*`).
- **Gemini:** ai.google.dev/gemini-api/docs/openai.
- **Ollama:** docs.ollama.com/api/openai-compatibility, plus the source reading in #50 (`main` @ 31547a6).
- **OpenRouter:** openrouter.ai/docs/api/reference/overview.

## Answer

A common shape exists if the SDK exposes a **provider-neutral subset** and lets the client map it:

- an ordered list of turns;
- a separate system prompt;
- function tools with a JSON Schema;
- tool calls whose arguments are parsed objects;
- an optional JSON-Schema response format;
- image input as **bytes + media type**;
- one normalized stop reason;
- normalized token usage.

The SDK has to **hide** five divergences:

1. Where the system prompt goes.
2. How tool results are carried.
3. Whether tool arguments arrive as an object or a JSON string.
4. Whether cached tokens are counted inside or outside the input count.
5. The stop-reason vocabulary.

It has to **expose or constrain** five more, because no mapping papers over them:

1. Assistant turns must be round-tripped **opaquely**. Claude thinking blocks and Gemini 3 thought
   signatures have to come back unchanged.
2. `tool_choice` is limited to `auto`/`none`. Forced choice returns a 400 on `claude-opus-5-5`, and Ollama
   ignores `tool_choice`.
3. Images in tool results work on Anthropic only.
4. The JSON-Schema subset is the strictest provider's.
5. Refusal and content-filter stops are their own outcome, not an error.

Streaming is the one feature where the two wire formats differ the most, and scripts need it the least.
Leave it out of v1.

## Feature by feature

### Multi-turn messages

| | Anthropic Messages | OpenAI Chat Completions |
|---|---|---|
| Roles in `messages` | `user`, `assistant` (plus mid-conversation `system` on newer models) | `system`, `developer`, `user`, `assistant`, `tool` |
| Content | string or block array (`text`, `image`, `document`, `tool_use`, `tool_result`, `thinking`, …) | string or part array; user parts: text/image/audio/file; assistant parts: text/refusal only |
| Final assistant turn | treated as **prefill**; returns a 400 on 4.6+ / 5.x models | ordinary history |
| Hidden state that must be echoed back | `thinking` blocks (with `signature`); the history is append-only ("preserved thinking" on `claude-opus-5-5`, see #56) | none on OpenAI. Gemini 3 has thought signatures in its OpenAI layer; the docs say they're supported but don't name the field. |

**Implication:** the SDK should not let scripts build assistant turns. The client returns an assistant
turn that the script appends to the history as is. Inside it, the client keeps the raw provider blocks
(thinking, signatures, provider extras). This covers Anthropic's append-only rule and Gemini's signatures
with one rule, and it removes prefill as something a script can do.

### System prompt

- **Anthropic:** top-level `system` (a string or an array of text blocks; `cache_control` goes on a block).
- **OpenAI:** first message with `role: "system"` or `role: "developer"`, text only. OpenAI's newer models
  treat `developer` as the system slot. Gemini, Ollama and OpenRouter document `system`.

**Hide it:** the SDK takes a `system: String?`. The OpenAI adapter sends it as a `system` message, the
role every compatible server accepts.

### Tools and tool calls

| | Anthropic | OpenAI-compatible |
|---|---|---|
| Definition | `{name, description, input_schema, strict?}` | `{type:"function", function:{name, description, parameters, strict?}}` |
| Call in response | `tool_use` content block `{id, name, input: object}` | `message.tool_calls[] {id, type:"function", function:{name, arguments: string}}` |
| Result | `tool_result` block `{tool_use_id, content, is_error?}` inside a **user** turn; all results for one turn go in one user message; content may be text **or images** | one `role:"tool"` message per call `{tool_call_id, content}`; content is **text only** |
| `tool_choice` | `auto`, `any`, `tool`, `none`. **`any`/`tool` return a 400 on `claude-opus-5-5`** | `none`, `auto`, `required`, named. **Ollama: not supported**. Gemini docs show `auto`. |
| Parallel calls | on by default; `disable_parallel_tool_use` | `parallel_tool_calls` |
| Strict schemas | `strict: true` is enforced | OpenAI enforces `strict`. Ollama drops `strict` and `additionalProperties` (#50). OpenRouter ignores parameters a model doesn't support. |

**Hide:** the definition format, `arguments` as a string vs. `input` as an object (the client parses it),
and how results are grouped. **Constrain:** expose only `Auto` / `None` for tool choice, and keep
validating arguments on the client for every provider, as #56 decided. **Expose:** a tool result is
text; image tool results only where the provider supports them (Anthropic). Simplest v1: text-only tool
results.

### Structured (JSON-schema) output

- **Anthropic:** `output_config: {format: {type: "json_schema", schema}}`. The JSON comes back in a `text`
  block. Schema limits: `additionalProperties: false` is required on every object; no recursion; no
  numeric (`minimum`/`maximum`) or string-length constraints; `minItems` only 0/1. Unsupported keywords
  return a **400**. On `refusal` or `max_tokens` the output may not match the schema. Enum case isn't
  guaranteed.
- **OpenAI:** `response_format: {type: "json_schema", json_schema: {name, schema, strict?, description?}}`
  (or `json_object`). The JSON comes back in `message.content`. A refusal comes back in
  `message.refusal`.
- **Gemini:** `response_format` is documented through the SDK `parse` helpers.
- **Ollama:** `json_schema` is accepted and forwarded as the native `format` (#50). The compat doc lists
  only "JSON mode".
- **OpenRouter:** `json_schema` on models that support it; otherwise ignored.

**Hide:** the wrapper (`output_config.format` vs. `response_format.json_schema` with a required `name`).
**Constrain:** the SDK accepts the Anthropic-safe schema subset. The client always parses and validates
the returned JSON, because Ollama/OpenRouter may not enforce it. A schema failure becomes a distinct error,
not a crash.

### Image input

| | Anthropic | OpenAI | Gemini /v1 | Ollama /v1 | OpenRouter |
|---|---|---|---|---|---|
| Wire | `image` block, source `base64` (`media_type`, `data`), `url`, or `file` | `image_url {url, detail}`, `url` may be a `data:` URI | `image_url` with base64 data URI (URLs not documented) | base64 only, **URLs not supported** | `image_url` URL or base64 |
| Formats | jpeg, png, gif, webp | — | — | — | — |
| Limits | 10 MB per image, 8000×8000 px (2000 px if >20 images), 32 MB per request; ~`⌈w/28⌉×⌈h/28⌉` tokens, capped (4784 on 4.7+) | — | — | — | — |

**Hide:** the SDK takes `bytes + mediaType` (jpeg/png/gif/webp). The adapters emit a base64 block or a
`data:` URI. Base64 is the only form every target accepts. Skip URL and file sources.

### Streaming

- **Anthropic SSE:** `message_start` → per block `content_block_start` / `content_block_delta`
  (`text_delta`, `input_json_delta`, `thinking_delta`, `signature_delta`) / `content_block_stop` →
  `message_delta` (stop reason; `usage` is **cumulative**) → `message_stop`. `ping` events appear
  anywhere. Errors arrive in-stream (`event: error`, e.g. `overloaded_error`).
- **OpenAI SSE:** `chat.completion.chunk` with `choices[].delta`. Tool-call fragments are keyed by
  `index`. `finish_reason` comes on the last choice chunk. Usage only with
  `stream_options.include_usage`, as one extra chunk with empty `choices` before `[DONE]`; it may be
  missing if the stream breaks. Ollama supports `include_usage`.
- **OpenRouter:** sends comment keep-alives. A mid-stream error is a per-choice `error` with
  `finish_reason: "error"`.

**Recommendation:** no streaming in v1. #56 already chose non-streaming for the agent, and a script
consumes whole answers. If it's added later, expose `Flow<TextDelta>` plus a final complete response, and
never expose raw tool-argument fragments.

### Stop reasons

| Normalized | Anthropic `stop_reason` | OpenAI `finish_reason` (OpenRouter-normalized too) |
|---|---|---|
| `EndTurn` | `end_turn` | `stop` (when no stop sequence matched) |
| `StopSequence` | `stop_sequence` (`stop_sequence` field says which) | `stop` (no way to tell) |
| `ToolUse` | `tool_use` | `tool_calls` (`function_call` deprecated) |
| `MaxTokens` | `max_tokens`, `model_context_window_exceeded` | `length` |
| `Refusal` | `refusal` (HTTP 200, `stop_details.category`) | `content_filter`, or a non-null `message.refusal` |
| internal only | `pause_turn` (server tools only; the client never sends server tools) | OpenRouter `error` → raise |

**Expose** the normalized enum. Keep `StopSequence` folded into `EndTurn` unless stop sequences are in the
interface, since OpenAI can't tell the two apart. `Refusal` must be an outcome a script can branch on,
because both APIs deliver it as a successful HTTP response.

### Token usage and cost

| | Anthropic `usage` | OpenAI `usage` |
|---|---|---|
| Input | `input_tokens` = **uncached** input only (after the last cache breakpoint) | `prompt_tokens` = all prompt tokens, **including** `prompt_tokens_details.cached_tokens` |
| Cache read | `cache_read_input_tokens` (0.05× base on Opus 5.5) | `prompt_tokens_details.cached_tokens` |
| Cache write | `cache_creation_input_tokens` (+ `cache_creation.ephemeral_5m/1h`), 1.25×/2× base | `prompt_tokens_details.cache_write_tokens` (new; explicit breakpoints on gpt-5.6+) |
| Output | `output_tokens` (includes thinking) | `completion_tokens`; `completion_tokens_details.reasoning_tokens` |
| Cost | not returned; compute from a price table | not returned by OpenAI. **OpenRouter returns `usage.cost`** and `cost_details`. |

Total Anthropic input = `input_tokens + cache_read_input_tokens + cache_creation_input_tokens`.

**Hide** the inside/outside difference: normalize to `{inputUncached, cacheRead, cacheWrite, output}`.
For OpenAI, `inputUncached = prompt_tokens − cached_tokens − (cache_write_tokens ?: 0)`. That
`cache_write_tokens` is counted inside `prompt_tokens` is an assumption the type docs don't state.
Check it.
**Expose** usage on every response. Cost stays in the client, priced from the #58 table. Use OpenRouter's
`cost` when it's present.

### Other divergences worth knowing

- **Output cap:** `max_tokens` is required on Anthropic. On OpenAI, `max_tokens` is deprecated in favor of
  `max_completion_tokens`, which o-series models require. Gemini, Ollama and OpenRouter document
  `max_tokens`. The adapter picks the field. The SDK takes one optional `maxOutputTokens`.
- **Prompt caching:** Anthropic caches only with `cache_control` (top-level auto-placement is simplest).
  OpenAI caches implicitly, with optional explicit breakpoints on gpt-5.6+. Hide it: the client always
  turns caching on.
- **Thinking / effort:** Anthropic `output_config.effort` vs. OpenAI/Gemini/Ollama `reasoning_effort`. On
  `claude-opus-5-5`, thinking can't be disabled and effort defaults to `medium`. Optional: expose
  `effort: Low | Medium | High`. #58 left user-tunable effort open.
- **Errors:** Anthropic 429/529 (`overloaded_error`), OpenAI 429. Mapping to SDK errors (rate-limited /
  overloaded / auth / bad request) is the failure-patch ticket.

## Sketch of the neutral shape (for the design ticket, not a decision)

```kotlin
// Request
data class LlmRequest(
    val system: String?,
    val messages: List<LlmMessage>,            // User(text/images) | Assistant(opaque) | ToolResults
    val tools: List<LlmTool> = emptyList(),    // name, description, JSON Schema (Anthropic-safe subset)
    val toolChoice: ToolChoice = ToolChoice.Auto, // Auto | None
    val responseSchema: JsonObject? = null,
    val maxOutputTokens: Int? = null,
)
// Response
data class LlmResponse(
    val assistant: LlmMessage.Assistant,       // append as is; holds raw provider blocks
    val text: String,
    val toolCalls: List<ToolCall>,             // id, name, arguments: JsonObject (validated)
    val stopReason: StopReason,                // EndTurn | ToolUse | MaxTokens | Refusal
    val usage: Usage,                          // inputUncached, cacheRead, cacheWrite, output
)
```

## Open points

- Gemini 3 thought signatures in the OpenAI layer: the docs say "supported" but don't name the field or
  say what happens when one is missing. The opaque-assistant rule covers it if the adapter keeps unknown
  fields on `tool_calls`. Check with a live call.
- OpenAI `cache_write_tokens` is new and only appears on models with explicit caching. Treat it as
  optional.
- `stop` sequences: leave them out unless a use case appears (OpenAI can't report which one fired).
