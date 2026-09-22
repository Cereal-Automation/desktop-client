package com.cereal.client.presentation.notification

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.notification.GetNotificationCenterLastSeenAtInteractor
import com.cereal.client.application.interactor.notification.MarkNotificationsSeenInteractor
import com.cereal.client.application.interactor.notification.ObserveNotificationAttemptsInteractor
import com.cereal.client.application.interactor.notification.ObserveNotificationCenterInteractor
import com.cereal.client.application.interactor.task.ObserveTasksInteractor
import com.cereal.client.domain.model.notification.NotificationDeliveryStatus
import com.cereal.client.domain.model.notification.NotificationHistory
import com.cereal.client.domain.model.notification.NotificationHistoryAttempt
import com.cereal.client.domain.model.task.Task
import com.cereal.client.presentation.error.ErrorResolver
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the global Notification center. Observes recent notifications across all of the current
 * user's tasks (newest-first), resolves each to its source task's name, and exposes them as UI
 * models. While this screen is active the per-user `lastSeenAt` watermark is advanced to the newest
 * notification, so the sidebar badge clears on open and never re-counts arrivals viewed on-screen.
 */
@OptIn(FlowPreview::class)
class NotificationCenterViewModel(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val observeNotificationCenterInteractor: ObserveNotificationCenterInteractor,
    private val observeNotificationAttemptsInteractor: ObserveNotificationAttemptsInteractor,
    private val getNotificationCenterLastSeenAtInteractor: GetNotificationCenterLastSeenAtInteractor,
    private val markNotificationsSeenInteractor: MarkNotificationsSeenInteractor,
    private val observeTasksInteractor: ObserveTasksInteractor,
    private val errorResolver: ErrorResolver,
) {
    private val _notifications = mutableStateOf<List<NotificationCenterUiModel>>(emptyList())
    val notifications: State<List<NotificationCenterUiModel>> = _notifications

    private val _isLoading = mutableStateOf(true)
    val isLoading: State<Boolean> = _isLoading

    private val _unseenCount = mutableStateOf(0)
    val unseenCount: State<Int> = _unseenCount

    private val _expandedId = mutableStateOf<String?>(null)
    val expandedId: State<String?> = _expandedId

    private val _expandedAttempts = mutableStateOf<List<NotificationDeliveryUiModel>>(emptyList())
    val expandedAttempts: State<List<NotificationDeliveryUiModel>> = _expandedAttempts

    val errorAction = errorResolver.errorAction

    private var attemptsJob: Job? = null

    init {
        scope.launch(dispatcherProvider.io) {
            // Baseline for "unseen" row styling: what was unseen the last time the user looked.
            // Captured once before we advance the watermark below.
            val seenBaseline =
                runCatching { getNotificationCenterLastSeenAtInteractor.run(Interactor.None()) }.getOrDefault(0L)

            combine(
                observeNotificationCenterInteractor(Interactor.None()).map { it.valueOrEmpty() },
                observeTasksInteractor(Interactor.None()).map { it.valueOrEmpty() },
            ) { history, tasks ->
                Snapshot(
                    items = history.map { it.toUiModel(tasks, seenBaseline) },
                    newestTimestamp = history.maxOfOrNull { it.timestamp },
                )
            }.collectLatest { snapshot ->
                withContext(dispatcherProvider.main) {
                    _notifications.value = snapshot.items
                    _unseenCount.value = snapshot.items.count { it.unseen }
                    _isLoading.value = false
                }
                // Hold the watermark at the newest notification for as long as the center is the
                // active screen, so live arrivals viewed here never re-badge.
                snapshot.newestTimestamp?.let { advanceLastSeen(it) }
            }
        }
    }

    fun onToggleExpand(notificationId: String) {
        if (_expandedId.value == notificationId) {
            collapse()
            return
        }
        _expandedId.value = notificationId
        _expandedAttempts.value = emptyList()
        attemptsJob?.cancel()
        attemptsJob =
            scope.launch(dispatcherProvider.io) {
                observeNotificationAttemptsInteractor(ObserveNotificationAttemptsInteractor.Params(notificationId))
                    .map { it.valueOrEmpty() }
                    .collectLatest { attempts ->
                        withContext(dispatcherProvider.main) {
                            _expandedAttempts.value = attempts.map { it.toUiModel() }
                        }
                    }
            }
    }

    private fun collapse() {
        _expandedId.value = null
        attemptsJob?.cancel()
        attemptsJob = null
        _expandedAttempts.value = emptyList()
    }

    private fun advanceLastSeen(timestamp: Long) {
        scope.launch(dispatcherProvider.io) {
            markNotificationsSeenInteractor(MarkNotificationsSeenInteractor.Params(timestamp))
        }
    }

    private fun NotificationHistory.toUiModel(
        tasks: List<Task>,
        seenBaseline: Long,
    ): NotificationCenterUiModel {
        val taskName =
            tasks
                .firstOrNull { it.id == taskId }
                ?.scriptInstance
                ?.packageInstance
                ?.definition
                ?.manifest
                ?.name
                .orEmpty()
        return NotificationCenterUiModel(
            id = id,
            taskId = taskId,
            taskName = taskName,
            title = title,
            message = message,
            relativeTime = formatRelativeTime(timestamp),
            unseen = timestamp > seenBaseline,
        )
    }

    private fun NotificationHistoryAttempt.toUiModel() =
        NotificationDeliveryUiModel(
            id = id,
            channel = channel,
            delivered = status == NotificationDeliveryStatus.SUCCESS,
            errorMessage = errorMessage,
        )

    private fun <T> SuspendableResult<List<T>, Exception>.valueOrEmpty(): List<T> =
        when (this) {
            is SuspendableResult.Success -> value
            is SuspendableResult.Failure -> emptyList()
        }

    private data class Snapshot(
        val items: List<NotificationCenterUiModel>,
        val newestTimestamp: Long?,
    )
}
