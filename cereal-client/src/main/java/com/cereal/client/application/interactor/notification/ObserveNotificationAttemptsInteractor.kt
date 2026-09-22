package com.cereal.client.application.interactor.notification

import com.cereal.client.application.FlowInteractor
import com.cereal.client.domain.model.notification.NotificationHistoryAttempt
import com.cereal.client.domain.repository.NotificationHistoryRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

/**
 * Observes the per-channel delivery attempts for a single notification, in send order. Drives the
 * delivery-status detail shown when a Notification center row is expanded.
 */
@OptIn(FlowPreview::class)
class ObserveNotificationAttemptsInteractor(
    private val notificationHistoryRepository: NotificationHistoryRepository,
) : FlowInteractor<List<NotificationHistoryAttempt>, ObserveNotificationAttemptsInteractor.Params>() {
    override suspend fun run(params: Params): Flow<List<NotificationHistoryAttempt>> = notificationHistoryRepository.observeAttempts(params.notificationId)

    data class Params(
        val notificationId: String,
    )
}
