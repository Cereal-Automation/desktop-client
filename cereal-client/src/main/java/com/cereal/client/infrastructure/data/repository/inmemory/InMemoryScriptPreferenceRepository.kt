package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.repository.ScriptPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [ScriptPreferenceRepository] backed by per-key [MutableStateFlow]s. */
class InMemoryScriptPreferenceRepository : ScriptPreferenceRepository {
    private val values = mutableMapOf<Pair<String, String>, MutableStateFlow<Any?>>()

    private fun flowFor(
        instanceId: String,
        key: String,
    ) = values.getOrPut(instanceId to key) { MutableStateFlow(null) }

    override suspend fun deleteValue(
        instanceId: String,
        key: String,
    ) {
        flowFor(instanceId, key).value = null
    }

    override suspend fun getString(
        instanceId: String,
        key: String,
    ): Flow<String?> = flowFor(instanceId, key).map { it as? String }

    override suspend fun setString(
        instanceId: String,
        key: String,
        value: String,
    ) {
        flowFor(instanceId, key).value = value
    }

    override suspend fun getInt(
        instanceId: String,
        key: String,
    ): Flow<Int?> = flowFor(instanceId, key).map { it as? Int }

    override suspend fun setInt(
        instanceId: String,
        key: String,
        value: Int,
    ) {
        flowFor(instanceId, key).value = value
    }

    override suspend fun getLong(
        instanceId: String,
        key: String,
    ): Flow<Long?> = flowFor(instanceId, key).map { it as? Long }

    override suspend fun setLong(
        instanceId: String,
        key: String,
        value: Long,
    ) {
        flowFor(instanceId, key).value = value
    }

    override suspend fun getFloat(
        instanceId: String,
        key: String,
    ): Flow<Float?> = flowFor(instanceId, key).map { it as? Float }

    override suspend fun setFloat(
        instanceId: String,
        key: String,
        value: Float,
    ) {
        flowFor(instanceId, key).value = value
    }

    override suspend fun getBoolean(
        instanceId: String,
        key: String,
    ): Flow<Boolean?> = flowFor(instanceId, key).map { it as? Boolean }

    override suspend fun setBoolean(
        instanceId: String,
        key: String,
        value: Boolean,
    ) {
        flowFor(instanceId, key).value = value
    }
}
