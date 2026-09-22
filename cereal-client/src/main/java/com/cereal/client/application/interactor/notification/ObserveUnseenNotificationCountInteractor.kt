package com.cereal.client.application.interactor.notification

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.notification.ObserveNotificationCenterInteractor.Companion.RECENT_LIMIT
import com.cereal.client.domain.repository.NotificationHistoryRepository
import com.cereal.client.domain.repository.NotificationSettingsRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Observes how many recent notifications are "unseen" — newer than the per-user `lastSeenAt`
 * watermark. Drives the sidebar Notification center badge.
 */
@OptIn(FlowPreview::class)
class ObserveUnseenNotificationCountInteractor(
    private val notificationHistoryRepository: NotificationHistoryRepository,
    private val notificationSettingsRepository: NotificationSettingsRepository,
) : FlowInteractor<Int, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Flow<Int> =
        combine(
            notificationHistoryRepository.observeRecent(RECENT_LIMIT),
            notificationSettingsRepository.getNotificationCenterLastSeenAt(),
        ) { notifications, lastSeenAt ->
            notifications.count { it.timestamp > lastSeenAt }
        }
}
