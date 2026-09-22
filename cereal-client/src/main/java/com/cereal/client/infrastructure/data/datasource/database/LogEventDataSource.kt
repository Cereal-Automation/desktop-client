package com.cereal.client.infrastructure.data.datasource.database

import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.model.logging.LoggingPriority
import com.cereal.client.domain.model.user.User
import kotlinx.coroutines.flow.Flow
import java.util.Date

interface LogEventDataSource {
    suspend fun insertLogEvent(
        user: User,
        taskId: String,
        priority: LoggingPriority,
        message: String,
        timestamp: Date,
    )

    fun observeLogEvents(
        user: User,
        taskId: String,
    ): Flow<List<LoggingEvent>>
}
