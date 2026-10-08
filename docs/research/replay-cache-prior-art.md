# Replay-cache prior art: browser-use `rerun_history` and Stagehand's action cache

Answers [#52](https://github.com/Cereal-Automation/desktop-client/issues/52) (map #47, research doc §2.3).
For each system: what is stored per action, what the cache key is, how the element is found again on
replay, what counts as a miss, and how it falls back to the LLM.

Everything below comes from source code, pinned to these commits:

- browser-use `c75e8476` (`browser_use/agent/service.py`, `browser_use/dom/views.py`)
- Stagehand v3 (local file cache) at tag `@browserbasehq/stagehand@3.7.3` = `3554b02b`
  (`packages/core/lib/v3/cache/ActCache.ts`, `cache/utils.ts`, `cache/AgentCache.ts`,
  `handlers/actHandler.ts`, `v3.ts`)
- Stagehand `main` (v4, server-side cache) `771f2da0`
  (`packages/extension/services/cacheService.ts`, `services/actService.ts`)

## TL;DR

| | browser-use `rerun_history` | Stagehand v3 `cacheDir` | Stagehand v4 server cache |
|---|---|---|---|
| Unit | one agent step (list of actions) | one `act(instruction)` call | one act/observe/extract call |
| Key | none: replay is positional through a saved `AgentHistoryList` JSON | `sha256({instruction.trim(), url with sorted query params, sorted variable keys})`, no DOM | computed server-side (opaque) from instruction + options + URL + **raw CDP AX tree of every frame**; model config excluded |
| Stored per action | `DOMInteractedElement`: tag, attributes, xpath, `element_hash`, `stable_hash`, `ax_name`, bounds, frame id | `Action{selector (xpath), description, method, arguments}` + instruction, url, variableKeys | the same `Action[]`, in Redis |
| Re-match on replay | 5-level cascade over the fresh selector map: EXACT hash, STABLE hash, XPATH, AX_NAME, unique ATTRIBUTE | none: reuse the stored selector (`waitForSelector` attached, 15 s, proceeds even on timeout) | none: reuse the stored selector |
| Miss | cascade finds nothing ("Could not find matching element"), or the action throws | no file / version != 1 / empty actions / variable-key mismatch / missing variable value | server says miss (cold, under hit-count threshold, other `missReason`), read failed, or replay threw (`replay_failed`) |
| LLM fallback | **none for element misses**: retry with backoff (5/10/20 s, 3 tries), re-open a dropdown, then raise or skip. Only `extract` steps always re-run the LLM | per action: `selfHeal` (default on) takes a fresh snapshot, asks the LLM for a new selector for `"<method> <description>"`, rewrites the cache file if selectors changed | whole call: any replay failure throws, then the full act pipeline runs and the new result is written back |

## browser-use: `rerun_history`

### What is stored

A run is saved with `AgentHistoryList.save_to_file` (`agent/views.py:622`). Each `AgentHistory` item
holds the model output (the action list), the results, step timing (`step_interval`), and
`state.interacted_element[i]`, one `DOMInteractedElement` per action (`dom/views.py:982`):

`node_id, backend_node_id, frame_id, node_type, node_value, node_name, attributes, bounds, x_path,
element_hash, stable_hash, ax_name`.

- `element_hash` is sha256 of `parent tag path | sorted STATIC_ATTRIBUTES | ax_name`, first 16 hex
  chars (`dom/views.py:867`). `STATIC_ATTRIBUTES` (`dom/views.py:84`) is an allowlist: `class, id,
  name, type, placeholder, aria-label, title, role, data-testid, data-test, data-cy, ...`.
- `stable_hash` is the same thing with transient class names removed (`DYNAMIC_CLASS_PATTERNS`:
  `focus, hover, active, selected, open, expanded, loading, ...`, `dom/views.py:139,834`). It is
  computed when the history is saved.

### Key

There is no cache key and no lookup. `load_and_rerun(history_file, variables)` loads one JSON file,
optionally substitutes variables into it, and `rerun_history` walks the steps in order
(`service.py:3103`). The "key" is the history file plus the step's position in it. Nothing checks
that the URL or page matches before a step runs.

### Re-matching (`_update_action_indices`, `service.py:3529`)

Each replayed step takes a fresh `get_browser_state_summary()`; elements in the same frame as the
recorded one go first. The cascade then stops at the first hit:

1. **EXACT**: `elem.element_hash == historical.element_hash`
2. **STABLE**: `elem.compute_stable_hash() == historical.stable_hash`
3. **XPATH**: same xpath string
4. **AX_NAME**: same tag and same accessible name
5. **ATTRIBUTE**: same tag and same value for the first of `name`, `id`, `aria-label` present
   (kept for old history files that have no `stable_hash`)

On a hit, the action's `index` is rewritten to the current highlight index. Every level takes the
first match it finds, so duplicate matches are never disambiguated. An optional
`wait_for_elements` polls until the page has as many elements as the recording expects (for SPAs).

### Miss and fallback

- A miss is `_update_action_indices` returning `None`. It raises `ValueError("Could not find
  matching element ... Tried: EXACT hash → STABLE hash → XPATH → AX_NAME → ATTRIBUTE")`, with
  diagnostics listing same-tag candidates (`service.py:3486`).
- `rerun_history` catches it and retries up to `max_retries=3` with exponential backoff (5 s, 10 s,
  20 s, max 30 s). One special case: if the previous step looked like a menu opener and the target
  looks like a menu item, it runs the previous step again once without using up a retry
  (`_reexecute_menu_opener`).
- After the last retry it raises `RuntimeError`, or with `skip_failures` records the error and moves
  on. **No LLM is asked to find the element again.**
- The LLM shows up in only three places: `extract` actions always re-run as an AI step against the
  live page (`_execute_ai_step`), an optional final summary of the rerun, and nowhere else.
- Smaller heuristics: steps that errored in the original run are skipped when `skip_failures` is on.
  A step that repeats the previous step on the same element after it succeeded is skipped as a
  "redundant retry" (`_is_redundant_retry_step`, `service.py:3714`). The delay between steps is the
  recorded `step_interval`, capped at 45 s.

## Stagehand v3: local `cacheDir` act cache

### Key and stored entry

`ActCache.prepareContext` (`ActCache.ts:45`):

```
cacheKey = sha256(JSON.stringify({ instruction: instruction.trim(),
                                   url: normalizeUrlForCacheKey(page.url()),  // query params sorted
                                   variableKeys: Object.keys(variables).sort() }))
```

The file is `<cacheDir>/<cacheKey>.json`, holding a `CachedActEntry` (`types/private/cache.ts`):
`{version: 1, instruction, url, variableKeys, actions: Action[], actionDescription, message}`, where
`Action = {selector, description, method, arguments}`. The selector is the XPath that inference
picked. Arguments keep the `%var%` placeholders, so the cache is keyed on variable *names* and the
values are filled in at replay. **The DOM is not in the local key.**

The agent cache (`AgentCache.ts`) works the same way one level up. Its key is a hash of
instruction, start URL, sanitized execute options (`maxSteps`, `highlightCursor`), a
`configSignature` (base model, system prompts, CUA mode, agent model, tool keys, integrations), and
variable keys. The value is the list of replay steps (`act` with its `Action[]`, `fillForm`, `goto`,
`scroll`, `wait`, `navback`, `keys`).

Only successful acts with at least one action are stored (`v3.ts:1416`). Caching is skipped for
`"auto"` model sessions, because there is no local LLM to self-heal with (`v3.ts:1359`).

### Replay

`tryReplay` returns `null`, which makes the caller run normal inference, when the file is missing or
unreadable, `version !== 1`, `actions` is empty, the variable-key set differs, or a needed variable
value is missing (`ActCache.ts:71-124`). Those are the only miss conditions, and they are all
checked before anything runs on the page.

On a hit, each action runs through `waitForCachedSelector` (wait for the XPath to be attached, 15 s
default, **proceed anyway on timeout**) and then `actHandler.takeDeterministicAction`. There is no
element re-matching: the stored selector is used as is.

### Fallback: self-heal per action

Inside `takeDeterministicAction` (`actHandler.ts:327`), if the Playwright-style action throws and
`selfHeal` is on (it defaults to `true`, `v3.ts:906`), Stagehand:

1. takes a fresh hybrid snapshot (AX tree plus xpath map),
2. asks the LLM to act on `"<method> <action.description>"` (the description stored in the cache,
   not the original instruction),
3. retries the same method and arguments on the new selector.

If replay succeeds and any selector, description, method or argument changed, `refreshCacheEntry`
rewrites the cache file, so the healed selector replaces the old one (`ActCache.ts:264-275`).

Edge case: if self-heal also fails, `tryReplay` returns a non-null `{success: false}` and `v3.ts:1376`
returns it as the act result. A hit that fails does **not** fall back to a full inference of the
original instruction. The only LLM fallback is the per-action self-heal.

## Stagehand v4 (current `main`): server-side cache

`withCache` (`cacheService.ts`) sends the request params that go into the key (act: `input`,
`variables`, `timeout`; extract adds `schema`; model config left out on purpose), the page URL, and
the **verbatim `Accessibility.getFullAXTree` nodes for every frame** to the Browserbase API. The
comment says "the API server owns all cache-key computation (DOM shaping/hashing, URL
normalization)", and that code is not in the public repo. The docs add that tracking query params
are filtered out, that a `selector` scope narrows the tree that gets hashed, and that a hit-count
`threshold` must be reached before hits are served (docs v3/v4 `best-practices/caching.mdx`).

- Bypass: a locator scope in the options (`shouldBypassCacheForLocatorScope`), no API key or
  session id, or the AX tree could not be collected.
- Hit: the value is normalized back into `Action[]`, and `replayCachedActions` runs each one through
  `takeDeterministicAction` with **`selfHeal: false`** (`actService.ts:254`). Any failure throws.
- Miss: the server's `missReason`, `read_failed`, or `replay_failed` when the hit threw. Every one
  falls through to the full `runActPipeline()` (snapshot plus LLM). The new actions are written back
  if they succeeded and are non-empty. The code comment says the full pipeline "doubles as the
  self-heal path for stale cached selectors".

So v4 changed both sides. The page shape moved into the key, which makes hits much less likely to
be stale, and a failed replay now falls back to a full LLM run of the original instruction instead
of the per-action self-heal.

## What this means for Cereal

- There are two kinds of miss check, and the systems mix them. A **pre-check** happens before acting:
  Stagehand v3 checks only the key, v4 includes the AX tree in the key. A **post-check** happens
  while acting: browser-use runs its match cascade, Stagehand catches the action throwing. Neither
  checks the page after the action, so "the click landed but on the wrong thing" goes undetected by
  all three.
- For an element locator that survives page changes, the browser-use cascade is the most useful
  thing to copy, and it maps directly onto kdriver's DOM and Accessibility data: store tag, an
  allowlist of attributes, xpath, accessible name, and frame. Then try an exact hash, a hash with
  dynamic classes stripped, the xpath, (tag + AX name), and finally unique id/name/aria-label.
- For the LLM fallback, Stagehand's design works: cache `{selector, method, args, description}`,
  and on a miss re-ask the LLM with the stored description against a fresh snapshot, then write the
  healed selector back.
- Key on variable names, not values (both Stagehand versions do this). Leave the model out of an
  action-level key (v4 does). Do include model and config in a whole-agent-run key (v3
  `configSignature`).
