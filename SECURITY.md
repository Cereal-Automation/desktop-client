# Security policy

## Reporting a vulnerability

**Do not open a public issue for a security problem.**

Report privately through GitHub: open this repository's **Security** tab and
use **Report a vulnerability**. If that is unavailable to you, email
**info@cereal-automation.com** with `SECURITY` in the subject line.

Please include what you did, what happened, what you expected, and the version
or commit you tested. A proof of concept helps; a working exploit is not
required.

## What to expect

| | |
|---|---|
| Acknowledgement | within **5 business days** |
| Assessment and severity | within **10 business days** of acknowledgement |
| Fix or public disclosure | within **90 days** of the report |

This is a small project. Those are the windows we hold ourselves to, not a
contractual guarantee. If a report is valid we will tell you when a fix ships
and credit you in the release notes unless you would rather we didn't.

## Supported versions

Only the **latest released version** of the client is supported. The client
self-updates, so there are no maintained release branches.

## In scope

- The desktop client in this repository, in a default installation
- Remote code execution, privilege escalation, or path traversal reachable from
  a script, a marketplace response, or the auto-update flow
- Weaknesses in the auto-update chain: release-metadata signature verification
  or installer hash verification
- Exposure of another user's data, or of a user's credentials or session token
  to a party that should not have them
- Secrets committed to this repository

## Out of scope

These are known, understood, and deliberate. Reporting them will get a polite
"yes, we know", so we would rather state them here.

- **Patching the licence check out of a local build.** The client is open
  source; you can edit it. The gates that matter are server-side: downloading a
  paid script requires an active subscription, and each paid script verifies an
  RSA-signed licence verdict against a key embedded in the script itself. A
  patched client reaches neither.
- **Bypassing the script capacity limit.** Capacity is enforced client-side on
  the honour system. We know. It is the difference between subscription tiers,
  never access to the product.
- **Extracting the obfuscated keys built into the binary.** The client uses
  [Sekret](https://github.com/DatL4g/Sekret) to obfuscate fixed keys. Obfuscation
  is not secrecy, and the threat model has always assumed a determined reader of
  the binary can recover them. See "Encryption" in the README for what these keys
  do and do not protect.
- **Attacks that require an already-compromised machine** — anything reachable
  only by an attacker who can already read the user's home directory, attach a
  debugger, or run code as that user. The client cannot defend against its own
  host.
- **Findings from automated scanners with no demonstrated impact**, missing
  hardening headers on marketing pages, and reports about software we do not
  publish.
- **Denial of service** against Cereal Automation's own servers. Please don't.

## Third-party dependencies

If the vulnerability is in a dependency and already public, an issue is fine —
it is not a disclosure. Report it privately only if you have found a way to
reach it through this client that the upstream advisory does not describe.
