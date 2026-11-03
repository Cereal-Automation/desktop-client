# Failure reports from the AI browser agent are shared under one default-on Share diagnostics switch

---
Status: accepted
Amends: [ADR 0011](0011-goal-run-reports-to-our-server.md)
---

A goal run report says *that* a check failed, not *why*. To diagnose failures we need the **run trace** of the
check: each snapshot the model saw, each model turn, what each action did, replay and recovery events, the outcome
and cost. A **failure report** is the trace of a failed or user-flagged check, plus the trace of the learned check
whose replay cache it used, sent under the goal run report's `runId`. Snapshots are accessibility trees of pages the
user is signed in to, so a failure report carries more personal data than a goal ever did.

One switch, **Share diagnostics** in *Settings → Privacy*, covers both goal run reports and failure reports. It
replaces "Share my goals", keeps that setting's stored value, and is **on by default** for the same reason as 0011:
an opt-in would yield too few, too self-selected failures to fix anything with. Only failed or flagged checks send a
trace; a check that succeeds sends nothing beyond the goal run report.

## Masking happens in the script, once

- The agent script masks every trace node before anything is cut short (as `Browser.snapshot` already does for the
  task's secrets): the task's secrets, then the same patterns the client uses for goals (emails, Luhn-valid card
  numbers, phone numbers, URL query strings, fragments and user info).
- Form field values (textbox and combobox values) become `[value]`, except text the agent typed itself. Prefilled
  fields are where the user's own address and account details sit.
- Screenshots never enter the trace; only the fact that one was taken.
- The client does not mask the trace again, and **the server does not either**: traces upload straight to Spaces
  with a presigned URL, so the server never sees their bytes. This is a deliberate break from 0011's double masking;
  re-masking would mean a job that downloads, scrubs and rewrites every file.

Visible page text stays unmasked: it is what a diagnosis reads. Names, addresses and order history on that text can
leave the machine. We accept that residual risk on the strength of the limits below.

## Storage and retention

- A private diagnostics bucket of its own, separate from the script JAR bucket, readable only by maintainers.
- A Spaces lifecycle rule deletes traces 30 days after upload. No server-side prune and no per-user deletion: "gone
  within 30 days" answers an erasure request. The goal run report row keeps its link, which simply stops resolving.
- Keyed like goal run reports: install id and `runId`, no account details.

## Disclosure: the policy and the setting, no Home notice

There are no notices on Home. This ADR removes 0011's one-time goal sharing notice too. Disclosure is the privacy
policy and the Share diagnostics setting text. That is weaker than 0011's pairing; we accept it because traces leave
the machine only for failed or flagged checks, masked, for 30 days.

## Consequences

- From 0011, this supersedes the "Share my goals" setting, the one-time Home notice, and "only the goal and a few
  run facts leave the machine". The goal run report itself, its masking and its 12-month retention stand.
- The client release that ships this is gated on privacy policy text that says: page content from signed-in sites
  can be included, only for failed or flagged checks, masked as above, kept 30 days, used only to debug the agent,
  keyed by install id.
- The masking patterns now live in two repos (client for goals, agent script for traces) and must stay in step.
