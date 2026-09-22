# Development guide

Working on the Cereal client itself. For what the application is and how to run it
as a user, see the [README](../README.md).

## Prerequisites

* Java 17 and Java 21 (both are required — `cereal-licensing` uses 17, the rest use 21). [Manage multiple versions](https://stackoverflow.com/a/26252993)

## Installation

* Clone this repository

## Usage

* Run the client: `./gradlew run` or with proguard enabled: `./gradlew runRelease`.

### Hot reload

For a dev run with [Compose Hot Reload](https://github.com/JetBrains/compose-hot-reload) (code changes reload into the
running window):

```bash
./gradlew :cereal-client:hotRun --mainClass=com.cereal.client.presentation.MainKt
```

`hotRun` requires the **JetBrains Runtime (JBR) 21** specifically — a plain JDK 21 will fail with
`Cannot find a Java installation ... matching {languageVersion=21, vendor=JetBrains}`. If you have IntelliJ IDEA
installed, it already bundles a JBR you can point Gradle at (no extra download). Add its path to your **user-level**
`~/.gradle/gradle.properties` (not the repo):

```properties
org.gradle.java.installations.paths=/path/to/IntelliJ IDEA.app/Contents/jbr/Contents/Home
```

Verify Gradle detects it with `./gradlew javaToolchains` (look for a `JetBrains` vendor entry). If you don't have a JBR,
download one from [JetBrains/JetBrainsRuntime](https://github.com/JetBrains/JetBrainsRuntime/releases).

## Release

Releases are created by the GitHub Actions pipeline when a tag is created. The tag name is used as version.

### Generating Release Notes

You can generate Discord-friendly release announcements using the Makefile:

```bash
make release-notes FROM=cereal-client/1.0.0 TO=cereal-client/1.1.0
```

This command uses GitHub Copilot CLI to analyze the code changes between two tags and generate a user-friendly release
announcement.

**Prerequisites:**

- git (for accessing repository history)
- GitHub Copilot CLI (install from: https://github.com/github/gh-copilot)

For more information about available commands, run:

```bash
make help
```

### Tagging

When a git tag is created a release is build. Tags should follow this convention, depending on which module you want to
build:

* Cereal Client: `cereal-client/0.1.0`
* Licensing: `cereal-licensing/0.1.0`

Where 0.1.0 must be replaced with the version you want to use. In case of libraries published to maven, this version is
used as library version and can be referenced by any script that uses these libraries.

### Manually create maven artifacts

To manually create a library on your local machine run (and replace `[module_name]` and `[version_number]`): `./gradlew [module_name]:publishUnobfuscatedPublicationToLocalBuildRepository -Pversion=[version_number]`.

### Testing a release

There are some things that can easily go wrong that need to be checked before actually releasing the new version:

* Do a test run to verify the app can actually start. It may happen that ProGuard did too much obfuscation causing an
  issue when loading classes. To build an obfuscated version locally execute:
  `./gradlew cereal-client:packageReleaseDmg -Pversion='100.0.0' -Pdebug=false -Pflavor=prod -Penvironment=production`.
* Test backwards compatibility by creating an acceptance build from the same code that's on the production and
  another build from the latest development code.

## Encryption

What the encryption does and does not protect is described in the
[README](../README.md#encryption). The operational recipes are below.

### Creating new obfuscated secrets

[Sekret](https://github.com/DatL4g/Sekret) is used to create obfuscated keys. The keys are defined in
`cereal-client/sekret.properties` and for local development all set to a value 'debug'. The actual encryption keys are
configured in the CI/CD pipeline.

To add a key:

* Add a key (with debug value) to `sekret.properties`.
* Run `./gradlew cereal-client:generateSekret`
* Run `./gradlew cereal-client:createAndCopySekretNativeBinary`.
* The add key is now available in the code and can be used: `Sekret.myKey(BuildConfig.SEKRET_KEY)`
* Add the same key with a real value to the SEKRET_PROPERTIES secret in Github actions and store it in Enpass.

### Development & encryption

When configuring the environment as 'local' the encryption is disabled. This means that when you want to put script jars
in the Script folder that they must be unencrypted.
