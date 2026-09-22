package com.cereal.client.infrastructure.data.datasource.database.room

import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.ScriptTaskDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.entity.TaskExecutionStatus
import com.cereal.client.infrastructure.data.datasource.database.room.entity.TaskStatusEntity
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.ScriptInstanceMapper
import com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Room implementation of [ScriptTaskDataSource].
 */
class RoomScriptTaskDataSource(
    private val roomDatabases: RoomDatabases,
    private val scriptInstanceMapper: ScriptInstanceMapper,
) : ScriptTaskDataSource {
    override suspend fun addTask(
        user: User,
        task: Task,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            val dao = userDatabase.scriptInstanceDao()

            // Find the script entity for this task
            val scriptId = UUID.fromString(task.scriptInstance.id)

            // Create finished task entity
            val taskEntity = scriptInstanceMapper.createTaskEntity(task, scriptId)
            dao.insertTask(taskEntity)

            // Create configuration entities
            val configurationEntities =
                scriptInstanceMapper.createTaskConfigurationEntities(
                    task,
                    taskEntity.id,
                )
            dao.insertTaskConfigurations(configurationEntities)

            // Create status entities
            val statusEntities =
                scriptInstanceMapper.createTaskStatusEntities(
                    task,
                    taskEntity.id,
                )
            dao.insertTaskStatuses(statusEntities)
        }
    }

    override suspend fun removeTask(
        user: User,
        taskId: String,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            userDatabase
                .scriptInstanceDao()
                .deleteTaskById(UUID.fromString(taskId))
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun addStatusToTask(
        user: User,
        taskId: String,
        status: TaskStatus,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            val dao = userDatabase.scriptInstanceDao()
            val now = Clock.System.now()
            val statusEntity =
                TaskStatusEntity(
                    id = UUID.randomUUID(),
                    taskId = UUID.fromString(taskId),
                    message = EncryptedString.from(status.message),
                    stackTrace =
                        when (status) {
                            is TaskStatus.Error -> EncryptedString.from(status.stackTrace)
                            else -> null
                        },
                    timestamp = status.timestamp.toEpochMilliseconds(),
                    status =
                        when (status) {
                            is TaskStatus.Idle -> TaskExecutionStatus.IDLE
                            is TaskStatus.Running -> TaskExecutionStatus.RUNNING
                            is TaskStatus.Success -> TaskExecutionStatus.SUCCESS
                            is TaskStatus.Error -> TaskExecutionStatus.ERROR
                        },
                    createdAt = now,
                    updatedAt = now,
                )
            dao.insertTaskStatus(statusEntity)
        }
    }

    override suspend fun getJobTasksFromHistory(
        user: User,
        scriptInstance: ScriptInstance,
    ): List<JobTask> {
        val dao = roomDatabases.getUserDatabase(user).scriptInstanceDao()
        val scriptId = UUID.fromString(scriptInstance.id)
        val taskEntities = dao.getTasksByScriptId(scriptId)

        return taskEntities.map { taskEntity ->
            val configurationEntities = dao.getTaskConfigurationsByTaskId(taskEntity.id)
            val statusEntities = dao.getTaskStatusesByTaskId(taskEntity.id)

            scriptInstanceMapper.mapTask(
                taskEntity,
                configurationEntities,
                statusEntities,
                scriptInstance,
                user,
            )
        }
    }
}
