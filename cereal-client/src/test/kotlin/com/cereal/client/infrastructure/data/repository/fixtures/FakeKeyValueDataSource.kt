package com.cereal.client.infrastructure.data.repository.fixtures

import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.KeyValueDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [KeyValueDataSource] backed by a [MutableMap] of [MutableStateFlow]s, keyed by the
 * preference key. Values are stored boxed as [Any] and read back through typed accessors.
 */
class FakeKeyValueDataSource : KeyValueDataSource {
    private val store = mutableMapOf<String, MutableStateFlow<Any?>>()

    private fun flowFor(key: String): MutableStateFlow<Any?> = store.getOrPut(key) { MutableStateFlow(null) }

    override suspend fun deleteValueByKey(
        key: String,
        user: User?,
    ) {
        flowFor(key).value = null
    }

    override fun getStringByKey(
        key: String,
        user: User?,
    ): Flow<String?> = flowFor(key).map { it as String? }

    override suspend fun setStringByKey(
        key: String,
        value: String?,
        user: User?,
    ) {
        flowFor(key).value = value
    }

    override fun getIntByKey(
        key: String,
        user: User?,
    ): Flow<Int?> = flowFor(key).map { it as Int? }

    override suspend fun setIntByKey(
        key: String,
        value: Int?,
        user: User?,
    ) {
        flowFor(key).value = value
    }

    override fun getLongByKey(
        key: String,
        user: User?,
    ): Flow<Long?> = flowFor(key).map { it as Long? }

    override suspend fun setLongByKey(
        key: String,
        value: Long?,
        user: User?,
    ) {
        flowFor(key).value = value
    }

    override fun getFloatByKey(
        key: String,
        user: User?,
    ): Flow<Float?> = flowFor(key).map { it as Float? }

    override suspend fun setFloatByKey(
        key: String,
        value: Float?,
        user: User?,
    ) {
        flowFor(key).value = value
    }

    override fun getBooleanByKey(
        key: String,
        user: User?,
    ): Flow<Boolean?> = flowFor(key).map { it as Boolean? }

    override suspend fun setBooleanByKey(
        key: String,
        value: Boolean?,
        user: User?,
    ) {
        flowFor(key).value = value
    }
}
