package com.cereal.client.infrastructure.provider.inmemory

import com.cereal.client.domain.provider.CrashReportingProvider

/**
 * Keeps the choice in memory and touches no reporting client, so the sandboxed `mock` flavor and
 * the screen tests can toggle the setting without a Sentry client or a file on disk.
 */
class InMemoryCrashReportingProvider(
    private var enabled: Boolean = true,
) : CrashReportingProvider {
    override fun isEnabled(): Boolean = enabled

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
