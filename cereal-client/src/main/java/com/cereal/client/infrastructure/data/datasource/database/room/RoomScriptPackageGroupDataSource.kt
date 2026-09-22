package com.cereal.client.infrastructure.data.datasource.database.room

import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.ScriptPackageGroupDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.ScriptInstanceMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Room implementation of [ScriptPackageGroupDataSource].
 */
class RoomScriptPackageGroupDataSource(
    private val roomDatabases: RoomDatabases,
    private val scriptInstanceMapper: ScriptInstanceMapper,
) : ScriptPackageGroupDataSource {
    override fun getScriptInstanceGroups(user: User): Flow<List<ScriptPackageGroup>> =
        roomDatabases
            .getUserDatabase(user)
            .scriptInstanceDao()
            .getAllScriptPackageGroupsWithCountsFlow()
            .map { entities ->
                entities.map { entity ->
                    scriptInstanceMapper.toDomain(entity)
                }
            }

    override suspend fun createScriptInstanceGroup(
        user: User,
        scriptPackageGroup: ScriptPackageGroup,
    ): ScriptPackageGroup {
        val userDatabase = roomDatabases.getUserDatabase(user)
        return userDatabase.immediateWriteTransaction {
            val dao = userDatabase.scriptInstanceDao()
            val entity = scriptInstanceMapper.createScriptPackageGroupEntity(scriptPackageGroup)
            dao.insertScriptPackageGroup(entity)
            scriptInstanceMapper.toDomain(entity, dao)
        }
    }

    override suspend fun updateScriptPackageGroup(
        user: User,
        scriptPackageGroup: ScriptPackageGroup,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            val dao = userDatabase.scriptInstanceDao()
            val existing =
                dao.getScriptPackageGroupById(UUID.fromString(scriptPackageGroup.id))
                    ?: throw IllegalArgumentException("Script package group not found: ${scriptPackageGroup.id}")

            val updated = scriptInstanceMapper.updateScriptPackageGroupEntity(existing, scriptPackageGroup)
            dao.updateScriptPackageGroup(updated)
        }
    }

    override suspend fun deleteScriptInstanceGroup(
        user: User,
        scriptPackageGroupId: String,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            userDatabase
                .scriptInstanceDao()
                .deleteScriptPackageGroupById(UUID.fromString(scriptPackageGroupId))
        }
    }
}
