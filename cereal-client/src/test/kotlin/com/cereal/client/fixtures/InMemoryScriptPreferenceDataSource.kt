package com.cereal.client.fixtures

import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.ScriptPreferenceDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake of [ScriptPreferenceDataSource].
 *
 * Values are stored in a single [MutableStateFlow] backed map keyed by user id + instance id + key,
 * so that getters observe writes.
 */
class InMemoryScriptPreferenceDataSource : ScriptPreferenceDataSource {
    private val store = MutableStateFlow<Map<String, Any>>(emptyMap())

    private fun keyFor(
        user: User,
        instanceId: String,
        key: String,
    ): String = "${user.id}::$instanceId::$key"

    private fun put(
        compositeKey: String,
        value: Any,
    ) {
        store.value = store.value + (compositeKey to value)
    }

    private inline fun <reified T> observe(compositeKey: String): Flow<T?> = store.map { it[compositeKey] as? T }

    override suspend fun deleteValue(
        user: User,
        instanceId: String,
        key: String,
    ) {
        store.value = store.value - keyFor(user, instanceId, key)
    }

    override fun getString(
        user: User,
        instanceId: String,
        key: String,
    ): Flow<String?> = observe(keyFor(user, instanceId, key))

    override suspend fun setString(
        user: User,
        instanceId: String,
        key: String,
        value: String,
    ) = put(keyFor(user, instanceId, key), value)

    override fun getInt(
        user: User,
        instanceId: String,
        key: String,
    ): Flow<Int?> = observe(keyFor(user, instanceId, key))

    override suspend fun setInt(
        user: User,
        instanceId: String,
        key: String,
        value: Int,
    ) = put(keyFor(user, instanceId, key), value)

    override fun getLong(
        user: User,
        instanceId: String,
        key: String,
    ): Flow<Long?> = observe(keyFor(user, instanceId, key))

    override suspend fun setLong(
        user: User,
        instanceId: String,
        key: String,
        value: Long,
    ) = put(keyFor(user, instanceId, key), value)

    override fun getFloat(
        user: User,
        instanceId: String,
        key: String,
    ): Flow<Float?> = observe(keyFor(user, instanceId, key))

    override suspend fun setFloat(
        user: User,
        instanceId: String,
        key: String,
        value: Float,
    ) = put(keyFor(user, instanceId, key), value)

    override fun getBoolean(
        user: User,
        instanceId: String,
        key: String,
    ): Flow<Boolean?> = observe(keyFor(user, instanceId, key))

    override suspend fun setBoolean(
        user: User,
        instanceId: String,
        key: String,
        value: Boolean,
    ) = put(keyFor(user, instanceId, key), value)
}
