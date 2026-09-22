package com.cereal.client.domain.model.proxy

/**
 * A proxy vendor that Cereal can connect to.
 *
 * Only [MARSPROXIES] is connectable in this slice; the remaining vendors are modelled so the UI can
 * present them as "coming soon" and so the vendor-agnostic abstraction is in place for later slices.
 *
 * This enum carries no user-facing copy: the presentation layer supplies the display name, brand mark,
 * brand colour, and tagline per vendor (see `ProxyVendorDisplay` under `presentation/proxy/provider`),
 * mirroring the pattern documented on `model/script/ScriptCapacity.kt`.
 *
 * @property available Whether the vendor can be connected to today.
 */
enum class ProxyVendor(
    val available: Boolean,
) {
    MARSPROXIES(available = true),
    BRIGHT_DATA(available = false),
    OXYLABS(available = false),
    IPROYAL(available = false),
    SMARTPROXY(available = false),
}
