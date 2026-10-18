# AI browser agent script: manifest and configuration today

Research for [#131](https://github.com/Cereal-Automation/desktop-client/issues/131), map [#130](https://github.com/Cereal-Automation/desktop-client/issues/130).
Read on 2026-10-18 from the remote `master` of the script repo at commit `40e65d5` (2026-10-18), via the GitHub API.

## Where it lives and release state

- **Repo:** [`cerealdevnull/Script-AI-Browser-Agent`](https://github.com/cerealdevnull/Script-AI-Browser-Agent), created 2026-10-10.
  It is **not** under `Cereal-Automation`: spec #69 and map #47 name `Cereal-Automation/Script-AI-Browser-Agent`, which does not exist
  (`gh repo list Cereal-Automation` has no such repo).
- **Released: never.** No tags, no GitHub releases. `RELEASENOTES.md` has only an `## Unreleased` section.
  The release workflow (`.github/workflows/release.yml`) builds on a tag push, rewrites `version_code` from the tag
  (`major*100 + minor*10 + patch`) and attaches `release-<tag>.jar` to a GitHub release. Upload to the marketplace is not automated.
- **Release gate** (`docs/release.md`, from script spec issue #129): a desktop client release must contain `LlmComponent`.
  desktop-client PR #114 merged 2026-10-17, but the latest client tag `cereal-client/1.15.0` (2026-09-23) does not contain it.
  The `features` check (#121) is also on `master` only. So the agent can't run on any released client yet; the rebrand release
  is the first client that can run it. Per `docs/release.md`, older clients "silently keep the previous release", but no
  previous release exists, so on an old client there is nothing to keep.
- Builds against `cereal-sdk` 1.14.0 (`gradle/libs.versions.toml`), Kotlin 2.3.21 pinned to the host's stdlib, kdriver 0.6.1, JVM 21.

## Marketplace identity (`publicIdentifier`)

- `manifest.json` `package_name` is **`com.cereal.aibrowseragent`**.
- The client installs a marketplace script with `packageName = publicIdentifier` and matches installed scripts by
  `manifest.packageName == publicIdentifier` (`ScriptSyncManager.kt` lines 91–139, `InstallMarketplaceScriptInteractor.kt`).
  So the listing's `publicIdentifier` has to be `com.cereal.aibrowseragent`.
- **Not verified:** whether a marketplace listing exists yet. Neither repo nor any issue names the backend's
  `public_identifier`, and the backend is out of scope for #130. The store text is ready in `STORELISTING.md`.

## `manifest.json` (`src/main/resources/manifest.json`)

```json
{
  "package_name": "com.cereal.aibrowseragent",
  "name": "AI Browser Agent",
  "version_code": 2,
  "sdk_version": "@cereal-sdk-version@",
  "script": "com.cereal.script.aiagent.AiAgentScript",
  "features": ["llm"],
  "instructions": "Set up your AI provider and model in Settings → AI. Write a goal and a start URL: Once does the task or answers the question, Repeat checks until a condition is true. A visible Chrome window opens, and the agent asks before it buys, pays or sends anything. Your AI provider bills the model's usage; each run is capped at $2 by default."
}
```

- `sdk_version` is filled at build time from `libs.versions.cereal.sdk`, which is **1.14.0** (`build.gradle.kts`, `processResources`).
- `features: ["llm"]` is required by desktop-client spec #121: without it `provider.llm()` throws `ScriptFeatureNotDeclaredException`,
  and the client warns at Start when the script declares `llm` but no AI provider is saved in Settings → AI.
- `version_code` 2 in the file; the release workflow overwrites it from the tag.

## Configuration items (`application/AgentConfiguration.kt`)

Sections: `task` "Task", `secrets` "Sign-in secrets", `advanced` "Advanced" (collapsed). The code comments say keyNames are
persisted by the host and never change.

| Pos | keyName | Label | Section | Type | Default | Required |
|---|---|---|---|---|---|---|
| 0 | `goal` | Goal | task | String | none | Yes: "Goal is required" (`GoalState`) |
| 1 | `startUrl` | Start URL | task | String (placeholder `https://example.com`) | none | Yes: "Start URL is required" / "must be an http(s) URL"; `https://` added when no scheme is typed |
| 2 | `runMode` | Run mode | task | Enum `RunMode { Once, Repeat }` | `Once` | No (has default) |
| 3 | `repeatInterval` | Check every (seconds) | task | Int | `300` | Shown and required only when Run mode = Repeat (`CheckEveryState`); > 0 |
| 20 | `secrets` | Sign-in secrets | secrets | List of `NamedSecret` rows: `label` String, `value` Secret | empty (nullable) | No. Each row needs a label and a value; labels unique after normalising (lowercase, spaces to `_`) |
| 30 | `allowedDomains` | Allowed domains | advanced | List of `AllowedDomain` rows: `domain` String | empty (nullable) | No. Added to the start URL's eTLD+1; subdomains included |
| 31 | `maxSteps` | Max steps per run | advanced | Int | `40` | No; > 0 |
| 32 | `maxRunCost` | Max $ per run | advanced | Double | `2.0` | No; > 0. Unknown-price models count as $0 |
| 33 | `maxTaskCost` | Max $ per task | advanced | Double | `5.0` | No; > 0. Resets when the task restarts |

Retired keyNames, never reuse: `provider`, `apiKey`, `model`, `baseUrl`, `secret1Name`..`secret3Value`, `reLearn`, `onNextStart`.

Pending: script issue [#163](https://github.com/cerealdevnull/Script-AI-Browser-Agent/issues/163) (open, ready-for-agent)
adds `confirmIrreversible` (Boolean, position 34, Advanced, default `true`, "Ask before irreversible steps").

## What changed since spec #69

| Spec #69 | Now |
|---|---|
| Provider, Base URL, API key, Model (default `claude-opus-5-5`) in the task form | Gone. The model comes from the client's **Settings → AI** through the SDK's `LlmComponent` (script spec #129, issue #133). Saved values stay stored and are ignored. The script doesn't know which provider it is. |
| `cereal-sdk` 1.12.0, no client or SDK changes | `cereal-sdk` 1.14.0, `features: ["llm"]`; needs a client with `LlmComponent` (not released yet) |
| Secret 1–3 name/value slots | One `secrets` list, any number of rows (label + masked Secret). Old slot values are not carried over; users re-enter them |
| Re-learn checkbox | Gone. The script **resets** itself: it forgets the replay cache when a heal or replay shows it no longer fits, and the browser profile when it gets stuck on a sign-in page |
| Max $ per run 3.0, per task 10.0 | 2.0 and 5.0 |
| "Repeat interval (seconds)" always visible | "Check every (seconds)", shown only in Repeat |
| Flat form | Three sections: Task, Sign-in secrets, Advanced (collapsed) |

## `CONTEXT.md` vocabulary (script repo)

The terms a goal composer touches:

- **task**: one configured agent (goal, start URL, run mode, caps, sign-in secrets) from start to stop; its goal runs share the task total and browser profile.
- **target**: one start URL a task watches. The glossary says a task has one or more targets on the same site, but the form has a single `startUrl`.
- **goal run**: one attempt to reach the goal on one target. **repeat run**: a goal run that comes back on a later loop or after a restart.
- **run mode**: **Once** (reach the goal, notify the result) or **Repeat** (monitor until the goal's condition holds).
- **question goal** ("tell me whether …") suits Once; **condition goal** ("notify me when … is in stock") suits Repeat, which learns it as a Check.
- **check** (lowercase, user-facing): one repeat run as the user sees it ("checked 14:32", "42 checks"). Don't call the **Check** condition a "Check" in user text.
- **Check**: a true/false condition on one element that replay evaluates without the LLM; **numeric Check** and **change Check** (Repeat only, keeps running and reports `old → new`) are variants; **baseline** is what a change Check compares against.
- **replay cache**: steps, Check and Readouts a learned run recorded, replayed without the LLM. **miss** / **heal** are internal; users see "Recovering".
- **step**: one LLM call; replayed actions are not steps. **run cap** / **task cap** / **task total**: step and dollar limits and spend.
- **Readout**: a labelled element whose text is reported verbatim. **result message**: what a notification says about a goal run.
- **irreversible step**, **confirmation**, **remote approval** (answering from Telegram), **handoff** (user clears a CAPTCHA in the agent's Chrome).
- **sign-in secret**, **scrub**, **browser profile**, **reset**, **allowed domain**, **consent reject** / **consent banner**.
- **AI provider**: whoever runs the model, picked with the model in the client's Settings → AI.

## Sources

- Script repo at `40e65d5`: `src/main/resources/manifest.json`, `src/main/kotlin/com/cereal/script/aiagent/application/AgentConfiguration.kt`,
  `src/main/kotlin/com/cereal/script/aiagent/domain/Settings.kt`, `build.gradle.kts`, `gradle/libs.versions.toml`, `CONTEXT.md`,
  `RELEASENOTES.md`, `STORELISTING.md`, `docs/release.md`, `.github/workflows/release.yml`; issue #163.
- desktop-client: issues #47, #69, #116, #121, #130; PR #114; tag `cereal-client/1.15.0`;
  `cereal-client/src/main/java/com/cereal/client/application/script/ScriptSyncManager.kt`,
  `application/interactor/marketplace/InstallMarketplaceScriptInteractor.kt`.
