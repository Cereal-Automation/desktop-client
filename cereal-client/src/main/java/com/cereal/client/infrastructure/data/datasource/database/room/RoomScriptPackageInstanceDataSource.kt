package com.cereal.client.infrastructure.data.datasource.database.room

import com.cereal.client.domain.model.script.ChildScriptInstance
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.ScriptPackageInstanceDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.dao.ScriptInstanceDao
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptPackageEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptParameterEntity
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.ScriptInstanceMapper
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.ScriptNotificationOverrideMapper
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.ScriptPackageInstanceMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Room implementation of [ScriptPackageInstanceDataSource].
 */
class RoomScriptPackageInstanceDataSource(
    private val roomDatabases: RoomDatabases,
    private val scriptInstanceMapper: ScriptInstanceMapper,
    private val scriptPackageInstanceMapper: ScriptPackageInstanceMapper,
    private val notificationOverrideMapper: ScriptNotificationOverrideMapper,
) : ScriptPackageInstanceDataSource {
    override suspend fun updateScriptPackageInstanceGroup(
        user: User,
        scriptPackageInstance: ScriptPackageInstance,
        newGroupId: String,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            val dao = userDatabase.scriptInstanceDao()
            dao.updateScriptPackageGroupId(
                packageId = UUID.fromString(scriptPackageInstance.id),
                newGroupId = UUID.fromString(newGroupId),
            )
        }
    }

    override suspend fun addScriptPackageInstance(
        user: User,
        scriptPackageInstance: ScriptPackageInstance,
        mainScriptInstance: MainScriptInstance,
        groupId: String,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            val dao = userDatabase.scriptInstanceDao()

            // Create script package entity first (required by foreign key constraints)
            val scriptPackageEntity =
                scriptInstanceMapper.createScriptPackageEntity(
                    scriptPackageInstance,
                    groupId,
                )
            dao.insertScriptPackage(scriptPackageEntity)

            // Create main script entity (depends on script package)
            val mainScriptEntity =
                scriptInstanceMapper.createScriptEntity(
                    mainScriptInstance,
                    UUID.fromString(scriptPackageInstance.id),
                )
            dao.insertScript(mainScriptEntity)

            // Create main script configuration (depends on script package)
            val mainScriptConfiguration =
                scriptInstanceMapper.createMainScriptConfigurationEntity(
                    scriptPackageEntity.id,
                )
            dao.insertScriptConfiguration(mainScriptConfiguration)

            // Create main script configuration items (depends on script configuration)
            val mainScriptConfigurationItems =
                scriptInstanceMapper.createScriptConfigurationItemEntities(
                    mainScriptInstance.configuration,
                    mainScriptConfiguration.id,
                )
            dao.insertScriptConfigurationItems(mainScriptConfigurationItems)

            // Create child script configurations (depends on script package)
            val childScriptConfigurations =
                scriptInstanceMapper.createChildScriptConfigurationEntities(
                    scriptPackageInstance,
                    scriptPackageEntity.id,
                )
            dao.insertScriptConfigurations(childScriptConfigurations)

            // Create child script configuration items (depends on script configurations)
            childScriptConfigurations.forEach { childConfig ->
                val childConfigurationValues = scriptPackageInstance.childConfigurations[childConfig.scriptId]
                if (childConfigurationValues != null) {
                    val childConfigurationItems =
                        scriptInstanceMapper.createScriptConfigurationItemEntities(
                            childConfigurationValues,
                            childConfig.id,
                        )
                    dao.insertScriptConfigurationItems(childConfigurationItems)
                }
            }

            // Create notification overrides if present
            scriptPackageInstance.notificationOverrides?.let { overrides ->
                if (overrides.hasAnyOverrides()) {
                    val overrideEntity =
                        notificationOverrideMapper.toEntity(
                            scriptPackageInstance.id,
                            overrides,
                        )
                    dao.insertScriptNotificationOverride(overrideEntity)
                }
            }
        }
    }

    override suspend fun addChildScript(
        user: User,
        childScriptIdentifier: String,
        scriptInstance: ChildScriptInstance,
        parentInstanceId: String,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            val dao = userDatabase.scriptInstanceDao()

            // Find parent script
            val parentScript =
                dao.getScriptById(UUID.fromString(parentInstanceId))
                    ?: throw IllegalArgumentException("Parent script not found: $parentInstanceId")

            // Create child script entity
            val childScriptEntity =
                scriptInstanceMapper.createScriptEntity(
                    scriptInstance,
                    parentScript.packageId,
                    parentScript.id,
                    childScriptIdentifier,
                )
            dao.insertScript(childScriptEntity)

            // Create script parameters
            val parameterEntities =
                scriptInstanceMapper.createScriptParameterEntities(
                    scriptInstance,
                    childScriptEntity.id,
                )
            dao.insertScriptParameters(parameterEntities)
        }
    }

    override suspend fun getScriptPackageInstancesInGroup(
        user: User,
        groupId: String,
        scriptPackageDefinitions: Map<String, ScriptPackage>,
    ): List<ScriptPackageInstance> {
        val dao = roomDatabases.getUserDatabase(user).scriptInstanceDao()
        val scriptPackageEntities = dao.getScriptPackagesByGroupId(UUID.fromString(groupId))

        return scriptPackageEntities.mapNotNull { packageEntity ->
            val scriptPackageDefinition =
                scriptPackageDefinitions[packageEntity.packageName]
                    ?: return@mapNotNull null

            mapScriptPackageEntityToInstance(dao, packageEntity, scriptPackageDefinition, user)
        }
    }

    override fun getScriptPackagesInGroupFlow(
        user: User,
        groupId: String,
        scriptPackageDefinitions: Map<String, ScriptPackage>,
    ): Flow<List<ScriptPackageInstance>> {
        val dao = roomDatabases.getUserDatabase(user).scriptInstanceDao()
        return dao
            .getScriptPackagesByGroupIdFlow(UUID.fromString(groupId))
            .map { entities ->
                entities.mapNotNull { packageEntity ->
                    val scriptPackageDefinition =
                        scriptPackageDefinitions[packageEntity.packageName]
                            ?: return@mapNotNull null

                    mapScriptPackageEntityToInstance(dao, packageEntity, scriptPackageDefinition, user)
                }
            }
    }

    override suspend fun getScriptPackageInstances(
        user: User,
        scriptPackageDefinitions: Map<String, ScriptPackage>,
    ): List<ScriptPackageInstance> {
        val dao = roomDatabases.getUserDatabase(user).scriptInstanceDao()
        val scriptPackageEntities = dao.getAllScriptPackages()

        return scriptPackageEntities.mapNotNull { packageEntity ->
            val scriptPackageDefinition =
                scriptPackageDefinitions[packageEntity.packageName]
                    ?: return@mapNotNull null

            mapScriptPackageEntityToInstance(dao, packageEntity, scriptPackageDefinition, user)
        }
    }

    override suspend fun getScriptPackageInstance(
        user: User,
        id: String,
        scriptPackageDefinition: ScriptPackage,
    ): ScriptPackageInstance? {
        val dao = roomDatabases.getUserDatabase(user).scriptInstanceDao()
        val packageEntity =
            dao.getScriptPackageById(UUID.fromString(id))
                ?: return null

        return mapScriptPackageEntityToInstance(dao, packageEntity, scriptPackageDefinition, user)
    }

    override suspend fun getScriptPackageNameById(
        user: User,
        id: String,
    ): String? =
        roomDatabases
            .getUserDatabase(user)
            .scriptInstanceDao()
            .getScriptPackageById(UUID.fromString(id))
            ?.packageName

    override suspend fun deleteScriptPackageInstance(
        user: User,
        scriptPackageInstanceId: String,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        val packageId = UUID.fromString(scriptPackageInstanceId)
        userDatabase.immediateWriteTransaction {
            userDatabase
                .scriptInstanceDao()
                .deleteScriptPackageById(packageId)
        }
    }

    override suspend fun getScriptInstances(
        user: User,
        scriptPackageInstance: ScriptPackageInstance,
    ): List<ScriptInstance> {
        val dao = roomDatabases.getUserDatabase(user).scriptInstanceDao()
        val packageId = UUID.fromString(scriptPackageInstance.id)

        // Get main script
        val mainScriptEntity =
            dao.getMainScriptByPackageId(packageId)
                ?: return emptyList()

        // Get main script configuration items
        val mainScriptConfiguration = dao.getMainScriptConfigurationByPackageId(packageId)
        val mainScriptConfigurationItems =
            mainScriptConfiguration?.let {
                dao.getScriptConfigurationItemsByConfigurationId(it.id)
            } ?: emptyList()

        val mainScript =
            scriptInstanceMapper.mapMainScript(
                mainScriptEntity,
                mainScriptConfigurationItems,
                scriptPackageInstance,
                user,
            )

        // Fetch the whole script tree for this package in two queries (all scripts + all
        // parameters), then assemble it in memory instead of issuing per-node queries.
        val allScripts = dao.getScriptsByPackageId(packageId)
        val childrenByParent = allScripts.groupBy { it.parentScriptId }
        val childScriptIds = allScripts.mapNotNull { it.id.takeIf { _ -> it.parentScriptId != null } }
        val parametersByScriptId =
            if (childScriptIds.isNotEmpty()) {
                dao.getScriptParametersByScriptIds(childScriptIds).groupBy { it.scriptId }
            } else {
                emptyMap()
            }

        val allChildScripts =
            buildChildScriptTree(
                mainScriptEntity.id,
                childrenByParent,
                parametersByScriptId,
                scriptPackageInstance,
                mainScript,
                user,
            )

        return allChildScripts + mainScript
    }

    /**
     * Assembles the child script subtree for [parentScriptId] from pre-fetched maps, preserving the
     * depth-first ordering the previous per-node recursion produced. A child that fails to map is
     * skipped along with its subtree, matching the prior behaviour.
     */
    private suspend fun buildChildScriptTree(
        parentScriptId: UUID,
        childrenByParent: Map<UUID?, List<ScriptEntity>>,
        parametersByScriptId: Map<UUID, List<ScriptParameterEntity>>,
        scriptPackageInstance: ScriptPackageInstance,
        mainScript: MainScriptInstance,
        user: User,
    ): List<ChildScriptInstance> {
        val directChildren = childrenByParent[parentScriptId] ?: return emptyList()
        val allChildren = mutableListOf<ChildScriptInstance>()

        for (childEntity in directChildren) {
            val parameters = parametersByScriptId[childEntity.id] ?: emptyList()
            val childScript =
                scriptInstanceMapper.mapChildScript(
                    childEntity,
                    parameters,
                    scriptPackageInstance,
                    mainScript,
                    user,
                )

            if (childScript != null) {
                allChildren.add(childScript)
                allChildren.addAll(
                    buildChildScriptTree(
                        childEntity.id,
                        childrenByParent,
                        parametersByScriptId,
                        scriptPackageInstance,
                        mainScript,
                        user,
                    ),
                )
            }
        }

        return allChildren
    }

    private suspend fun mapScriptPackageEntityToInstance(
        dao: ScriptInstanceDao,
        packageEntity: ScriptPackageEntity,
        scriptPackageDefinition: ScriptPackage,
        user: User,
    ): ScriptPackageInstance? {
        // Get main script
        val mainScriptEntity = dao.getMainScriptByPackageId(packageEntity.id)

        // Get main script configuration
        val mainScriptConfiguration = dao.getMainScriptConfigurationByPackageId(packageEntity.id)
        val mainScriptConfigurationItems =
            mainScriptConfiguration?.let {
                dao.getScriptConfigurationItemsByConfigurationId(it.id)
            } ?: emptyList()

        // Get child script configurations
        val childScriptConfigurations = dao.getChildScriptConfigurationsByPackageId(packageEntity.id)

        // Use batch query to get all configuration items at once
        val allConfigurationItems =
            if (childScriptConfigurations.isNotEmpty()) {
                dao
                    .getScriptConfigurationItemsByConfigurationIds(childScriptConfigurations.map { it.id })
                    .groupBy { it.configurationId }
            } else {
                emptyMap()
            }

        val childScriptConfigurationItems =
            childScriptConfigurations.associate { config ->
                config.scriptId!! to (allConfigurationItems[config.id] ?: emptyList())
            }

        // Get notification overrides
        val notificationOverrideEntity = dao.getScriptNotificationOverrideByPackageId(packageEntity.id)
        val notificationOverrides = notificationOverrideMapper.toDomain(notificationOverrideEntity)

        return scriptPackageInstanceMapper.mapToScriptPackageInstance(
            packageEntity,
            mainScriptEntity,
            mainScriptConfigurationItems,
            childScriptConfigurations,
            childScriptConfigurationItems,
            scriptPackageDefinition,
            user,
            notificationOverrides,
        )
    }
}
