package com.cereal.client.infrastructure.provider

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.provider.CrashReportingProvider
import com.cereal.client.infrastructure.bootstrap.BootstrapPreferenceKey
import com.cereal.client.infrastructure.bootstrap.BootstrapPreferences
import com.cereal.client.infrastructure.crashreporting.CrashReportingClient
import com.cereal.client.infrastructure.crashreporting.CrashReportingLifecycle

/**
 * Backs the crash-reporting choice with the pre-DI [BootstrapPreferences] store, and applies it to
 * the Sentry client straight away.
 *
 * The store, not [com.cereal.client.domain.repository.ApplicationPreferenceRepository], because
 * [com.cereal.client.App] has to read this choice before Koin and before a user is signed in —
 * see [BootstrapPreferences] for the full reasoning. Both ends must go through
 * [BootstrapPreferences.default] so the UI writes the file bootstrap reads.
 */
class CrashReportingProviderImpl(
    private val preferences: BootstrapPreferences,
    private val applicationConfig: ApplicationConfig,
    private val lifecycle: CrashReportingLifecycle = CrashReportingClient,
) : CrashReportingProvider {
    override fun isEnabled(): Boolean = preferences.get(BootstrapPreferenceKey.CrashReportingEnabled)

    override fun setEnabled(enabled: Boolean) {
        preferences.set(BootstrapPreferenceKey.CrashReportingEnabled, enabled)
        if (enabled) {
            lifecycle.start(applicationConfig.homeDirectory.path)
        } else {
            lifecycle.stop()
        }
    }
}
