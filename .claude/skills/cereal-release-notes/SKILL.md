---
name: cereal-release-notes
description: >
  Generates release notes for the Cereal client from a local git repository's commit history
  and formats them for five channels: a Discord announcement, an Instagram caption, an X (Twitter)
  thread, a formal markdown changelog entry, and a short blog post for cereal-automation.com/blog.
  Use this skill whenever someone mentions "release notes", "release", "changelog", "ship",
  "version", "what changed", "announce", "blog post", "Instagram post", "Discord post", "tweet"
  in the context of the Cereal client or any software release. Also trigger when the user asks
  to "publish", "write up", or "announce" a new version.
---

# Cereal Release Notes Skill

You help create release notes for the Cereal client by reading its git commit history and
turning that into polished content for five distinct channels.

## Golden rule: write for non-technical users

**This is the single most important constraint of this skill.** The people reading these
release notes are everyday Cereal users — not developers. They don't know (or care) what
OAuth, a context provider, a refactor, a race condition, a regression, or an API endpoint
is. They want to know what changed *for them*: what's new they can do, what used to be
broken and now works, and what feels nicer.

Every output — Discord, Instagram, X, the blog post, **and even the formal changelog** —
must read naturally to someone with zero coding background. If a sentence assumes
developer knowledge, rewrite it.

**Never put in the output (without translating):**
refactor, dependency, API, backend, frontend, context, provider, hook, middleware,
race condition, regression, payload, schema, endpoint, cache, hydration, build pipeline,
CI, CD, auth tokens, webhook, null pointer, stack trace, mutex, latency, throughput,
serialization, polyfill, tree-shaking, bundler, lint, type error.

**Translation patterns — internalize these:**

| Technical commit | Plain-language bullet |
|---|---|
| `refactor(auth): migrate to context API` | Logging in is faster and more reliable |
| `fix(MessageList): null pointer on empty thread` | Fixed an issue where opening an empty conversation could freeze the app |
| `perf(feed): debounce scroll handler` | Scrolling through your feed is smoother now |
| `feat(settings): add dark mode toggle` | You can now switch to dark mode in Settings |
| `fix: race condition in sync` | Fixed a rare bug where messages could appear out of order |
| `chore(deps): bump react to 18.3` | (omit, or roll into "behind-the-scenes improvements") |
| `feat(api): expose /v2/threads endpoint` | (omit unless user-facing — this is internal) |

**Rules of thumb:**
- Lead with the user benefit, not the implementation. ("Logging in is faster" — not "We rewrote the auth layer.")
- Describe symptoms, not internals. Users notice "messages disappeared" — they don't notice "null pointer in MessageList".
- Use everyday verbs: *added, fixed, made faster, smoothed out, cleaned up, now remembers, no longer crashes when…*
- Skip what users can't see. Internal-only changes (build, CI, tests, pure refactors) should be omitted from social posts and the blog post, and rolled into a single friendly line in the changelog like *"Behind-the-scenes improvements to keep things stable."*
- Read every line back as if you've never opened a code editor. If anything would make a non-coder squint, rewrite it.

## What you need from the user

Before doing anything, make sure you have:

1. **Repo path** — the local path to the Cereal client git repo (ask if not provided)
2. **Version range** — one of:
   - A version tag or range like `v1.2.0` or `v1.1.0..v1.2.0`
   - "since last tag" (use `git describe --tags --abbrev=0` to find it)
   - A number like "last 20 commits"
   - If the user gives you a version number but no explicit range, assume they mean from the previous tag to that tag

## Step 1: Extract the commit history

Run the appropriate git command to get the commit log. Use this format for clean parsing:

```bash
git -C <repo_path> log <range> --pretty=format:"%H|%s|%b" --no-merges
```

If getting commits since the last tag:
```bash
LAST_TAG=$(git -C <repo_path> describe --tags --abbrev=0 2>/dev/null || echo "")
if [ -z "$LAST_TAG" ]; then
  git -C <repo_path> log --pretty=format:"%H|%s|%b" --no-merges
else
  git -C <repo_path> log ${LAST_TAG}..HEAD --pretty=format:"%H|%s|%b" --no-merges
fi
```

Also grab the version name if you don't already have it:
```bash
git -C <repo_path> describe --tags --abbrev=0 2>/dev/null || git -C <repo_path> rev-parse --short HEAD
```

## Step 2: Parse and categorize the commits

The Cereal client uses Conventional Commits. Parse each commit subject into:

| Prefix | Category | Emoji |
|--------|----------|-------|
| `feat` | New features | ✨ |
| `fix` | Bug fixes | 🐛 |
| `perf` | Performance improvements | ⚡ |
| `refactor` | Code refactoring | ♻️ |
| `docs` | Documentation | 📝 |
| `chore` | Maintenance / dependencies | 🔧 |
| `style` | UI/visual changes | 🎨 |
| `test` | Tests | 🧪 |
| `ci` | CI/CD | 🚀 |
| `build` | Build system | 📦 |
| `revert` | Reverts | ⏪ |

Strip the prefix and scope from the subject (e.g. `feat(auth): add login button` → `add login button`).

For the **release notes audience** (non-technical users):
- `feat` and `fix` are the stars — these are what users actually feel.
- `perf` and `style` are worth mentioning when they're noticeable ("scrolling is smoother", "the buttons look cleaner").
- `refactor` should usually be **translated into a user-visible benefit** (e.g. "logging in is more reliable") or omitted entirely if there's nothing the user would notice.
- `chore`, `ci`, `build`, `test`, `docs` are internal plumbing — **leave them out of every output** except the formal changelog, where they collapse into a single friendly line ("Behind-the-scenes improvements").

Group commits by category. Within a category, keep the most impactful ones at the top (judge by the description — "add OAuth support" > "tweak button color"). And remember: the category label here is for *your* sorting, not for the user. The reader will see plain headings like "What's new" and "Fixes" — never "feat" or "perf".

## Step 3: Generate all five outputs

Create all five outputs and save them. Be thoughtful about tone — the Cereal brand is modern, clean, and community-oriented.

---

### Output 1: Discord announcement

**Tone:** Warm, casual, community-first. Like talking to a friend who uses the app — not a developer audience. Skip code-speak entirely.

**Format:**
```
🥣 **Cereal <version> is out!**

A quick rundown of what's new 👇

✨ **What's new**
- <feat summary>
- <feat summary>

🐛 **Fixes**
- <fix summary>
- <fix summary>

[Optional: ⚡ **Improvements** / 🎨 **Polish** if noteworthy]

[Optional: 1-2 sentence personal note about the release — what you're excited about, what's coming next]

---
Full changelog: [link if available]
```

Rules:
- Keep bullet points punchy (1 line each), in plain language a non-coder would use
- Lead each bullet with the *thing the user can now do* or the *thing that no longer breaks*, not the technical change
- If there are no bugs fixed, skip that section entirely rather than writing "none"
- Max ~400 words. Discord renders markdown, so **bold** and bullet lists work great.

---

### Output 2: Instagram caption

**Tone:** Friendly, visual, hype. Instagram is about the vibe — highlight only the headline features, in the kind of language you'd use telling a friend about an app you love. No tech jargon, ever.

**Format:**
```
<Hook line — something punchy about what's new>

<2-3 sentences highlighting the most exciting changes, in casual language>

<Optional: a "what this means for you" line>

<Relevant hashtags — 8-12, mix of broad (#opensource, #dev, #app) and specific (#cerealapp, #cereal)>
```

Rules:
- Under 2200 characters total
- No bullet points — write in flowing prose
- Emojis are encouraged but don't overdo it (3-6 total)
- Hashtags go at the end, on their own line or separated by a line break
- Don't mention internal/chore stuff — users don't care about CI fixes

---

### Output 3: X (Twitter) thread

**Tone:** Direct, punchy, *user-flavored*. X rewards concise threads, but our audience is people who use the app — not people who build apps. Keep the language plain. "Credible" here means specific and honest, not technical.

**Format:**
Each tweet is a separate item. Structure:

```
Tweet 1 (hook): "🥣 Cereal <version> just dropped. Here's what's new 🧵"

Tweet 2+: One tweet per major feature/fix. Lead with the punchline.
  - Each tweet ≤ 280 characters
  - End non-final tweets with a subtle continuation cue (e.g., nothing, or "→")
  - Use emoji sparingly (1 per tweet max)

Final tweet: "Full changelog: [link] — feedback welcome 🙌"
```

Rules:
- 3–7 tweets total (1 hook + 1-5 content + 1 closer)
- Focus on new features and fixes only — leave internal stuff out
- Be specific *in user terms* — "You can now log in with Google" beats both "Improved authentication" and "Added OAuth login" (the average reader doesn't know what OAuth is)
- Don't pad with filler tweets

---

### Output 4: Formal changelog

**Tone:** Clear, structured, factual — but still readable by a non-technical user. This is the canonical record, but our users *also* read it, so it must not drift into developer-speak. Think "well-organized release notes," not "engineering log."

**Format:**
```markdown
## [<version>] - <YYYY-MM-DD>

### Added
- <new thing the user can now do, in plain language>

### Fixed
- <what used to go wrong, in plain language, and that it's now fixed>

### Improved
- <something that now works better/faster/smoother — translated from refactor/perf>

### Removed
- <feature taken away, if any — and ideally why or what to use instead>

### Behind the scenes
- One short, friendly summary line covering all the internal plumbing changes
  (build, CI, tests, dependency bumps, internal refactors). Do not list these individually.
```

Rules:
- Follow [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) structure, but rename the "Changed" section to **"Improved"** and the "Internal" section to **"Behind the scenes"** so non-technical readers immediately understand what each section means.
- Every bullet must be in plain user language. If you can't translate a commit into something a non-coder would understand, it likely belongs in "Behind the scenes" — or doesn't belong at all.
- Do **not** dump every internal commit. Collapse all of them into a single friendly line under "Behind the scenes" (e.g. *"Various stability and maintenance improvements."*). Listing CI tweaks and dependency bumps individually clutters the changelog and confuses non-technical readers.
- Use present tense ("Add", "Fix" — not "Added", "Fixed") in bullet headings, but feel free to use natural prose in the body of bullets.
- Skip commit hashes by default — they're noise to non-technical readers. Only include them if the user explicitly asks.
- If there's a breaking change, call it out at the top of the entry under a clearly labeled **"⚠️ Heads up"** section, explained in plain language (what changed, what the user needs to do, if anything).

---

### Output 5: Blog post

**Tone:** Conversational but polished, written for everyday users — not engineers. The blog post lives on cereal-automation.com/blog and is the canonical announcement piece that other channels (Discord, Instagram, X) can link back to. It should be readable in under 2 minutes by someone who has never written a line of code.

**Format:**
```markdown
## Cereal <version> is out

[1–2 sentence intro: the headline of this release — what's the single most important thing that changed and why it matters to users.]

### What's new

[2–4 sentences walking through the biggest new features in plain, everyday language — described from the user's point of view, not the developer's. Group related features. Aggressively translate any jargon out. If there's a flagship feature, give it a short paragraph of its own and explain *why a regular user would care*.]

### Fixes and improvements

[1–3 sentences covering notable bug fixes and quality-of-life improvements, described as a user would notice them ("messages no longer get stuck in the outbox", "the app starts up about 20% faster"). Combine related items. Skip this section entirely if there's nothing user-visible to mention. Never describe what was changed in the code — only what the user will feel.]

[Optional: ### Breaking changes — only if there are any. Be explicit about migration steps.]

### How to update

Update through the Cereal desktop app — it'll prompt you on next launch, or you can check for updates manually from the settings menu.

[Optional closing line: 1 sentence about what's coming next or a thank-you to the community.]

[Full changelog: link if available]
```

Rules:
- 200–400 words. No filler. Every sentence earns its place.
- Skip internal/chore work entirely — that belongs only in the formal changelog
- Don't reuse Discord copy verbatim — the blog post is prose, not bullet lists
- Lead with the most exciting change, not a chronological recap
- Write at roughly an 8th-grade reading level. Short sentences. Familiar words.
- If you catch yourself using a developer term, ask: "could I say this without it?" Almost always, yes.
- For patch releases, keep it short (under 200 words is fine) and don't oversell
- For milestone releases (1.0, major versions), give it more weight — a slightly longer intro and a closing reflection are appropriate

---

## Step 4: Save the outputs

Save all five outputs to a single markdown file:

```
cereal-<version>-release-notes-<YYYY-MM-DD>.md
```

Structure the file with clear section headers for each platform. This makes it easy to copy-paste
the relevant section.

If the user has a workspace folder, save it there. Otherwise save to the current directory.

## Tips for good release notes

- **Group related fixes** — if 3 commits all fix login issues, combine them into one bullet ("Fixed a few login glitches that could leave you stuck on the loading screen").
- **Translate technical language aggressively** — "refactor AuthProvider to use context API" → "Logging in is smoother and more reliable." If you can't translate a commit into something a non-coder would care about, drop it from social posts and the blog, and roll it under "Behind the scenes" in the changelog.
- **Be honest about scope** — if it was a small patch, say so. Don't oversell.
- **Check for breaking changes** — look for `BREAKING CHANGE:` in commit bodies and call them out prominently. Phrase them as "Heads up: <what changed>. <What you need to do, if anything>." — never as raw migration notes.

## Final self-check before saving

Before writing the file out, re-read each output as if you've never written code in your life.
Ask yourself, for each bullet and sentence:

1. Would a friend who only uses apps (and has never coded) understand this on first read?
2. Does this lead with what changed *for the user*, not what changed *in the code*?
3. Are there any words that feel like jargon (the list at the top of this skill is a good reference)? If yes — rewrite.
4. Did any internal-only commits sneak into the social posts or the blog post? They shouldn't have.
5. Is the formal changelog free of bare commit hashes, file paths, and class/function names?

If any answer is "no", revise before saving. The whole point of this skill is producing
release notes that delight everyday users — not ones that read like a git log.
