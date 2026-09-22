package com.cereal.client.application.interactor.notification

import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.NotificationSettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Reads the persisted per-user `lastSeenAt` watermark once. The Notification center captures this on
 * entry as the baseline for "unseen" row styling, then advances the watermark to the newest
 * notification — so the captured baseline reflects what was unseen since the user last looked.
 */
class GetNotificationCenterLastSeenAtInteractor(
    private val notificationSettingsRepository: NotificationSettingsRepository,
) : Interactor<Long, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Long = notificationSettingsRepository.getNotificationCenterLastSeenAt().first()
}
