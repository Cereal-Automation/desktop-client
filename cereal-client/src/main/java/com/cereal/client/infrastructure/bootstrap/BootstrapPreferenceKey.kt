package com.cereal.client.infrastructure.bootstrap

/**
 * The boolean settings that must be readable before dependency injection exists, with the value to
 * assume when the store has never been written (or cannot be read).
 *
 * Deliberately a short list. Anything that can wait until Koin is up belongs on
 * [com.cereal.client.domain.repository.ApplicationPreferenceRepository] instead — see
 * [BootstrapPreferences] for why this store exists at all.
 */
enum class BootstrapPreferenceKey(
    val key: String,
    val defaultValue: Boolean,
) {
    /**
     * Whether crash reports may be sent. Defaults to `true`: crash reporting is opt-*out*, so a user
     * who has never expressed a preference keeps it enabled.
     */
    CrashReportingEnabled("crash_reporting_enabled", defaultValue = true),
}
