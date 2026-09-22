package com.cereal.client.application.interactor.notification

import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.NotificationSettingsRepository

/**
 * Advances the per-user `lastSeenAt` watermark to [Params.timestamp] (the newest notification the
 * user is currently looking at). Called when the Notification center opens and again whenever a
 * newer notification arrives while it stays the active screen, so on-screen arrivals never re-badge.
 *
 * The watermark only ever moves forward; an older timestamp is ignored.
 */
class MarkNotificationsSeenInteractor(
    private val notificationSettingsRepository: NotificationSettingsRepository,
) : Interactor<Unit, MarkNotificationsSeenInteractor.Params>() {
    override suspend fun run(params: Params) {
        notificationSettingsRepository.setNotificationCenterLastSeenAt(params.timestamp)
    }

    data class Params(
        val timestamp: Long,
    )
}
