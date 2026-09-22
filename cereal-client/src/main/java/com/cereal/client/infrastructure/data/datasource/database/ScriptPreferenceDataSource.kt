package com.cereal.client.infrastructure.data.datasource.database

import com.cereal.client.domain.model.user.User
import kotlinx.coroutines.flow.Flow

interface ScriptPreferenceDataSource {
    suspend fun deleteValue(
        user: User,
        instanceId: String,
        key: String,
    )

    fun getString(
        user: User,
        instanceId: String,
        key: String,
    ): Flow<String?>

    suspend fun setString(
        user: User,
        instanceId: String,
        key: String,
        value: String,
    )

    fun getInt(
        user: User,
        instanceId: String,
        key: String,
    ): Flow<Int?>

    suspend fun setInt(
        user: User,
        instanceId: String,
        key: String,
        value: Int,
    )

    fun getLong(
        user: User,
        instanceId: String,
        key: String,
    ): Flow<Long?>

    suspend fun setLong(
        user: User,
        instanceId: String,
        key: String,
        value: Long,
    )

    fun getFloat(
        user: User,
        instanceId: String,
        key: String,
    ): Flow<Float?>

    suspend fun setFloat(
        user: User,
        instanceId: String,
        key: String,
        value: Float,
    )

    fun getBoolean(
        user: User,
        instanceId: String,
        key: String,
    ): Flow<Boolean?>

    suspend fun setBoolean(
        user: User,
        instanceId: String,
        key: String,
        value: Boolean,
    )
}
