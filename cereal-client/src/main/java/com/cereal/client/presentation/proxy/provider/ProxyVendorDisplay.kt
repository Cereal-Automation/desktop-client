package com.cereal.client.presentation.proxy.provider

import com.cereal.client.domain.model.proxy.ProxyVendor

/**
 * Presentation-layer display metadata for a [ProxyVendor].
 *
 * The domain [ProxyVendor] enum carries no user-facing copy (see `model/script/ScriptCapacity.kt`);
 * the branding shown in the UI — name, brand mark, brand colour, and marketing tagline — lives here in
 * the presentation layer and is resolved per vendor via [display].
 *
 * @property displayName Human-readable vendor name shown in the UI.
 * @property brandMark Short brand letters rendered on the vendor's brand tile.
 * @property brandColorHex Brand accent colour as a hex string (e.g. "#E54848").
 * @property tagline Short marketing line shown under the vendor name in the picker.
 */
data class ProxyVendorDisplay(
    val displayName: String,
    val brandMark: String,
    val brandColorHex: String,
    val tagline: String,
)

/** Resolves the presentation branding for this [ProxyVendor]. */
val ProxyVendor.display: ProxyVendorDisplay
    get() =
        when (this) {
            ProxyVendor.MARSPROXIES -> {
                ProxyVendorDisplay(
                    displayName = "MarsProxies",
                    brandMark = "M",
                    brandColorHex = "#E54848",
                    tagline = "Residential & ISP · 9M+ IPs",
                )
            }

            ProxyVendor.BRIGHT_DATA -> {
                ProxyVendorDisplay(
                    displayName = "Bright Data",
                    brandMark = "BD",
                    brandColorHex = "#4F8CFF",
                    tagline = "Enterprise residential",
                )
            }

            ProxyVendor.OXYLABS -> {
                ProxyVendorDisplay(
                    displayName = "Oxylabs",
                    brandMark = "Ox",
                    brandColorHex = "#7C5CFC",
                    tagline = "Datacenter & residential",
                )
            }

            ProxyVendor.IPROYAL -> {
                ProxyVendorDisplay(
                    displayName = "IPRoyal",
                    brandMark = "IR",
                    brandColorHex = "#2ECC71",
                    tagline = "Royal residential pool",
                )
            }

            ProxyVendor.SMARTPROXY -> {
                ProxyVendorDisplay(
                    displayName = "Smartproxy",
                    brandMark = "Sp",
                    brandColorHex = "#F5B73A",
                    tagline = "Rotating residential",
                )
            }
        }
