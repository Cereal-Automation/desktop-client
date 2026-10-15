package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.model.notification.ChannelAttempt
import com.cereal.client.domain.repository.LogEventRepository
import com.cereal.client.domain.repository.NotificationHistoryRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLogEventRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryNotificationHistoryRepository
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * Where the sandboxed (`mock`) flavor mirrors log events and notifications as plain text, so they
 * can be tailed/grepped while developing (e.g. `tail -f ~/Cereal-Sandbox/logs.txt`).
 */
val sandboxOutputDirectory: File
    get() = File(System.getProperty("user.home"), "Cereal-Sandbox")

/** In-memory [LogEventRepository] that also appends every event to [file] as one line of text. */
class SandboxTextFileLogEventRepository(
    private val file: File = File(sandboxOutputDirectory, "logs.txt"),
    private val delegate: LogEventRepository = InMemoryLogEventRepository(),
) : LogEventRepository by delegate {
    override fun persist(event: LoggingEvent) {
        delegate.persist(event)
        // ponytail: synchronous append on the caller's thread; fine for dev volumes.
        file.appendLineSynchronized("${event.timestamp.toInstant()} [${event.priority}] ${event.tag}: ${event.message}")
    }
}

/** In-memory [NotificationHistoryRepository] that also appends every recorded notification to [file]. */
class SandboxTextFileNotificationHistoryRepository(
    private val file: File = File(sandboxOutputDirectory, "notifications.txt"),
    private val delegate: NotificationHistoryRepository = InMemoryNotificationHistoryRepository(),
) : NotificationHistoryRepository by delegate {
    override suspend fun record(
        taskId: String,
        title: String?,
        message: String,
        timestamp: Long,
        attempts: List<ChannelAttempt>,
    ) {
        delegate.record(taskId, title, message, timestamp, attempts)
        val channels = attempts.joinToString { "${it.channel}=${it.status}" + (it.errorMessage?.let { e -> " ($e)" } ?: "") }
        file.appendLineSynchronized("${Instant.ofEpochMilli(timestamp)} $taskId: ${title.orEmpty()} | $message | $channels")
    }
}

private val fileLock = Any()

private fun File.appendLineSynchronized(line: String) {
    synchronized(fileLock) {
        try {
            parentFile.mkdirs()
            appendText(line + System.lineSeparator())
        } catch (e: IOException) {
            // A dev-only mirror must never take down the logging/notification path it shadows.
            LoggerFactory.getLogger("SandboxTextFileRepositories").warn("Could not write to $this", e)
        }
    }
}
