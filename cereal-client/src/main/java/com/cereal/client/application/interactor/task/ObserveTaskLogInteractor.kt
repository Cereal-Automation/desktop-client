package com.cereal.client.application.interactor.task

import com.cereal.client.application.FlowInteractor
import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.model.logging.LoggingPriority
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.repository.LogEventRepository
import com.cereal.client.domain.repository.TasksRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Date
import kotlin.time.ExperimentalTime

/** A task's log events merged with its status history, oldest first (DEBUG entries included). */
@OptIn(FlowPreview::class)
class ObserveTaskLogInteractor(
    private val logEventRepository: LogEventRepository,
    private val tasksRepository: TasksRepository,
) : FlowInteractor<List<LoggingEvent>, ObserveTaskLogInteractor.Params>() {
    override suspend fun run(params: Params): Flow<List<LoggingEvent>> =
        combine(logEventRepository.observeLogEvents(params.taskId), tasksRepository.getAllTasks()) { events, tasks ->
            val history = tasks.find { it.id == params.taskId }?.statusHistory.orEmpty()
            mergeLogEventsWithStatusHistory(events, history, params.taskId)
        }

    data class Params(
        val taskId: String,
    )
}

/** Merges status history into log events as entries: Error→ERROR, Running/Success→INFO, Idle→DEBUG. */
@OptIn(ExperimentalTime::class)
fun mergeLogEventsWithStatusHistory(
    logEvents: List<LoggingEvent>,
    statusHistory: List<TaskStatus>,
    taskId: String,
): List<LoggingEvent> {
    val statusEntries =
        statusHistory.mapNotNull { status ->
            val message = status.message ?: return@mapNotNull null
            LoggingEvent(
                priority =
                    when (status) {
                        is TaskStatus.Error -> LoggingPriority.ERROR
                        is TaskStatus.Running, is TaskStatus.Success -> LoggingPriority.INFO
                        is TaskStatus.Idle -> LoggingPriority.DEBUG
                    },
                tag = taskId,
                message = message,
                timestamp = Date(status.timestamp.toEpochMilliseconds()),
            )
        }
    return (logEvents + statusEntries).sortedBy { it.timestamp }
}
