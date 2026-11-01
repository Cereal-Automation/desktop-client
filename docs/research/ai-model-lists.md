# What each AI preset's model list returns

Research for [#248](https://github.com/Cereal-Automation/desktop-client/issues/248), part of the
"pick an AI model instead of typing one" map ([#247](https://github.com/Cereal-Automation/desktop-client/issues/247)).
Gathered 2026-11-01 from provider API docs, provider source code, and live responses where no key is needed.

## What we parse today

- `OpenAiCompatibleLlmDataSource.listModels` sends `GET {baseUrl}/models` with no query (Gemini, OpenAI,
  OpenRouter, Local, Other).
- `AnthropicLlmDataSource.listModels` sends `GET {baseUrl}/models?limit=1000` and reads one page.
- Both go through `LlmHttp.getModels`, which decodes `{"data":[{"id","display_name"?}]}` into
  `LlmModel(id, displayName)` and ignores every other field. A 404 becomes "no models at this base URL".

So today only `id` and, for Anthropic alone, `display_name` reach the domain. Dates, prices and
capabilities are all dropped.

## Summary

| Preset | Creation date | Display name | Machine-readable $0 | Free tier (not in the API) | Can't-chat signal | Size | Paging |
|---|---|---|---|---|---|---|---|
| Gemini (OpenAI layer) | Not documented; unverified | Not documented | None | **Yes**: AI Studio free tier, rate-limited, content used to improve Google products | None documented in this layer. The native API has `supportedGenerationMethods` | Unverified (dozens, mixed types) | Not documented for this layer. The native API pages at 50 by default |
| Anthropic | `created_at`, RFC 3339 (may be epoch if unknown). List is newest first | `display_name` (we read it) | None | No | None needed: every model chats. `capabilities` object and `lifecycle` are present | About 10 (estimate; needs a key) | Cursor: `limit` 1–1000 (default 20), `has_more`, `after_id` |
| OpenAI | `created`, Unix seconds | None | None | No free API tier documented | None. Must infer from id (`text-embedding-*`, `tts-*`, `whisper-*`, `dall-e-*`, `gpt-image-*`, `omni-moderation-*`, ...) | About 100 (estimate; needs a key) | None |
| OpenRouter | `created`, Unix seconds (always set in the live list) | `name`, e.g. "OpenAI: GPT-6.1 Terra Pro" | **Yes**: `pricing.prompt == "0"` and `pricing.completion == "0"`; most also end in `:free` | Yes for `:free` variants: 20 req/min, 50 req/day (1000/day after buying $10 credit); some free providers log and train | `architecture.output_modalities`; default response already holds text-output models only. `endpoints` lacks `/api/v1/chat/completions` for `:batch` ids | 452 (live) | Optional `offset`/`limit`; with neither, full list, `links.next` null |
| Local (Ollama) | `created` is Unix seconds of **last modified** (pull time), not release | None (id only) | Not needed: local is free | n/a | None on `/v1/models`. Native `/api/tags` and `/api/show` carry `capabilities` (`completion`, `embedding`, `vision`, ...) | What the user pulled | None |

## Gemini (`https://generativelanguage.googleapis.com/v1beta/openai`)

- **Fields.** Google's OpenAI-compatibility page shows `client.models.list()` and `GET .../openai/models`
  but documents no response fields; the examples print only `model.id`
  ([OpenAI compatibility](https://ai.google.dev/gemini-api/docs/openai)). The shape could not be
  fetched without a key. **Unverified**: whether the layer sends `created`, whether `id` carries a
  `models/` prefix, and whether `owned_by` is set. One keyed call settles all three before we build on
  them.
- **Native endpoint as a fallback.** `GET /v1beta/models` returns `name`, `baseModelId`, `version`,
  `displayName`, `description`, token limits, `supportedGenerationMethods`, thinking and sampling
  defaults. It has **no creation date and no price**. It pages with `pageSize` (default 50, max 1000)
  and `nextPageToken` ([models reference](https://ai.google.dev/api/models)). `supportedGenerationMethods`
  without `generateContent` marks embedding-only and similar models.
- **Free tier.** Yes. Most Flash, Flash-Lite, Gemma, TTS and embedding models are "free of charge" on
  the free tier; Gemini 4 Argon, 3.1 Pro Preview, the image models and Lyria are not available on it.
  Free-tier content is "used to improve our products"; paid-tier content is not
  ([pricing](https://ai.google.dev/gemini-api/docs/pricing)). Rate limits apply per project, vary by
  model, and are shown in AI Studio rather than on the docs page
  ([rate limits](https://ai.google.dev/gemini-api/docs/rate-limits)). Nothing in either list endpoint
  says whether a model is on the free tier, so this can only be a note on the preset.
- **Live check.** `GET .../openai/models` with no key returns **404** `"Requested entity was not found."`;
  with a bad key it returns 400 `"Please pass a valid API key"`. Our `getModels` maps 404 to "no models
  at this base URL", so an empty key on the Gemini preset shows the wrong message. The auto-load
  should not fire before a key is entered.

## Anthropic (`https://api.anthropic.com/v1`)

Source: [List Models](https://platform.claude.com/docs/en/api/models/list).

- **Date.** `created_at`, RFC 3339, "the time at which the model was released. May be set to an epoch
  value if the release date is unknown". The list is ordered "more recently released models are listed
  first", so the server order is already newest first.
- **Name.** `display_name`, e.g. "Claude Opus 5.5". Already parsed.
- **Price.** None in the API. No free tier.
- **Can't-chat.** Every listed model is a Messages model. Extra fields: `capabilities` (image/PDF input,
  thinking types, effort levels, structured outputs, ...), `lifecycle` (`active`/`deprecated`/`retired`;
  retired only on request), `deprecated_at`, `retires_at`, `line`, `max_input_tokens`, `max_tokens`.
  `lifecycle == "deprecated"` is a useful "don't default to this" signal.
- **Paging.** `limit` default 20, range 1–1000; `has_more`, `first_id`, `last_id`, `after_id`,
  `before_id`. Our `?limit=1000` gets everything in one page.

## OpenAI (`https://api.openai.com/v1`)

Source: [List models](https://developers.openai.com/api/reference/resources/models/methods/list)
(the `platform.openai.com` reference returns 403 to scripted fetches).

- **Fields.** `id`, `object` ("model"), `created` (Unix seconds), `owned_by`, and an optional
  `shutdown_date` (date or null). That is the whole object.
- **Name / price / capability.** None. The list mixes chat models with embeddings, TTS, Whisper,
  image, moderation and realtime models, with no field telling them apart. Only the id prefix tells
  them apart, which is a heuristic.
- **Free tier.** None documented for the API.
- **Paging.** None; one `GET /models`.

## OpenRouter (`https://openrouter.ai/api/v1`)

Sources: [Get models](https://openrouter.ai/docs/api/api-reference/models/get-models), live
`GET https://openrouter.ai/api/v1/models` (public, no key, cached 120 s), [limits](https://openrouter.ai/docs/api/reference/limits),
[provider logging](https://openrouter.ai/docs/guides/privacy/provider-logging).

- **Date.** `created`, Unix seconds; set on all 452 entries. Newest live: `particle-ai/particle-1`
  (2026-10-28). The `sort=newest` query exists too.
- **Name.** `name`, prefixed with the vendor ("OpenAI: GPT-6.1 Terra Pro").
- **Price.** `pricing` with string values in USD **per token**: `prompt` and `completion` always,
  plus optional `request`, `image`, `audio`, `web_search`, `internal_reasoning`, cache fields.
  In the live list, 17 models have `prompt == completion == "0"`: 14 `:free` ids, `openrouter/free`
  (a router over free models), and two Lyria music models (text+audio output). So "$0" should be
  `prompt == "0" && completion == "0"` plus a text-only output check, not the `:free` suffix alone.
  Routers like `openrouter/auto` send **`"-1"`** (price unknown/variable); treat negatives as "not free".
- **Free tier.** `:free` variants: 20 requests/min, 50/day, or 1000/day once $10 of credit was ever
  bought. Free endpoints may log prompts and train on them, depending on the provider and the
  account's privacy toggles.
- **Can't-chat.** `output_modalities` defaults to `text`, so the default list (452) already excludes
  the 235 embeddings, speech, transcription, image, video and rerank models that `?output_modalities=all`
  returns (687). What still slips through: 16 models whose output adds `image` or `audio`, and 71
  `:batch` ids whose `endpoints` lack `/api/v1/chat/completions`. 66 models list no `tools` in
  `supported_parameters`, which matters since scripts use tool calls.
- **Paging.** Optional `offset`/`limit`; with neither, the full list comes back (`total_count: 452`,
  `links.next: null`). No paging needed.

## Local / Ollama (`http://localhost:11434/v1`)

Sources: [OpenAI compatibility](https://docs.ollama.com/api/openai-compatibility),
[`openai/openai.go`](https://github.com/ollama/ollama/blob/main/openai/openai.go),
[`api/types.go`](https://github.com/ollama/ollama/blob/main/api/types.go),
[`types/model/capability.go`](https://github.com/ollama/ollama/blob/main/types/model/capability.go).

- **Fields.** `/v1/models` maps each local model to `{id, object: "model", created, owned_by}`.
  `created` is `ModifiedAt.Unix()`: the docs say it "corresponds to when the model was last modified",
  i.e. when the user pulled it, not when the model was released. Sorting by it means "most recently
  pulled first".
- **Name / price.** No display name. Local models cost nothing, except remote ones (`remote_host` set
  on the native list, e.g. Ollama cloud models), which `/v1/models` doesn't flag.
- **Can't-chat.** Nothing on `/v1/models`. The native `GET /api/tags` (`ListModelResponse`) and
  `POST /api/show` carry `capabilities`: `completion`, `tools`, `insert`, `vision`, `embedding`,
  `thinking`, `image`, `audio`, `decision`, `speech`. The docs page for `/api/tags` doesn't show the
  field yet; the source does. An embedding model lacks `completion`.
- **Size / paging.** Whatever the user pulled; one call.

## Consequences for the picker (#247)

- **Sort newest first** works from the API on Anthropic, OpenAI and OpenRouter. Ollama's date is pull
  time. Gemini's OpenAI layer is unverified and the native API has no date, so Gemini likely needs the
  preset's hardcoded default, as the map already allows.
- **Free badge** has a machine-readable source only on OpenRouter (`pricing`) and Local. Gemini's free
  tier is a preset note. Anthropic and OpenAI have neither.
- **Filtering non-chat models** has a field on OpenRouter (`output_modalities`, `endpoints`), Ollama
  native (`capabilities`) and Gemini native (`supportedGenerationMethods`). OpenAI and the Gemini
  OpenAI layer leave only id heuristics. Anthropic needs none.
- **Parser changes** needed for this: read `created` (Unix seconds) and `created_at` (RFC 3339),
  OpenRouter's `name` and `pricing`, and `lifecycle`. Keep `?limit=1000` on Anthropic; no other preset
  needs paging.

## Open

- One keyed `GET .../v1beta/openai/models` on Gemini to see its actual fields and id format.
