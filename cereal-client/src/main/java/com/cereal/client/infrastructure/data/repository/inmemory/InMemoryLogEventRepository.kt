package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.repository.LogEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [LogEventRepository] for tests and the sandboxed (`mock`) flavor. [persist] keeps each
 * event under its tag (the task id, as in the Room-backed impl); events can also be seeded via [seed].
 */
class InMemoryLogEventRepository : LogEventRepository {
    private val events = ConcurrentHashMap<String, MutableStateFlow<List<LoggingEvent>>>()

    fun seed(
        taskId: String,
        loggingEvents: List<LoggingEvent>,
    ) {
        flowFor(taskId).value = loggingEvents
    }

    // ponytail: unbounded, fine for a dev session; cap per task if long runs eat memory.
    override fun persist(event: LoggingEvent) = flowFor(event.tag).update { it + event }

    override fun observeLogEvents(taskId: String): Flow<List<LoggingEvent>> = flowFor(taskId)

    private fun flowFor(taskId: String) = events.getOrPut(taskId) { MutableStateFlow(emptyList()) }
}
