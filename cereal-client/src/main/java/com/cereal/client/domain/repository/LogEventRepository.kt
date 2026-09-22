package com.cereal.client.domain.repository

import com.cereal.client.domain.model.logging.LoggingEvent
import kotlinx.coroutines.flow.Flow

/**
 * Owns persisted log events for the authenticated user: writing them and observing them per task.
 * The user-facing logging facade that formats and emits messages is a provider concern — see
 * [com.cereal.client.domain.provider.LoggerProvider], which delegates persistence here.
 */
interface LogEventRepository {
    /**
     * Persist a single log event for the current user. Fire-and-forget; never blocks the caller.
     */
    fun persist(event: LoggingEvent)

    fun observeLogEvents(taskId: String): Flow<List<LoggingEvent>>
}
