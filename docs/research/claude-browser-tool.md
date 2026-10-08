# Could Claude's browser-use or computer-use tool replace our own AX-tree loop?

Research for [#51](https://github.com/Cereal-Automation/desktop-client/issues/51) (map
[#47](https://github.com/Cereal-Automation/desktop-client/issues/47)). It fills the **[UNVERIFIED]** gap in §2.2 of
[ai-automation-transformation.md](https://github.com/Cereal-Automation/desktop-client/blob/research/ai-automation-transformation/docs/research/ai-automation-transformation.md).
Checked 2026-10-08.

## Answer

**No.** Neither tool is a loop or a browser that we could swap in. Both are *client toolsets*: Anthropic ships a
tool schema and a system prompt, and we still write the executor that drives Chrome. On top of that, the browser
toolset only works on the Claude API and Google Cloud, so a design built on it would break BYOK-with-any-provider
and local models.

What to do instead:

- **Keep our own provider-neutral tool set and loop** over kdriver.
- **Copy the browser toolset's shape**: member names, `ref`-or-coordinate targets, `read_page` before `screenshot`,
  and the batch-halt rule. The shape is a documented design that Claude is trained on, and it gives us a cheap
  upgrade path.
- **Optional later**: add an Anthropic-only adapter that sends `browser_toolset_20260801` and routes its calls to
  the same kdriver executor. It is not needed for C1.
- **Rule out computer use for C1.** It drives a whole desktop from pixels, while we already have CDP inside Chrome.

## 1. What the tools are

| | Browser use | Computer use |
|---|---|---|
| Tool type | `browser_toolset_20260801`, an entry in `tools` with no `name` [B] | `computer_toolset_20260801` [C] |
| Status | GA, no beta header [B] | GA, no beta header on the Claude API [C] |
| Where it runs | Client side: "Nothing runs on Anthropic's side." Our app hosts the browser and runs every call [B] | Client side, "in an environment you control" [C] |
| Members | 27 by default plus 4 optional (`file_upload`, `read_console`, `read_network`, `javascript_exec`) [B] | 17: screenshot, zoom, clicks, drag, mouse, scroll, type, key, hold_key, wait [C] |
| Page state | `read_page` gives an accessibility-style text tree with `[ref_N]` tags, capped at 50,000 chars. Also `find`, `get_page_text`, `screenshot` and `zoom` [B] | Screenshots only, with coordinates in screenshot pixel space [C] |
| Models | Fable 5.1/5, Mythos 5.1/5, Opus 5.5/5/4.8, Sonnet 5.5/5, Haiku 5.5 [B] | Same list. Opus 4.7 and older use only beta `computer_20251124` [C] |
| Platforms | Claude API and Google Cloud only. Not on Bedrock, Claude Platform on AWS, Foundry, or Managed Agents [B] | GA on the Claude API and Google Cloud. Older beta versions only elsewhere [C] |

## 2. What the client side has to implement

For the browser toolset [B]:

- Handlers for every enabled member, routed on the pair (`toolset_name`, `name`). Disabled or unknown members get
  an error result, never silence.
- Ref resolution. A `target` is either `{"type":"ref","ref":"ref_2"}` from `read_page`/`find`, or a
  `{"type":"coordinate",...}` in viewport pixels. **The API doesn't check refs**, so the executor has to detect stale
  ones and return an error.
- Results that echo `"toolset_name": "browser"`. Content may only be `text`, `image` or `browser_state`. Tab-management
  results return exactly one `browser_state` block.
- Batches: run calls in order and stop at the first failure. Every remaining call gets
  `is_error: true` with `Not executed: an earlier action in this turn failed.`
- Our own resizing. The API rejects oversized images instead of downscaling them, so we resize and scale
  coordinates back.
- When streaming, each `input` arrives whole, so wait for the turn to end before running the batch.
- Our own URL scheme checks, domain allowlist and human confirmation. The docs list these as our job.

Computer use needs the same loop shape, plus a virtual display (the reference uses Xvfb in Docker), coordinate
scaling, and ~500 ms delays after actions [C].

**SDK support.** Only the Python and TypeScript SDKs ship a driver base class
(`BetaAbstractBrowserToolset20260801`). Even those include "no browser, desktop, ready-made driver, or URL policy"
[S]. The Java SDK v2.70.0 (2026-10-08) has only the wire types, `BrowserToolset20260801` and
`BrowserToolsetConfigs` in `anthropic-java-core/.../models/messages/`, found with `gh api search/code`. On the JVM
we would write the routing, result building and batch-halt logic ourselves.

## 3. Does it fit kdriver-driven Chrome inside a script?

**Mechanically, yes.** Anthropic's own minimal reference driver
([claude-quickstarts/browser-toolset/python/cdp_browser.py](https://github.com/anthropics/claude-quickstarts/tree/main/browser-toolset))
is raw CDP over a websocket:

- `Page.navigate`
- `Input.dispatchMouseEvent`
- `Runtime.evaluate` to build the `read_page` tree, resolve refs, and pull page text. It uses injected JS, *not* the
  experimental `Accessibility` domain.

kdriver exposes all of these: `Tab` implements `CDP`, and it has `rawEvaluate`, `mouseClick` and `screenshotB64`
(§2.3 of the background doc). Nothing needs a new browser dependency, a container or a display.

**What it doesn't save us.** The executor, which is the bulk of the work, is the same code either way: the
snapshot, ref map, staleness checks, input dispatch, tab tracking and safety checks. The toolset only changes the
wire format and supplies Anthropic's prompt.

**Computer use doesn't fit.** It assumes it owns a whole screen and works only from pixels. A script already
inside Chrome would have to fake a desktop out of `Page.captureScreenshot` and give up the cheap text tree.

## 4. Cost per step versus our own AX-tree text snapshot

Fixed overhead per request (input tokens):

| Setup | Overhead | Source |
|---|---|---|
| `browser_toolset_20260801`, default members | ~6,600 (+~880 with all optional members on) | [B] Pricing |
| `computer_toolset_20260801`, default members | ~4,500 (−~410 without `zoom`) | [C] Pricing |
| Our own ~6–8 custom tools | low hundreds to ~1–2k, depending on descriptions | estimate, **[UNVERIFIED]**; measure with `count_tokens` |

Variable cost per step:

| Item | Cost | Source |
|---|---|---|
| Screenshot | ~1,000–1,800 input tokens, billed as vision input | [C] |
| `read_page` / our AX snapshot | same size whichever loop produces it, since it's our own text; capped at 50k chars ≈ 12k tokens worst case | [B] |

Worked example on Opus 5.5 ($4/MTok input; cache reads ≈ 0.1×, see §2.2):

- Prompt caching hides most of the fixed overhead after step 1. A cached ~6,600-token toolset costs about **660
  effective tokens per step ≈ $0.0026**.
- **The per-step cost is driven by the page snapshot either way, not by the tool definitions.** A text snapshot
  beats a screenshot whenever it is under ~1–2k tokens. The docs themselves recommend reading the tree first [B].
- Net effect of choosing the toolset: a small fixed premium per step, cached. It isn't a reason to choose either way.

## 5. Lock-in: BYOK and local models

- The toolset only exists on Anthropic's Messages API, and only on the Claude API and Google Cloud [B]. OpenAI,
  OpenAI-compatible gateways and local models (Ollama, LM Studio, vLLM) have no `browser_toolset_20260801`, no
  `toolset_name` and no `browser_state` block.
- Building the agent on it would mean either Claude-only, or **two loops**: a native one for Claude and a
  custom-tools one for everything else.
- Plain custom tools (`name` + `input_schema`, `strict: true`) work across Anthropic, OpenAI function calling and
  most OpenAI-compatible servers. One loop covers every provider, which fits #47's "BYOK only, local models
  best-effort".
- BYOK itself is fine on either path, since the user's key calls the API directly. The lock-in comes from the
  wire format, not from billing.

## 6. Recommendation for the C1 spec

1. **Provider-neutral custom tools**, named and shaped after the browser toolset members:
   - `navigate`
   - `read_page` (indexed refs)
   - `find`
   - `click` with a `target` that is a ref or a coordinate
   - `form_input` / `type`
   - `key`
   - `scroll`
   - `screenshot` (opt-in)
   - `wait`
   - `done`

   Use the same batch-halt and stale-ref error semantics. Claude is already tuned on this vocabulary, and a later
   native adapter becomes a thin translation.
2. **Executor over kdriver.** Build the ref tree from injected JS (as the reference does) or from
   `Accessibility.getFullAXTree`, keeping in mind that the latter is experimental (§2.3).
3. **Defer** an Anthropic-native mode (`browser_toolset_20260801`, which gets Anthropic's tuned prompt and
   prompt-injection classifiers on browser output [B]) until an eval shows it beats our tool set on the acceptance
   model. Measure before adding a second loop.
4. **Drop computer use** from C1.

## Sources

- [B] https://platform.claude.com/docs/en/agents-and-tools/tool-use/browser-use-tool (read in full, including Pricing and Limitations)
- [C] https://platform.claude.com/docs/en/agents-and-tools/tool-use/computer-use-tool (read in full, including Pricing, Migrate and Earlier versions)
- [S] https://platform.claude.com/docs/en/agents-and-tools/tool-use/browser-use-sdk (SDK toolset classes, Python/TypeScript only)
- https://github.com/anthropics/claude-quickstarts/tree/main/browser-toolset (`python/cdp_browser.py`, the reference CDP driver)
- https://github.com/anthropics/anthropic-sdk-java v2.70.0 (`BrowserToolset20260801.kt`, `BrowserToolsetConfigs.kt`)
