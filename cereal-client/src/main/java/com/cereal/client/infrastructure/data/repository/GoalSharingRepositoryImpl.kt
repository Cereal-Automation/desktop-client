package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.repository.GoalSharingRepository
import com.cereal.client.infrastructure.bootstrap.BootstrapPreferenceKey
import com.cereal.client.infrastructure.bootstrap.BootstrapPreferences

/** Keeps goal sharing in the [BootstrapPreferences] store, next to crash reporting, so it holds signed out. */
class GoalSharingRepositoryImpl(
    private val preferences: BootstrapPreferences,
) : GoalSharingRepository {
    override fun isEnabled(): Boolean = preferences.get(BootstrapPreferenceKey.GoalSharingEnabled)

    override fun setEnabled(enabled: Boolean) {
        preferences.set(BootstrapPreferenceKey.GoalSharingEnabled, enabled)
    }

    override fun isNoticeSeen(): Boolean = preferences.get(BootstrapPreferenceKey.GoalSharingNoticeSeen)

    override fun setNoticeSeen() {
        preferences.set(BootstrapPreferenceKey.GoalSharingNoticeSeen, true)
    }

    override fun installId(): String = preferences.installId()
}
