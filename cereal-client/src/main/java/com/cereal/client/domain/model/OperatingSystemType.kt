package com.cereal.client.domain.model

/**
 * The operating system the client runs on.
 *
 * This is a pure domain concept. The *current* OS is build-time configuration, so it is resolved in
 * the infrastructure layer and exposed via `ApplicationConfig.operatingSystem` — the domain must not
 * read `BuildConfig`.
 */
enum class OperatingSystemType {
    Windows,
    MacOS,
    Linux,
}
