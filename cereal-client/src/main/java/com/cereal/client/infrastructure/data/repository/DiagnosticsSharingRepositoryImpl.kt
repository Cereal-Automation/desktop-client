package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.repository.DiagnosticsSharingRepository
import com.cereal.client.infrastructure.bootstrap.BootstrapPreferenceKey
import com.cereal.client.infrastructure.bootstrap.BootstrapPreferences

/** Keeps Share diagnostics in the [BootstrapPreferences] store, next to crash reporting, so it holds signed out. */
class DiagnosticsSharingRepositoryImpl(
    private val preferences: BootstrapPreferences,
) : DiagnosticsSharingRepository {
    override fun isEnabled(): Boolean = preferences.get(BootstrapPreferenceKey.ShareDiagnosticsEnabled)

    override fun setEnabled(enabled: Boolean) {
        preferences.set(BootstrapPreferenceKey.ShareDiagnosticsEnabled, enabled)
    }

    override fun installId(): String = preferences.installId()
}
