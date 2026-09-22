package com.cereal.client.infrastructure.data.datasource.database.room

import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.model.logging.LoggingPriority
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.LogEventDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.entity.LogEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import java.util.UUID

class RoomLogEventDataSource(
    private val roomDatabases: RoomDatabases,
) : LogEventDataSource {
    override suspend fun insertLogEvent(
        user: User,
        taskId: String,
        priority: LoggingPriority,
        message: String,
        timestamp: Date,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            userDatabase.logEventDao().insert(
                LogEventEntity(
                    id = UUID.randomUUID().toString(),
                    taskId = taskId,
                    priority = priority.name,
                    message = message,
                    timestamp = timestamp.time,
                ),
            )
            userDatabase.logEventDao().pruneExcess(taskId, MAX_LOG_EVENTS)
        }
    }

    override fun observeLogEvents(
        user: User,
        taskId: String,
    ): Flow<List<LoggingEvent>> =
        roomDatabases
            .getUserDatabase(user)
            .logEventDao()
            .observeByTaskId(taskId)
            .map { entities -> entities.map { it.toLoggingEvent() } }

    private fun LogEventEntity.toLoggingEvent() =
        LoggingEvent(
            priority = LoggingPriority.valueOf(priority),
            tag = taskId,
            message = message,
            timestamp = Date(timestamp),
        )

    companion object {
        private const val MAX_LOG_EVENTS = 500
    }
}
