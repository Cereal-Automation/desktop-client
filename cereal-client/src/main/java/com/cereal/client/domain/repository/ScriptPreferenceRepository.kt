package com.cereal.client.domain.repository

import kotlinx.coroutines.flow.Flow

interface ScriptPreferenceRepository {
    suspend fun deleteValue(
        instanceId: String,
        key: String,
    )

    suspend fun getString(
        instanceId: String,
        key: String,
    ): Flow<String?>

    suspend fun setString(
        instanceId: String,
        key: String,
        value: String,
    )

    suspend fun getInt(
        instanceId: String,
        key: String,
    ): Flow<Int?>

    suspend fun setInt(
        instanceId: String,
        key: String,
        value: Int,
    )

    suspend fun getLong(
        instanceId: String,
        key: String,
    ): Flow<Long?>

    suspend fun setLong(
        instanceId: String,
        key: String,
        value: Long,
    )

    suspend fun getFloat(
        instanceId: String,
        key: String,
    ): Flow<Float?>

    suspend fun setFloat(
        instanceId: String,
        key: String,
        value: Float,
    )

    suspend fun getBoolean(
        instanceId: String,
        key: String,
    ): Flow<Boolean?>

    suspend fun setBoolean(
        instanceId: String,
        key: String,
        value: Boolean,
    )
}
