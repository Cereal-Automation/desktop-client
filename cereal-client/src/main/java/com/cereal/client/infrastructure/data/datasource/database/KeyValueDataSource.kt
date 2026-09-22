package com.cereal.client.infrastructure.data.datasource.database

import com.cereal.client.domain.model.user.User
import kotlinx.coroutines.flow.Flow

interface KeyValueDataSource {
    suspend fun deleteValueByKey(
        key: String,
        user: User? = null,
    )

    fun getStringByKey(
        key: String,
        user: User? = null,
    ): Flow<String?>

    suspend fun setStringByKey(
        key: String,
        value: String?,
        user: User? = null,
    )

    fun getIntByKey(
        key: String,
        user: User? = null,
    ): Flow<Int?>

    suspend fun setIntByKey(
        key: String,
        value: Int?,
        user: User? = null,
    )

    fun getLongByKey(
        key: String,
        user: User? = null,
    ): Flow<Long?>

    suspend fun setLongByKey(
        key: String,
        value: Long?,
        user: User? = null,
    )

    fun getFloatByKey(
        key: String,
        user: User? = null,
    ): Flow<Float?>

    suspend fun setFloatByKey(
        key: String,
        value: Float?,
        user: User? = null,
    )

    fun getBooleanByKey(
        key: String,
        user: User? = null,
    ): Flow<Boolean?>

    suspend fun setBooleanByKey(
        key: String,
        value: Boolean?,
        user: User? = null,
    )
}
