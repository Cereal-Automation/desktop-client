package com.cereal.client.fixtures

import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.KeyValueDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake of [KeyValueDataSource].
 *
 * Values are stored in a single [MutableStateFlow] backed map keyed by user id + key, so that
 * getters observe writes. A `null` user is scoped under a stable sentinel key.
 */
class InMemoryKeyValueDataSource : KeyValueDataSource {
    private val store = MutableStateFlow<Map<String, Any?>>(emptyMap())

    private fun keyFor(
        key: String,
        user: User?,
    ): String = "${user?.id ?: NO_USER}::$key"

    private fun put(
        compositeKey: String,
        value: Any?,
    ) {
        store.value =
            if (value == null) {
                store.value - compositeKey
            } else {
                store.value + (compositeKey to value)
            }
    }

    private inline fun <reified T> observe(compositeKey: String): Flow<T?> = store.map { it[compositeKey] as? T }

    override suspend fun deleteValueByKey(
        key: String,
        user: User?,
    ) {
        store.value = store.value - keyFor(key, user)
    }

    override fun getStringByKey(
        key: String,
        user: User?,
    ): Flow<String?> = observe(keyFor(key, user))

    override suspend fun setStringByKey(
        key: String,
        value: String?,
        user: User?,
    ) = put(keyFor(key, user), value)

    override fun getIntByKey(
        key: String,
        user: User?,
    ): Flow<Int?> = observe(keyFor(key, user))

    override suspend fun setIntByKey(
        key: String,
        value: Int?,
        user: User?,
    ) = put(keyFor(key, user), value)

    override fun getLongByKey(
        key: String,
        user: User?,
    ): Flow<Long?> = observe(keyFor(key, user))

    override suspend fun setLongByKey(
        key: String,
        value: Long?,
        user: User?,
    ) = put(keyFor(key, user), value)

    override fun getFloatByKey(
        key: String,
        user: User?,
    ): Flow<Float?> = observe(keyFor(key, user))

    override suspend fun setFloatByKey(
        key: String,
        value: Float?,
        user: User?,
    ) = put(keyFor(key, user), value)

    override fun getBooleanByKey(
        key: String,
        user: User?,
    ): Flow<Boolean?> = observe(keyFor(key, user))

    override suspend fun setBooleanByKey(
        key: String,
        value: Boolean?,
        user: User?,
    ) = put(keyFor(key, user), value)

    private companion object {
        private const val NO_USER = "<no-user>"
    }
}
