package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.model.logging.LoggingPriority
import com.cereal.client.domain.model.notification.ChannelAttempt
import com.cereal.client.domain.model.notification.NotificationChannelType
import com.cereal.client.domain.model.notification.NotificationDeliveryStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Date

class SandboxTextFileRepositoriesTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `log events are appended to the text file and still observable`() =
        runTest {
            val file = File(dir, "nested/logs.txt")
            val repository = SandboxTextFileLogEventRepository(file)

            repository.persist(LoggingEvent(LoggingPriority.INFO, "task-1", "hello", Date(0)))
            repository.persist(LoggingEvent(LoggingPriority.ERROR, "task-1", "boom", Date(0)))

            assertEquals(
                listOf("1970-01-01T00:00:00Z [INFO] task-1: hello", "1970-01-01T00:00:00Z [ERROR] task-1: boom"),
                file.readLines(),
            )
            assertEquals(listOf("hello", "boom"), repository.observeLogEvents("task-1").first().map { it.message })
        }

    @Test
    fun `notifications are appended to the text file and still observable`() =
        runTest {
            val file = File(dir, "notifications.txt")
            val repository = SandboxTextFileNotificationHistoryRepository(file)

            repository.record(
                taskId = "task-1",
                title = "Title",
                message = "Body",
                timestamp = 0,
                attempts = listOf(ChannelAttempt(NotificationChannelType.DISCORD, NotificationDeliveryStatus.FAILURE, null, "429")),
            )

            assertEquals(listOf("1970-01-01T00:00:00Z task-1: Title | Body | DISCORD=FAILURE (429)"), file.readLines())
            assertTrue(repository.observeByTaskId("task-1").first().isNotEmpty())
        }
}
