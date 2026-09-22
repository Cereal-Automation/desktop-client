package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.repository.LogEventRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.database.LogEventDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class LogEventRepositoryImpl(
    private val logEventDataSource: LogEventDataSource,
    private val userSession: UserSession,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : LogEventRepository {
    override fun persist(event: LoggingEvent) {
        scope.launch {
            runCatching {
                val user = userSession.requireUser()
                logEventDataSource.insertLogEvent(user, event.tag, event.priority, event.message, event.timestamp)
            }.onFailure { if (it is CancellationException) throw it }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeLogEvents(taskId: String): Flow<List<LoggingEvent>> =
        userSession.getUserFlow().flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                logEventDataSource.observeLogEvents(user, taskId)
            }
        }
}
