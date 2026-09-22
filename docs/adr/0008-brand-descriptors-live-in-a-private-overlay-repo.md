# 8. Brand descriptors live in a private overlay repository

Date: 2026-09-03

## Status

Accepted

## Context

This repository is being published under Apache-2.0. The white-label **build
mechanism** ([ADR-0003](./0003-white-label-brands-data-driven.md)) is part of the
code a reader has to understand, so it stays public. The **Brand descriptors** it
consumes are not: a descriptor names the marketplace scripts an appliance is
locked to, its paywall deep-link, its update-feed path, and its bundle identity.
Together those disclose the appliance strategy and the paywall structure, which
are commercially sensitive.

The awkward part is that development *moves to* the public repository. Deleting
`cereal-client/brands/` is trivial; keeping branded builds buildable afterwards is
the actual decision.

Constraints that narrow it:

- A Brand is a **directory, not a file**. `brand.properties` may sit beside
  `icon-mac.icns` / `icon-windows.ico` / `icon-linux.png`, which are binary.
- The descriptor format is a **contract**: ADR-0003 records that changing its
  shape means touching every Brand folder.
- There is more than one Brand eventually — that is the entire point of ADR-0003.
- The public build must not reference something that is not there. It already
  doesn't: with `-Pbrand` unset, no descriptor is read and `brandAsset()` falls
  through to the stock icons.

## Decision

Brand descriptors live in a **private overlay repository**, checked out into
`cereal-client/brands/`, which this repository **gitignores**.

1. **A repository, not a secret.** The overlay carries binary icons natively, is
   reviewable and versioned — appropriate for a format ADR-0003 calls a contract —
   and adding the Nth Brand costs a directory rather than a secret. Rejected:
   **a CI secret materialised at build time** — it is text-only, so icons would
   need base64 smuggling, and it needs one secret per Brand, with no history and
   no review of a change to the contract.

2. **Gitignored, not merely absent.** `cereal-client/brands/` is in
   `.gitignore`, so the overlay checkout is invisible to the public repository and
   a descriptor cannot be re-committed by accident. Rejected: **a gitignored
   directory populated out of band with no defined source** — that is this
   decision minus the versioning, minus the review, and minus anyone but the
   author being able to reproduce a branded build.

3. **The overlay owns the Brand-authoring workflow.** How to add a Brand and how
   to run one locally are tasks performed *in* the overlay, so its README owns
   them. `docs/white-label-brands.md` is removed from this repository rather than
   retained as a pointer to a repository most readers cannot see.

4. **Stock builds never depend on the overlay.** No `-Pbrand`, no descriptor read.
   A missing or unknown Brand fails fast at configuration time with an error that
   names the expected path and the overlay checkout as the likely cause.

## Consequences

**Positive**

- The public repository carries the mechanism and the reasoning (ADR-0003, 0004)
  without the commercially sensitive content.
- Descriptor changes get history and review, which the contract warrants.
- A fresh public clone builds stock Cereal with no overlay present.

**Negative / costs**

- A branded build now has a prerequisite step: clone the overlay into
  `cereal-client/brands/`. Forgetting it is a configuration-time failure, not a
  silent stock build, because `-Pbrand` is explicit.
- Wiring a branded build into CI ([ADR-0003] Phase 5, never done — no workflow
  passes `brand` today) gains an `actions/checkout` of a second, private
  repository and the token to read it.
- Two repositories must stay in step when the descriptor format changes. The
  format is deliberately additive-with-defaults (`brand(key, default)`), which
  keeps most changes one-sided.
