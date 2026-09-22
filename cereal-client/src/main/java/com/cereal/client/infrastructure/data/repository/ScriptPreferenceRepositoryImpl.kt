package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.repository.ScriptPreferenceRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.database.ScriptPreferenceDataSource
import kotlinx.coroutines.flow.Flow

class ScriptPreferenceRepositoryImpl(
    private val scriptPreferenceDataSource: ScriptPreferenceDataSource,
    private val userSession: UserSession,
) : ScriptPreferenceRepository {
    override suspend fun deleteValue(
        instanceId: String,
        key: String,
    ) {
        scriptPreferenceDataSource.deleteValue(userSession.requireUser(), instanceId, key)
    }

    override suspend fun getString(
        instanceId: String,
        key: String,
    ): Flow<String?> =
        scriptPreferenceDataSource.getString(
            userSession.requireUser(),
            instanceId,
            key,
        )

    override suspend fun setString(
        instanceId: String,
        key: String,
        value: String,
    ) {
        scriptPreferenceDataSource.setString(
            userSession.requireUser(),
            instanceId,
            key,
            value,
        )
    }

    override suspend fun getInt(
        instanceId: String,
        key: String,
    ): Flow<Int?> = scriptPreferenceDataSource.getInt(userSession.requireUser(), instanceId, key)

    override suspend fun setInt(
        instanceId: String,
        key: String,
        value: Int,
    ) {
        scriptPreferenceDataSource.setInt(userSession.requireUser(), instanceId, key, value)
    }

    override suspend fun getLong(
        instanceId: String,
        key: String,
    ): Flow<Long?> = scriptPreferenceDataSource.getLong(userSession.requireUser(), instanceId, key)

    override suspend fun setLong(
        instanceId: String,
        key: String,
        value: Long,
    ) {
        scriptPreferenceDataSource.setLong(userSession.requireUser(), instanceId, key, value)
    }

    override suspend fun getFloat(
        instanceId: String,
        key: String,
    ): Flow<Float?> =
        scriptPreferenceDataSource.getFloat(
            userSession.requireUser(),
            instanceId,
            key,
        )

    override suspend fun setFloat(
        instanceId: String,
        key: String,
        value: Float,
    ) {
        scriptPreferenceDataSource.setFloat(
            userSession.requireUser(),
            instanceId,
            key,
            value,
        )
    }

    override suspend fun getBoolean(
        instanceId: String,
        key: String,
    ): Flow<Boolean?> =
        scriptPreferenceDataSource.getBoolean(
            userSession.requireUser(),
            instanceId,
            key,
        )

    override suspend fun setBoolean(
        instanceId: String,
        key: String,
        value: Boolean,
    ) {
        scriptPreferenceDataSource.setBoolean(
            userSession.requireUser(),
            instanceId,
            key,
            value,
        )
    }
}
