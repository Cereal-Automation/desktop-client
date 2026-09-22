# Cereal

The desktop client for [Cereal Automation](https://www.cereal-automation.com) — a
Kotlin / Compose Multiplatform application that runs automation scripts on your
own machine.

Scripts are distributed through the Cereal marketplace; the client installs them,
configures them, schedules them, and reports what they do. Typical scripts watch
webshops for stock or price changes and notify you through Discord, email, or
Telegram. The client runs on macOS, Windows, and Linux.

This repository holds the client itself. The Cereal SDK that scripts are written
against is a separate, closed-source binary dependency, resolved from a public
Maven repository — you do not need access to it to build this project.

## Why this is public

So that it can be read. Cereal runs on your machine, logs into your accounts, and
stores your data locally under keys the client controls. Rather than ask you to
take our word for what it does with any of that, the code is here.

The secondary reason is script authors: the client is the runtime their scripts
execute inside, and reading it beats guessing at it.

We are not trying to build a contributor community. Pull requests are welcome —
see [CONTRIBUTING.md](CONTRIBUTING.md) — but there is no roadmap, no governance
model, and no triage commitment. Auditability is the goal.

## Building and running

A fresh clone builds and runs with **no secrets and no server access**. The
default build uses the `mock` flavor: every repository is an in-memory
implementation with sample data, so `./gradlew run` gives you a working
application window immediately.

Clone the repository, then:

```bash
./gradlew run
```

You need **Java 17 and Java 21** — `cereal-licensing` targets 17, everything else
targets 21.

Running against real servers requires credentials we do not distribute. That is
the only part of this repository you cannot exercise yourself.

Full developer setup, hot reload, release, and packaging instructions are in
[docs/development.md](docs/development.md). Architecture and conventions are in
[CLAUDE.md](CLAUDE.md); domain vocabulary is in [CONTEXT.md](CONTEXT.md), and
design decisions are recorded as ADRs in [docs/adr/](docs/adr/).

## Encryption

The client encrypts data at rest and in transit. Being precise about what that
buys you matters more in a public repository than it did in a private one:

| | |
|---|---|
| **Script JARs on disk** | Encrypted with a per-user key held server-side, combined with a fixed file key |
| **Local databases** | Specified columns encrypted; the application database uses a fixed key, the user database combines it with the per-user key |
| **Marketplace API** | HTTPS plus request/response signing |
| **Auto-update** | Release metadata is RSA-signed and the downloaded installer is SHA-256 verified before it is launched |

The fixed keys are obfuscated in the binary using
[Sekret](https://github.com/DatL4g/Sekret). **Obfuscation is not secrecy.** A
determined reader of the binary can recover them, and publishing this repository
does not change that — the threat model never assumed otherwise. What these keys
actually defend against is casual inspection of files on a shared or stolen
machine, not a motivated attacker with your disk.

The per-user key is the one that does real work, and it is not in the client: it
lives on the server and is issued to an authenticated session.

## What leaves your machine

Cereal talks to the network on its own initiative in exactly two places:

- **Our API** — signing in, your subscription, the marketplace catalogue, script
  downloads and update checks. This is the product; there is no opting out of it
  while staying signed in.
- **Crash reports**, to [Sentry](https://sentry.io), **and you can switch them
  off**: *Settings → Privacy → Send crash reports*. They are on by default. A
  report carries the error and its stack trace, your operating system, the app
  version, the path to your Cereal folder, and the names, sizes and checksums of
  your installed script files — not their contents. Switching the setting off
  stops the reporting client from starting at all, on this launch and every
  later one, rather than collecting reports and discarding them. The choice is
  stored in plain text in `bootstrap.properties` in your Cereal folder, so you
  can confirm it took effect without taking our word for it.

**There is no product analytics.** There was, and it was removed rather than
made opt-in — the client sends no usage events, no device fingerprint and no
install identifier.

Everything else on the wire is something you asked for: the scripts you run make
their own requests, notifications go to the Discord, Telegram or email channels
you configure, and a connected proxy provider is contacted with the token you
gave it. Script contents, passwords, proxies and your local databases are never
part of what Cereal itself sends.

## Security

Report vulnerabilities privately — see [SECURITY.md](SECURITY.md), which also
lists what is explicitly **out of scope**, including patching the licence check
out of your own build.

## Licence

Apache-2.0. See [LICENSE](LICENSE).

Third-party notices that the licence requires us to carry forward are in
[NOTICE](NOTICE); the complete dependency inventory, including the provenance of
every committed native binary and the reasoning on each copyleft or
dual-licensed dependency, is in [THIRD-PARTY.md](THIRD-PARTY.md).

The Apache licence grants no trademark rights. If you fork and distribute, you
must rename — see [TRADEMARK.md](TRADEMARK.md).

## A note on the history

Development of Cereal began in **2020**. This repository begins at the
open-source release, as a single commit — the earlier history is not public and
will not be. It documented a different product generation, and carrying it over
was not worth the review it would have required. So the large initial commit is
not concealment; it is six years of work arriving at once.
