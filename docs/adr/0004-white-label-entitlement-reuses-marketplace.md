# 4. White-label entitlement reuses marketplace accounts; scripts are downloaded, not bundled

Date: 2026-06-19

## Status

Accepted

## Context

A white-label build (see [ADR-0003](./0003-white-label-brands-data-driven.md)) is
a locked, rebranded appliance presenting as a standalone product. Two reasonable
expectations a future reader will bring to "white-label" turn out to be wrong
here, so they are worth recording:

- that such a product would have its *own* account system / licensing rather than
  authenticate against Cereal, and
- that a "bundled-script" appliance ships the script *inside* the build.

Today, running any script requires a logged-in user holding a marketplace
*subscription* to the script's `publicIdentifier` — enforced by
`ScriptLicenseChecker.isLicensed()` in `StartScriptInteractor`. Scripts are
downloaded from the marketplace at runtime as JARs and discovered on disk.

## Decision

White-label builds **reuse the existing account + subscription gate unchanged**,
and Brand scripts are **downloaded at runtime, not bundled in the build**.

1. **Same backend, same gate.** The appliance authenticates against the same
   `marketplace.cereal-automation.com` and entitlement is the existing
   per-`publicIdentifier` subscription check — untouched. Buying the Brand's
   package on the marketplace auto-subscribes the user server-side; on login the
   appliance reads those subscriptions and the gate passes. Rejected:
   *build-is-entitlement* (no auth, script implicitly licensed) and *offline
   signed license keys* (the `cereal-licensing` path) — both would fork the
   licensing model and forfeit Cereal's per-user revenue control. Cost: the
   "Cereal account" concept leaks through the veneer, mitigated by rebranding the
   auth screen and disabling guest login.

2. **Nothing is bundled.** Despite the framing, the build ships no script bytes.
   The Brand descriptor lists script *identities*; the appliance downloads and
   installs them at runtime through the normal marketplace flow, **auto-installing
   on login** (latest available version, silent updates) instead of requiring
   manual browsing. Rejected: committing JARs to the repo or pulling them as
   build-time artifacts — both couple script releases to client builds, and the
   runtime download path already exists and works.

3. **Seeding ≠ licensing.** Auto-install only places the script; the runtime
   subscription check still gates execution. The two stay cleanly separable: a
   logged-in but unsubscribed user gets the script installed but blocked at run.

4. **Missing subscription → branded paywall.** A logged-in user lacking a
   required subscription sees a branded screen that deep-links to the
   brand-skinned purchase page (target from the Brand descriptor). There is no
   marketplace-browse fallback — one targeted CTA, since one package buys the
   whole appliance.

## Consequences

**Positive**

- Zero new licensing or backend code — the client-side entitlement flow works as-is.
- Brand scripts stay current automatically; no client rebuild to ship a script bump.

**Negative / costs**

- The product authenticates against Cereal and depends on Cereal subscriptions —
  acceptable because Cereal owns the relationship, but it is the surprising part.
- The appliance is non-functional offline at first run until scripts download.
- "Bundled script" is a misnomer the glossary explicitly corrects to **Brand
  scripts** (see `CONTEXT.md`).
