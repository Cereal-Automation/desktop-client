package com.cereal.client.infrastructure.data.datasource.database

import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.user.User
import kotlinx.coroutines.flow.Flow

/**
 * Persistence operations for script package groups.
 */
interface ScriptPackageGroupDataSource {
    fun getScriptInstanceGroups(user: User): Flow<List<ScriptPackageGroup>>

    suspend fun createScriptInstanceGroup(
        user: User,
        scriptPackageGroup: ScriptPackageGroup,
    ): ScriptPackageGroup

    suspend fun updateScriptPackageGroup(
        user: User,
        scriptPackageGroup: ScriptPackageGroup,
    )

    suspend fun deleteScriptInstanceGroup(
        user: User,
        scriptPackageGroupId: String,
    )
}
