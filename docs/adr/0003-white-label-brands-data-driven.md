# 3. White-label builds: data-driven Brands on a new build axis

Date: 2026-06-19

## Status

Accepted

## Context

We want to ship white-label builds of the Cereal client — rebranded, locked
single-product appliances (the first being an "Ecommerce Product Monitor") that
hide the marketplace and present as a standalone product. This is a reusable
mechanism, not a one-off: more Brands will follow.

The client already has a `mock`/`prod` build flavor (`-Pflavor`, surfaced as
`BuildConfig.FLAVOR`) that selects fakes vs. real datasources, and a
`CerealConfiguration` that is `open` with a `suffix` constructor parameter
(yielding `"Cereal$suffix"`). Neither was designed to carry a full alternate
identity.

## Decision

Introduce **Brand** as the white-label descriptor, selected at build time on a
**new axis orthogonal to flavor**.

1. **New `-Pbrand=<id>` axis, not another flavor.** Brand is independent of
   `mock`/`prod`: we want a `mock` ecommerce build for dev *and* a `prod` one to
   ship, so they cannot share one dimension. Unset `brand` = stock Cereal.
   Rejected: encoding brand into the flavor (would multiply into a `mock-ecommerce`
   / `prod-ecommerce` / … matrix on a knob that means something else).

2. **Data-driven descriptors, not code per brand.** A Brand is a `brands/<id>/`
   directory holding identity values (full app name, bundleID, installer
   packageName, marketplace/social URLs, update-feed path, paywall deep-link),
   color hexes, icon files, and the list of script identities it is locked to.
   Gradle reads `-Pbrand` to feed these into `BuildConfig` + `nativeDistributions`;
   a single Brand-aware `CerealConfiguration` + theme reads them at runtime.
   **Adding a Brand is a content task — a folder + assets, zero Kotlin.** Rejected:
   a hand-written `CerealConfiguration` subclass + theme + DI wiring per Brand
   (type-safe, but every new Brand becomes an engineering change). The small
   generic glue to turn hexes/paths into a Compose `ColorScheme` and bundle assets
   is written once.

   > Since [ADR-0008](./0008-brand-descriptors-live-in-a-private-overlay-repo.md),
   > `brands/` is gitignored and supplied by a private overlay repository — the
   > mechanism below is unchanged, but the descriptors are not in this repository.

3. **Full identity replacement, not a suffix.** A Brand supplies a *full* app
   name ("Ecommerce Product Monitor"), its own bundleID, packageName, and a
   dedicated home/data directory — so a white-label build installs and runs
   side-by-side with stock Cereal and other brands with fully separate data,
   sessions, and scripts dirs. The existing `suffix` mechanism is replaced.

4. **Fixed locked UI set in v1.** Every white-label build hides the *same*
   surface (marketplace, script management, browse/add-script links); the
   destination set is **not** per-Brand configurable. A Brand varies only identity
   + script list. Per-Brand UI config can be added later if a Brand needs it.

5. **Per-Brand update feed, shared signing key.** Each Brand build updates from
   its own feed path (e.g. `downloads.cereal-automation.com/<brand>/latest-<os>.json`)
   so a brand build never offers the stock installer as an "update," but reuses
   the one Cereal release signing key — there is no security reason to mint one per
   brand. Encryption keys (`databaseEncryptionKey`/`fileEncryptionKey`) also stay
   shared; separate home dirs already prevent data collision.

## Consequences

**Positive**

- Onboarding the Nth Brand is a content task, not an engineering task.
- One codebase, one release pipeline; the appliance is stock Cereal with entry
  points hidden and identity swapped.
- Brands coexist with stock and each other on a single machine.

**Negative / costs**

- The Brand descriptor format becomes a **contract**: changing its shape means
  touching every Brand folder. This is the hard-to-reverse part.
- Some branding (Compose colors, icons) is not pure data and needs generic glue
  to bind descriptor values into the theme and bundle assets.
- Brand × flavor is a real build matrix the CI must cover.

**Deferred (additive)**

- Per-Brand UI configuration (which destinations are visible).
- Per-Brand signing keys or encryption keys, if hard isolation is ever required.
