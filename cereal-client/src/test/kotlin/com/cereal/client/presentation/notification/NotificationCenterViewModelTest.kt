package com.cereal.client.presentation.notification

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.notification.GetNotificationCenterLastSeenAtInteractor
import com.cereal.client.application.interactor.notification.MarkNotificationsSeenInteractor
import com.cereal.client.application.interactor.notification.ObserveNotificationAttemptsInteractor
import com.cereal.client.application.interactor.notification.ObserveNotificationCenterInteractor
import com.cereal.client.application.interactor.task.ObserveTasksInteractor
import com.cereal.client.domain.model.notification.ChannelAttempt
import com.cereal.client.domain.model.notification.NotificationChannelType
import com.cereal.client.domain.model.notification.NotificationDeliveryStatus
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryNotificationHistoryRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryNotificationSettingsRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import com.cereal.client.presentation.error.ErrorResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises [NotificationCenterViewModel] against the real interactor graph backed by in-memory
 * repositories, so the unseen-count / `lastSeenAt` watermark logic runs on headless CI (the
 * existing [NotificationCenterScreenTest] self-skips where rendering is unavailable).
 *
 * The `lastSeenAt` watermark is the contract under test:
 *  - on entry the VM captures the persisted watermark as the "unseen since you last looked" baseline,
 *  - then advances the watermark to the newest notification so the sidebar badge clears on open and
 *    on-screen arrivals never re-badge.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationCenterViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)

    private val notificationRepository = InMemoryNotificationHistoryRepository()
    private val preferenceRepository = InMemoryNotificationSettingsRepository()
    private val tasksRepository = InMemoryTasksRepository()

    private fun seed(
        taskId: String,
        title: String?,
        message: String,
        timestamp: Long,
        attempts: List<ChannelAttempt> = listOf(success(NotificationChannelType.DISCORD)),
    ) {
        runBlocking { notificationRepository.record(taskId, title, message, timestamp, attempts) }
    }

    private fun success(channel: NotificationChannelType) = ChannelAttempt(channel, NotificationDeliveryStatus.SUCCESS, payload = "ok", errorMessage = null)

    private fun failure(channel: NotificationChannelType) = ChannelAttempt(channel, NotificationDeliveryStatus.FAILURE, payload = null, errorMessage = "boom")

    private fun createViewModel(scope: CoroutineScope): NotificationCenterViewModel =
        NotificationCenterViewModel(
            scope = scope,
            dispatcherProvider = dispatcherProvider,
            observeNotificationCenterInteractor = ObserveNotificationCenterInteractor(notificationRepository),
            observeNotificationAttemptsInteractor = ObserveNotificationAttemptsInteractor(notificationRepository),
            getNotificationCenterLastSeenAtInteractor = GetNotificationCenterLastSeenAtInteractor(preferenceRepository),
            markNotificationsSeenInteractor = MarkNotificationsSeenInteractor(preferenceRepository),
            observeTasksInteractor = ObserveTasksInteractor(tasksRepository),
            errorResolver = ErrorResolver(),
        )

    @Test
    fun `loads notifications on entry and clears the loading flag`() =
        runTest(dispatcher) {
            seed("task-a", "Item in stock", "Found it", timestamp = 300L)

            val viewModel = createViewModel(CoroutineScope(dispatcher))
            advanceUntilIdle()

            assertFalse(viewModel.isLoading.value)
            assertEquals(1, viewModel.notifications.value.size)
            assertEquals(
                "Item in stock",
                viewModel.notifications.value
                    .first()
                    .title,
            )
        }

    @Test
    fun `exposes notifications newest-first across tasks`() =
        runTest(dispatcher) {
            seed("task-a", "Oldest", "1", timestamp = 100L)
            seed("task-b", "Newest", "2", timestamp = 300L)
            seed("task-c", "Middle", "3", timestamp = 200L)

            val viewModel = createViewModel(CoroutineScope(dispatcher))
            advanceUntilIdle()

            assertEquals(
                listOf("Newest", "Middle", "Oldest"),
                viewModel.notifications.value.map { it.title },
            )
        }

    @Test
    fun `marks notifications newer than the persisted watermark as unseen`() =
        runTest(dispatcher) {
            // The user last looked at t=150. Anything newer is unseen; anything at-or-older is seen.
            runBlocking { preferenceRepository.setNotificationCenterLastSeenAt(150L) }
            seed("task-a", "Seen", "old", timestamp = 100L)
            seed("task-b", "Also seen (boundary)", "boundary", timestamp = 150L)
            seed("task-c", "Unseen", "new", timestamp = 300L)

            val viewModel = createViewModel(CoroutineScope(dispatcher))
            advanceUntilIdle()

            val byTitle = viewModel.notifications.value.associateBy { it.title }
            assertFalse(byTitle.getValue("Seen").unseen)
            assertFalse(byTitle.getValue("Also seen (boundary)").unseen, "watermark is inclusive: > baseline is unseen")
            assertTrue(byTitle.getValue("Unseen").unseen)
            assertEquals(1, viewModel.unseenCount.value)
        }

    @Test
    fun `unseen count reflects every notification newer than the baseline`() =
        runTest(dispatcher) {
            runBlocking { preferenceRepository.setNotificationCenterLastSeenAt(100L) }
            seed("task-a", "Old", "x", timestamp = 100L)
            seed("task-b", "New 1", "x", timestamp = 200L)
            seed("task-c", "New 2", "x", timestamp = 300L)

            val viewModel = createViewModel(CoroutineScope(dispatcher))
            advanceUntilIdle()

            assertEquals(2, viewModel.unseenCount.value)
        }

    @Test
    fun `opening the center advances the persisted watermark to the newest notification`() =
        runTest(dispatcher) {
            runBlocking { preferenceRepository.setNotificationCenterLastSeenAt(0L) }
            seed("task-a", "A", "x", timestamp = 100L)
            seed("task-b", "B", "x", timestamp = 500L)

            createViewModel(CoroutineScope(dispatcher))
            advanceUntilIdle()

            val watermark = preferenceRepository.getNotificationCenterLastSeenAt().first()
            assertEquals(500L, watermark, "watermark should advance to the newest notification on open")
        }

    @Test
    fun `re-opening the center after the watermark advanced shows nothing unseen`() =
        runTest(dispatcher) {
            seed("task-a", "A", "x", timestamp = 100L)
            seed("task-b", "B", "x", timestamp = 300L)

            // First open: everything is unseen relative to the default 0 baseline, then the
            // watermark is advanced to 300.
            val first = createViewModel(CoroutineScope(dispatcher))
            advanceUntilIdle()
            assertEquals(2, first.unseenCount.value)

            // Second open: the persisted watermark is now 300, so the same notifications are seen.
            val second = createViewModel(CoroutineScope(dispatcher))
            advanceUntilIdle()
            assertEquals(0, second.unseenCount.value)
            assertTrue(second.notifications.value.none { it.unseen })
        }

    @Test
    fun `empty notification list yields an empty, not-loading, all-seen state`() =
        runTest(dispatcher) {
            val viewModel = createViewModel(CoroutineScope(dispatcher))
            advanceUntilIdle()

            assertFalse(viewModel.isLoading.value)
            assertTrue(viewModel.notifications.value.isEmpty())
            assertEquals(0, viewModel.unseenCount.value)
        }

    @Test
    fun `empty list leaves the persisted watermark untouched`() =
        runTest(dispatcher) {
            runBlocking { preferenceRepository.setNotificationCenterLastSeenAt(42L) }

            createViewModel(CoroutineScope(dispatcher))
            advanceUntilIdle()

            // No notifications means no newest timestamp, so the watermark is never advanced.
            assertEquals(42L, preferenceRepository.getNotificationCenterLastSeenAt().first())
        }

    @Test
    fun `toggling a row exposes its per-channel delivery attempts`() =
        runTest(dispatcher) {
            seed(
                "task-a",
                "Item in stock",
                "Found it",
                timestamp = 300L,
                attempts = listOf(success(NotificationChannelType.DISCORD), failure(NotificationChannelType.EMAIL)),
            )

            val viewModel = createViewModel(CoroutineScope(dispatcher))
            advanceUntilIdle()
            val id =
                viewModel.notifications.value
                    .first()
                    .id

            viewModel.onToggleExpand(id)
            advanceUntilIdle()

            assertEquals(id, viewModel.expandedId.value)
            val attempts = viewModel.expandedAttempts.value
            assertEquals(2, attempts.size)
            val discord = attempts.first { it.channel == NotificationChannelType.DISCORD }
            val email = attempts.first { it.channel == NotificationChannelType.EMAIL }
            assertTrue(discord.delivered)
            assertFalse(email.delivered)
            assertEquals("boom", email.errorMessage)
        }

    @Test
    fun `toggling the same row twice collapses it and clears the attempts`() =
        runTest(dispatcher) {
            seed("task-a", "Item in stock", "Found it", timestamp = 300L)

            val viewModel = createViewModel(CoroutineScope(dispatcher))
            advanceUntilIdle()
            val id =
                viewModel.notifications.value
                    .first()
                    .id

            viewModel.onToggleExpand(id)
            advanceUntilIdle()
            assertEquals(id, viewModel.expandedId.value)

            viewModel.onToggleExpand(id)
            advanceUntilIdle()

            assertNull(viewModel.expandedId.value)
            assertTrue(viewModel.expandedAttempts.value.isEmpty())
        }

    @Test
    fun `expanding a different row switches the expanded selection`() =
        runTest(dispatcher) {
            seed("task-a", "First", "x", timestamp = 300L)
            seed("task-b", "Second", "x", timestamp = 200L)

            val viewModel = createViewModel(CoroutineScope(dispatcher))
            advanceUntilIdle()
            val firstId = viewModel.notifications.value[0].id
            val secondId = viewModel.notifications.value[1].id

            viewModel.onToggleExpand(firstId)
            advanceUntilIdle()
            assertEquals(firstId, viewModel.expandedId.value)

            viewModel.onToggleExpand(secondId)
            advanceUntilIdle()
            assertEquals(secondId, viewModel.expandedId.value)
        }
}
