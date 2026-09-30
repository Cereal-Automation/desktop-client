package com.cereal.client.presentation.headless

import com.cereal.client.domain.model.notification.ChannelAttempt
import com.cereal.client.domain.model.notification.NotificationChannelType
import com.cereal.client.domain.model.notification.NotificationDeliveryStatus
import com.cereal.client.domain.repository.NotificationHistoryRepository
import com.cereal.client.domain.repository.NotificationSettingsRepository
import com.cereal.client.domain.repository.TasksRepository
import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Keys
import fixtures.aTask
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.Koin
import testutil.HeadlessTestScope
import testutil.runHeadlessTest

class HeadlessNotificationsTest {
    private val now = System.currentTimeMillis()

    @Test
    fun `rows show time, task, text and one mark per channel attempt, and Enter shows the errors`() =
        runHeadlessTest(
            seed = {
                get<TasksRepository>().addTask(aTask("t1", "com.a", running = false))
                get<NotificationHistoryRepository>().record(
                    "t1",
                    "Checkout",
                    "Order placed",
                    now - 5 * 60_000,
                    listOf(success(NotificationChannelType.DISCORD), failure(NotificationChannelType.TELEGRAM, "401 Unauthorized")),
                )
                get<NotificationHistoryRepository>().record("t1", null, "Newer", now, listOf(success(NotificationChannelType.EMAIL)))
            },
        ) {
            awaitText("1-5 tabs")
            press(CharKey('5'))

            val screen = awaitText("Order placed")
            val rows = screen.filter { "Test #1" in it }
            assertEquals(2, rows.size, screen.joinToString("\n"))
            assertTrue(rows[0].startsWith("›") && "Just now" in rows[0] && rows[0].endsWith("D- T- E✓"), rows[0])
            assertTrue("5m ago" in rows[1] && "Checkout: Order placed" in rows[1] && rows[1].endsWith("D✓ T✗ E-"), rows[1])

            press(Keys.Down, Keys.Enter)
            awaitText("Telegram: 401 Unauthorized")

            press(Keys.Enter)
            awaitScreen { lines -> lines.none { "401 Unauthorized" in it } }
        }

    @Test
    fun `label counts unseen notifications and lastSeenAt follows the newest while the tab is active`() =
        runHeadlessTest(
            seed = {
                get<TasksRepository>().addTask(aTask("t1", "com.a", running = false))
                record("First", now - 2_000)
                record("Second", now - 1_000)
            },
        ) {
            awaitText("5 Notifications (2)")

            press(CharKey('5'))
            awaitText("[5 Notifications]")
            awaitLastSeen(now - 1_000)

            // Arrivals while the tab is active never badge.
            koin.record("Third", now)
            awaitText("Third")
            awaitLastSeen(now)
            awaitText("[5 Notifications]")

            press(CharKey('1'))
            awaitText("[1 Tasks]")
            koin.record("Fourth", now + 1_000)
            awaitText("5 Notifications (1)")
            assertEquals(now, get<NotificationSettingsRepository>().getNotificationCenterLastSeenAt().first())
        }

    private suspend fun HeadlessTestScope.awaitLastSeen(timestamp: Long) {
        val lastSeenAt = get<NotificationSettingsRepository>().getNotificationCenterLastSeenAt()
        awaitScreen { runBlocking { lastSeenAt.first() } == timestamp }
    }

    private suspend fun Koin.record(
        message: String,
        timestamp: Long,
    ) = get<NotificationHistoryRepository>().record("t1", null, message, timestamp, listOf(success(NotificationChannelType.DISCORD)))

    private fun success(channel: NotificationChannelType) = ChannelAttempt(channel, NotificationDeliveryStatus.SUCCESS, null, null)

    private fun failure(
        channel: NotificationChannelType,
        error: String,
    ) = ChannelAttempt(channel, NotificationDeliveryStatus.FAILURE, null, error)
}
