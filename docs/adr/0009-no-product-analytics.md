# The client ships no product analytics

---
Status: accepted
---

The client used to send five events (`app_start`, `login`, `register`, `logout`,
`script_execution_started`) to Google Analytics via the Measurement Protocol, with no consent
and no opt-out, carrying a stable authenticated `user_id` and a SHA-256 of the user's email
address. Ahead of open-sourcing we **removed the integration entirely** rather than putting it
behind an opt-in: four of the five events describe things the server already observes as
authenticated API calls, so the only data genuinely lost is which scripts get *run* locally.
Crash reporting is unaffected — it stays, with an opt-out in *Settings → Privacy*.

## Considered Options

- **Remove the integration (chosen)** — deletes the provider, data source, event definitions, the
  persistent client-ID preference, and the `GOOGLE_ANALYTICS_API_SECRET` build credential. No
  release build needs an analytics credential any more. Costs the script-run signal outright.
- **Gate behind strict opt-in (rejected)** — would have kept the capability at the price of a
  consent surface, a dependency on the bootstrap preference store (the first event fires before
  login), and a permanent third-party transfer in every release build. A default-off toggle shipped
  to a privacy-conscious automation audience yields a dataset small and self-selected enough that
  no decision could rest on it — the full engineering bill for noise.
- **Keep `script_execution_started` "anonymously" (rejected)** — a GA payload without `user_id`
  still carries a persistent client ID plus screen resolution, timezone, locale and a JVM
  fingerprint. That is a device fingerprint, still personal data, still requiring consent. The
  whole consent bill for one event.

## Consequences

- **Do not reintroduce a client-side analytics SDK.** If usage data is wanted again, the right home
  is a run-report endpoint on our own server: our infrastructure, our privacy policy, and the same
  endpoint would close the tier-capacity metering gap found by the
  enforcement audit, which established
  that no run reporting exists server-side today.
- `key_analytics_client_id` rows survive in existing installs' key-value stores. Nothing reads
  them; the key-value store is queried by explicit key, so they are inert and no migration is owed.
- Removing the only analytics call in `UserAuthManager.deauthenticate()` also removed a
  pre-deauthentication user lookup that existed solely to label the `logout` event.
