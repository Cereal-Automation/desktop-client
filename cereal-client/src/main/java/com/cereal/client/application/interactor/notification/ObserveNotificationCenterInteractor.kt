package com.cereal.client.application.interactor.notification

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.notification.NotificationHistory
import com.cereal.client.domain.repository.NotificationHistoryRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

/**
 * Observes the most recent notifications across all of the current user's tasks, newest-first,
 * bounded by [RECENT_LIMIT]. Backs the global Notification center list.
 */
@OptIn(FlowPreview::class)
class ObserveNotificationCenterInteractor(
    private val notificationHistoryRepository: NotificationHistoryRepository,
) : FlowInteractor<List<NotificationHistory>, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Flow<List<NotificationHistory>> = notificationHistoryRepository.observeRecent(RECENT_LIMIT)

    companion object {
        /**
         * Safety belt independent of the retention window: the center never loads an unbounded list
         * even within the 30-day window after heavy task activity.
         */
        const val RECENT_LIMIT = 200
    }
}
