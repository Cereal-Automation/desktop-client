package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.repository.LogEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [LogEventRepository] for tests. Log events per task can be seeded via [seed] so that
 * log-output UI can be exercised. [persist] is a no-op.
 */
class InMemoryLogEventRepository : LogEventRepository {
    private val events = mutableMapOf<String, MutableStateFlow<List<LoggingEvent>>>()

    fun seed(
        taskId: String,
        loggingEvents: List<LoggingEvent>,
    ) {
        flowFor(taskId).value = loggingEvents
    }

    override fun persist(event: LoggingEvent) = Unit

    override fun observeLogEvents(taskId: String): Flow<List<LoggingEvent>> = flowFor(taskId)

    private fun flowFor(taskId: String) = events.getOrPut(taskId) { MutableStateFlow(emptyList()) }
}
