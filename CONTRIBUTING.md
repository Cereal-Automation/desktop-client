# Contributing

This repository is open source so it can be read, audited, and built by anyone —
particularly by the script authors who target it. Contributions are welcome, but
be aware of the shape of the project before you invest time in a large change:

- It is developed by one person. There is no roadmap, no triage SLA, and no
  guarantee that a pull request will be reviewed quickly.
- The architecture is strict. `CLAUDE.md` describes a Clean Architecture layout
  with hard layer boundaries, and a change that crosses them will not be merged
  regardless of how well it works.
- Large or structural changes should start as an issue. A pull request that
  arrives without one may be declined on direction alone, which wastes your time
  more than ours.

Bug reports, build fixes, documentation corrections, and small well-scoped
improvements are the contributions most likely to land.

## Developer Certificate of Origin

Every commit must be signed off. We use the
[Developer Certificate of Origin 1.1](https://developercertificate.org/) — not a
CLA. You keep your copyright; you assert that you have the right to submit the
work under this project's licence.

Add the sign-off with `-s`:

```bash
git commit -s -m "Fix the thing"
```

which appends a line to the commit message:

```
Signed-off-by: Your Name <your.email@example.com>
```

Use your real name and a real email address. To sign off a series you already
wrote, `git rebase --signoff <base>`.

By signing off you certify the DCO, reproduced in full below.

> **Developer Certificate of Origin 1.1**
>
> By making a contribution to this project, I certify that:
>
> (a) The contribution was created in whole or in part by me and I have the right
> to submit it under the open source license indicated in the file; or
>
> (b) The contribution is based upon previous work that, to the best of my
> knowledge, is covered under an appropriate open source license and I have the
> right under that license to submit that work with modifications, whether
> created in whole or in part by me, under the same open source license (unless
> I am permitted to submit under a different license), as indicated in the file;
> or
>
> (c) The contribution was provided directly to me by some other person who
> certified (a), (b) or (c) and I have not modified it.
>
> (d) I understand and agree that this project and the contribution are public
> and that a record of the contribution (including all personal information I
> submit with it, including my sign-off) is maintained indefinitely and may be
> redistributed consistent with this project or the open source license(s)
> involved.

## Before you open a pull request

Run the checks the CI runs:

```bash
./gradlew ktlintCheck :cereal-client:detekt test
```

`./gradlew ktlintFormat` fixes most formatting violations. Lint runs with
`allWarningsAsErrors`, so a warning fails the build.

Tests use JUnit 5 and prefer real in-memory fakes over mocks — see the testing
section of `CLAUDE.md` before writing new ones.

## Licence

Contributions are accepted under [Apache-2.0](LICENSE), the licence this project
is distributed under. See [SECURITY.md](SECURITY.md) for reporting
vulnerabilities — do not report those as pull requests.
