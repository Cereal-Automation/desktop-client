package com.cereal.client.infrastructure.data.datasource.database.room.mapper

import com.cereal.client.application.script.ScriptInstanceFactory
import com.cereal.client.application.task.JobTaskFactory
import com.cereal.client.domain.model.script.ChildScriptInstance
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.room.dao.ScriptInstanceDao
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptConfigurationEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptConfigurationItemEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptPackageEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptPackageGroupEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptPackageGroupWithCountEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptParameterEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.TaskConfigurationEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.TaskEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.TaskExecutionStatus
import com.cereal.client.infrastructure.data.datasource.database.room.entity.TaskStatusEntity
import com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString
import com.cereal.sdk.component.script.ScriptParameters
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Room mapper for converting between script entities and domain models
 */
class ScriptInstanceMapper(
    private val keyValueMapper: KeyValueRoomMapper,
    private val scriptInstanceFactory: ScriptInstanceFactory,
    private val jobTaskFactory: JobTaskFactory,
) {
    @OptIn(ExperimentalTime::class)
    fun createScriptPackageGroupEntity(scriptPackageGroup: ScriptPackageGroup): ScriptPackageGroupEntity {
        val now =
            Clock.System
                .now()
        return ScriptPackageGroupEntity(
            id = UUID.fromString(scriptPackageGroup.id),
            name = scriptPackageGroup.name,
            createdAt = now,
            updatedAt = now,
        )
    }

    @OptIn(ExperimentalTime::class)
    fun updateScriptPackageGroupEntity(
        existing: ScriptPackageGroupEntity,
        scriptPackageGroup: ScriptPackageGroup,
    ): ScriptPackageGroupEntity =
        existing.copy(
            name = scriptPackageGroup.name,
            updatedAt =
                Clock.System
                    .now(),
        )

    suspend fun toDomain(
        entity: ScriptPackageGroupEntity,
        dao: ScriptInstanceDao,
    ): ScriptPackageGroup {
        val totalScriptPackages = dao.getScriptPackagesByGroupId(entity.id).size
        return ScriptPackageGroup(
            id = entity.id.toString(),
            name = entity.name,
            totalScriptPackages = totalScriptPackages,
        )
    }

    fun toDomain(entity: ScriptPackageGroupWithCountEntity): ScriptPackageGroup =
        ScriptPackageGroup(
            id = entity.id.toString(),
            name = entity.name,
            totalScriptPackages = entity.scriptPackageCount.toInt(),
        )

    @OptIn(ExperimentalTime::class)
    fun createScriptPackageEntity(
        scriptPackageInstance: ScriptPackageInstance,
        groupId: String,
    ): ScriptPackageEntity {
        val now =
            Clock.System
                .now()
        return ScriptPackageEntity(
            id = UUID.fromString(scriptPackageInstance.id),
            groupId = UUID.fromString(groupId),
            packageName = scriptPackageInstance.definition.manifest.packageName,
            numberOfConcurrentTasks = scriptPackageInstance.numberOfConcurrentTasks,
            createdAt = scriptPackageInstance.createdAt,
            updatedAt = now,
        )
    }

    @OptIn(ExperimentalTime::class)
    fun createScriptEntity(
        scriptInstance: ScriptInstance,
        packageId: UUID,
        parentScriptId: UUID? = null,
        childScriptIdentifier: String? = null,
    ): ScriptEntity {
        val now =
            Clock.System
                .now()
        return ScriptEntity(
            id = UUID.fromString(scriptInstance.id),
            packageId = packageId,
            scriptId = childScriptIdentifier,
            parentScriptId = parentScriptId,
            createdAt = scriptInstance.createdAt,
            updatedAt = now,
        )
    }

    @OptIn(ExperimentalTime::class)
    fun createScriptParameterEntities(
        scriptInstance: ChildScriptInstance,
        scriptId: UUID,
    ): List<ScriptParameterEntity> {
        val now =
            Clock.System
                .now()
        return scriptInstance.params?.getAll()?.map { (key, value) ->
            ScriptParameterEntity(
                id = UUID.randomUUID(),
                scriptId = scriptId,
                key = key,
                value = EncryptedString.from(keyValueMapper.valueToString(value)),
                type = keyValueMapper.getValueType(value),
                createdAt = now,
                updatedAt = now,
            )
        } ?: emptyList()
    }

    @OptIn(ExperimentalTime::class)
    fun createMainScriptConfigurationEntity(packageId: UUID): ScriptConfigurationEntity {
        val now =
            Clock.System
                .now()
        return ScriptConfigurationEntity(
            id = UUID.randomUUID(),
            packageId = packageId,
            scriptId = null,
            isMainConfiguration = true,
            createdAt = now,
            updatedAt = now,
        )
    }

    @OptIn(ExperimentalTime::class)
    fun createChildScriptConfigurationEntities(
        scriptPackageInstance: ScriptPackageInstance,
        packageId: UUID,
    ): List<ScriptConfigurationEntity> {
        val now =
            Clock.System
                .now()
        return scriptPackageInstance.childConfigurations.map { (scriptId, _) ->
            ScriptConfigurationEntity(
                id = UUID.randomUUID(),
                packageId = packageId,
                scriptId = scriptId,
                isMainConfiguration = false,
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    fun createScriptConfigurationItemEntities(
        configuration: ScriptConfigurationValues,
        configurationId: UUID,
    ): List<ScriptConfigurationItemEntity> {
        val now =
            Clock.System
                .now()
        return configuration.map { (key, value) ->
            ScriptConfigurationItemEntity(
                id = UUID.randomUUID(),
                configurationId = configurationId,
                key = key,
                value = EncryptedString.from(keyValueMapper.valueToString(value.raw)),
                type = keyValueMapper.getValueType(value.raw),
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun mapMainScript(
        scriptEntity: ScriptEntity,
        mainScriptConfigurationItems: List<ScriptConfigurationItemEntity>,
        scriptPackageInstance: ScriptPackageInstance,
        user: User,
    ): MainScriptInstance {
        val scriptConfigurationValues =
            keyValueMapper.mapConfigurationItemsFromEntities(
                mainScriptConfigurationItems,
                user,
                scriptPackageInstance.definition.mainScript.configuration,
            )

        return scriptInstanceFactory.createMainScriptInstance(
            scriptEntity.id.toString(),
            scriptPackageInstance.definition.mainScript,
            scriptConfigurationValues,
            scriptEntity.createdAt,
            scriptPackageInstance,
        )
    }

    @OptIn(ExperimentalTime::class)
    suspend fun mapChildScript(
        scriptEntity: ScriptEntity,
        scriptParameters: List<ScriptParameterEntity>,
        scriptPackageInstance: ScriptPackageInstance,
        parent: MainScriptInstance,
        user: User,
    ): ChildScriptInstance? {
        val scriptDefinition =
            scriptPackageInstance.definition.childScripts[scriptEntity.scriptId]
                ?: return null

        val configuration: ScriptConfigurationValues =
            scriptPackageInstance.childConfigurations[scriptEntity.scriptId!!]
                ?: emptyMap()

        val parameters =
            ScriptParameters().apply {
                mapping.putAll(
                    keyValueMapper.mapParametersFromEntities(
                        scriptParameters,
                        user,
                        scriptDefinition.configuration,
                    ),
                )
            }

        return scriptInstanceFactory.createChildScriptInstance(
            scriptEntity.id.toString(),
            scriptDefinition,
            configuration,
            scriptEntity.createdAt,
            scriptPackageInstance,
            parent,
            parameters,
        )
    }

    @OptIn(ExperimentalTime::class)
    fun createTaskEntity(
        task: Task,
        scriptId: UUID,
    ): TaskEntity {
        val now =
            Clock.System
                .now()
        val taskId = UUID.fromString(task.id)
        return TaskEntity(
            id = taskId,
            scriptId = scriptId,
            createdAt = now,
            updatedAt = now,
        )
    }

    @OptIn(ExperimentalTime::class)
    fun createTaskConfigurationEntities(
        task: Task,
        taskId: UUID,
    ): List<TaskConfigurationEntity> {
        val now =
            Clock.System
                .now()
        return task.configuration.map { (key, value) ->
            TaskConfigurationEntity(
                id = UUID.randomUUID(),
                taskId = taskId,
                key = key,
                value = EncryptedString.from(keyValueMapper.valueToString(value.raw)),
                type = keyValueMapper.getValueType(value.raw),
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    fun createTaskStatusEntities(
        task: Task,
        taskId: UUID,
    ): List<TaskStatusEntity> {
        val now =
            Clock.System
                .now()
        return task.statusHistory.map { status ->
            TaskStatusEntity(
                id = UUID.randomUUID(),
                taskId = taskId,
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
        }
    }

    suspend fun mapTask(
        taskEntity: TaskEntity,
        configurationEntities: List<TaskConfigurationEntity>,
        statusEntities: List<TaskStatusEntity>,
        scriptInstance: ScriptInstance,
        user: User,
    ): JobTask {
        val configuration =
            keyValueMapper.mapTaskConfigurationFromEntities(
                configurationEntities,
                user,
                scriptInstance.definition.configuration,
            )

        val statusHistory = statusEntities.map { mapStatusEntity(it) }

        return jobTaskFactory.create(
            taskEntity.id.toString(),
            scriptInstance,
            configuration,
            statusHistory,
            createdAt = taskEntity.createdAt,
        )
    }

    @OptIn(ExperimentalTime::class)
    internal fun mapStatusEntity(entity: TaskStatusEntity): TaskStatus =
        when (entity.status) {
            TaskExecutionStatus.IDLE -> {
                TaskStatus.Idle(
                    entity.message?.value ?: "",
                    Instant.fromEpochMilliseconds(entity.timestamp),
                )
            }

            TaskExecutionStatus.RUNNING -> {
                TaskStatus.Running(
                    entity.message?.value,
                    Instant.fromEpochMilliseconds(entity.timestamp),
                )
            }

            TaskExecutionStatus.SUCCESS -> {
                TaskStatus.Success(
                    entity.message?.value ?: "",
                    Instant.fromEpochMilliseconds(entity.timestamp),
                )
            }

            TaskExecutionStatus.ERROR -> {
                TaskStatus.Error(
                    message = entity.message?.value ?: "",
                    stackTrace = entity.stackTrace?.value,
                    timestamp = Instant.fromEpochMilliseconds(entity.timestamp),
                )
            }
        }
}
