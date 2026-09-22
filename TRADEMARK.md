# Trademark policy

The Cereal client is licensed under [Apache-2.0](LICENSE). Section 6 of that
licence is explicit: it grants copyright and patent rights, and **no trademark
rights**. This document states what that means in practice.

## Reserved marks

Cereal Automation reserves the following, whether or not they are registered:

- The word marks **Cereal** and **Cereal Automation**
- The Cereal logo and application icons, in every form and file in this
  repository (`cereal-client/resources/**`, `cereal-client/src/main/composeResources/**`)
- The domain **cereal-automation.com** and the product identifiers derived from
  it, including the bundle and package identifiers `com.cereal-automation.*`

The icon and logo files stay under the Apache-2.0 grant along with the rest of
the repository — they are not carved out. Copyright permission to copy a file is
simply not permission to use the mark it depicts, and this section is what
governs the latter.

## If you fork

You may fork, modify, and redistribute this software under Apache-2.0. If you
distribute your fork to anyone else, you must rename it:

- Choose a name that is not **Cereal**, **Cereal Automation**, or confusingly
  similar to either.
- Replace the logo and application icons with your own.
- Change the bundle, package, and installer identifiers so they do not use the
  `com.cereal-automation` namespace. In practice this means the `packageName`,
  `vendor`, `copyright`, and `menuGroup` values in
  `cereal-client/cereal-client.gradle.kts`, and the icons they reference.
- Do not present your fork as official, endorsed by, or supported by Cereal
  Automation.

A private fork you never distribute is unaffected.

## What you may still do

Nominative use is fine and always was. Without asking us you may:

- State accurately that your project is "a fork of Cereal" or "based on Cereal".
- Name Cereal in articles, reviews, comparisons, talks, and documentation.
- Use the name to describe interoperability — "compatible with Cereal scripts".

The line is straightforward: describing a relationship to Cereal is fine;
implying you *are* Cereal is not.

## Questions

Ask at info@cereal-automation.com before you ship, not after.
