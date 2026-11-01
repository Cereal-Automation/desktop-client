# AI browser agent goal runs are reported to our own server, opt-out

---
Status: accepted
---

Users type what they want automated into the AI browser agent's goal composer in plain words. Each goal is a
direct statement of demand, and since [ADR 0009](0009-no-product-analytics.md) removed product analytics we have no
signal at all about what users try to automate. The client now sends a **goal run report** for every agent task
start, and its outcome when it finishes, to `telemetry/goal-runs` on our own Web app: the run-report endpoint 0009
named as the right home for usage data. Ordinary ready-made scripts are not reported.

Reporting is **on by default**, disclosed in the privacy policy, and can be switched off in *Settings → Privacy*
("Share my goals to help improve Cereal"), which takes effect immediately, including for runs already going.

## Why default-on, when 0009 rejected default-off for analytics

0009 rejected an opt-in because a default-off toggle shipped to a privacy-conscious audience yields a dataset too
small and self-selected to decide anything on. Free-text goals are worth collecting only with that same broad
coverage, so an opt-in would rebuild the problem 0009 described. We pair the default with:

- a one-time notice on Home, shown to every install (new ones too), linking to the setting;
- masking on the client (emails, Luhn-valid card numbers, phone numbers, URL query strings and fragments) and again
  on the server;
- only the goal and a few run facts leave the machine, never the rest of the task's configuration;
- records keyed by a pseudonymous install id (a random UUID in the bootstrap preferences), never an email. The
  user or guest token is sent when there is one, as on every API call, so the endpoint can rate-limit; the record
  itself stores no account details;
- 12-month retention, enforced by a scheduled prune on the server.

## Consequences

- Reporting is fire-and-forget: a failed report is logged and dropped, with no queue or retry, and never slows,
  blocks or fails a run. A run cut off by a crash or the app closing has a start record and no finish record.
- The client release that ships this is gated on the privacy policy text being live.
- The endpoint is a starting point for the tier-capacity metering gap 0009 mentions; metering is not part of this.
