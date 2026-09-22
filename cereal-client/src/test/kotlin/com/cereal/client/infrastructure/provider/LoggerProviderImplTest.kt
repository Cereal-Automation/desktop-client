package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.model.logging.LoggingPriority
import com.cereal.client.domain.repository.LogEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoggerProviderImplTest {
    /** Captures the events the facade hands to the repository, so we can assert on formatting. */
    private class RecordingLogEventRepository : LogEventRepository {
        val persisted = mutableListOf<LoggingEvent>()

        override fun persist(event: LoggingEvent) {
            persisted += event
        }

        override fun observeLogEvents(taskId: String): Flow<List<LoggingEvent>> = emptyFlow()
    }

    private val logEventRepository = RecordingLogEventRepository()
    private val provider = LoggerProviderImpl(logEventRepository)

    @Test
    fun `info persists an INFO event`() =
        runTest {
            provider.info("com.pkg", "hello")

            val event = logEventRepository.persisted.single()
            assertEquals(LoggingPriority.INFO, event.priority)
            assertEquals("com.pkg", event.tag)
            assertEquals("hello", event.message)
        }

    @Test
    fun `error persists an ERROR event`() =
        runTest {
            provider.error("com.pkg", "oops", null)

            val event = logEventRepository.persisted.single()
            assertEquals(LoggingPriority.ERROR, event.priority)
            assertEquals("oops", event.message)
        }

    @Test
    fun `warning persists a WARNING event`() =
        runTest {
            provider.warning("com.pkg", "be careful")

            assertEquals(LoggingPriority.WARNING, logEventRepository.persisted.single().priority)
        }

    @Test
    fun `debug persists a DEBUG event`() =
        runTest {
            provider.debug("com.pkg", "trace details")

            assertEquals(LoggingPriority.DEBUG, logEventRepository.persisted.single().priority)
        }

    @Test
    fun `log with empty message and no throwable is skipped`() =
        runTest {
            provider.info("com.pkg", null)

            assertTrue(logEventRepository.persisted.isEmpty())
        }

    @Test
    fun `error with null message uses throwable stack trace as the message`() =
        runTest {
            provider.error("com.pkg", null, IllegalStateException("kaboom"))

            val message = logEventRepository.persisted.single().message
            assertTrue(message.contains("IllegalStateException"))
            assertTrue(message.contains("kaboom"))
        }

    @Test
    fun `info formats message with arguments`() =
        runTest {
            provider.info("com.pkg", "Hello %s, you have %d messages", "Alice", 3)

            assertEquals("Hello Alice, you have 3 messages", logEventRepository.persisted.single().message)
        }

    @Test
    fun `error appends stack trace to a non-empty message`() =
        runTest {
            provider.error("com.pkg", "something failed", RuntimeException("root cause"))

            val message = logEventRepository.persisted.single().message
            assertTrue(message.startsWith("something failed"))
            assertTrue(message.contains("root cause"))
        }
}
