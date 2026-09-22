package com.cereal.client.application.interactor.notification

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.notification.ChannelAttempt
import com.cereal.client.domain.model.notification.NotificationChannelType
import com.cereal.client.domain.model.notification.NotificationDeliveryStatus
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryNotificationHistoryRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryNotificationSettingsRepository
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ObserveUnseenNotificationCountInteractorTest {
    private val notifications = InMemoryNotificationHistoryRepository()
    private val preferences = InMemoryNotificationSettingsRepository()
    private val interactor = ObserveUnseenNotificationCountInteractor(notifications, preferences)

    private suspend fun record(timestamp: Long) {
        notifications.record(
            taskId = "task",
            title = "t$timestamp",
            message = "m$timestamp",
            timestamp = timestamp,
            attempts = listOf(ChannelAttempt(NotificationChannelType.SYSTEM, NotificationDeliveryStatus.SUCCESS, null, null)),
        )
    }

    @Test
    fun `counts only notifications newer than the lastSeenAt watermark`() =
        runTest {
            record(100L)
            record(200L)
            record(300L)
            preferences.setNotificationCenterLastSeenAt(150L)

            val count = interactor(Interactor.None()).first()

            assertEquals(2, (count as SuspendableResult.Success).value)
        }

    @Test
    fun `counts everything when nothing has been seen`() =
        runTest {
            record(100L)
            record(200L)

            val count = interactor(Interactor.None()).first()

            assertEquals(2, (count as SuspendableResult.Success).value)
        }

    @Test
    fun `counts nothing once the watermark reaches the newest notification`() =
        runTest {
            record(100L)
            record(200L)
            preferences.setNotificationCenterLastSeenAt(200L)

            val count = interactor(Interactor.None()).first()

            assertEquals(0, (count as SuspendableResult.Success).value)
        }
}
