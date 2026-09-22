package com.cereal.client.domain.model.settings

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * User-controlled cadence for background proxy health checks.
 *
 * The probe traffic flows **through the user's paid proxy** (api.ipify.org GET),
 * so checking thousands of proxies aggressively can add up. This setting lets the
 * user dial it down — or shut it off entirely and only check on demand via the
 * "Test all" button on the group details pane.
 */
enum class ProxyHealthCheckInterval(
    val storageValue: String,
    val staleThreshold: Duration?,
) {
    OFF("off", null),
    EVERY_6_HOURS("every_6h", 6.hours),
    EVERY_24_HOURS("every_24h", 24.hours),
    ;

    companion object {
        val Default: ProxyHealthCheckInterval = OFF

        fun fromStorageValue(value: String?): ProxyHealthCheckInterval = entries.firstOrNull { it.storageValue == value } ?: Default
    }
}
