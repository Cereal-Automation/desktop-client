package com.cereal.client.infrastructure.data.datasource.database.room

import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.ScriptPreferenceDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptPreferenceEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ValueType
import com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Room implementation of ScriptPreferenceDataSource
 */
class RoomScriptPreferenceDataSource(
    private val roomDatabases: RoomDatabases,
) : ScriptPreferenceDataSource {
    private val logger = LoggerFactory.getLogger(ScriptPreferenceDataSource::class.java)

    override suspend fun deleteValue(
        user: User,
        instanceId: String,
        key: String,
    ) {
        val dao = roomDatabases.getUserDatabase(user).scriptPreferenceDao()
        dao.deleteByInstanceIdAndKey(instanceId, key)
    }

    override fun getString(
        user: User,
        instanceId: String,
        key: String,
    ): Flow<String?> =
        getValueByKey(user, instanceId, key)
            .onEach {
                logger.debug("[$instanceId] Received script preference: [$key] with [$it]")
            }

    override suspend fun setString(
        user: User,
        instanceId: String,
        key: String,
        value: String,
    ) {
        logger.debug("[$instanceId] Updating script preference: [$key] to [$value]")
        setValueByKey(user, instanceId, key, value, ValueType.STRING)
    }

    override fun getInt(
        user: User,
        instanceId: String,
        key: String,
    ): Flow<Int?> =
        getValueByKey(user, instanceId, key)
            .map {
                it?.toIntOrNull()
            }.onEach {
                logger.debug("[$instanceId] Received script preference: [$key] with [$it]")
            }

    override suspend fun setInt(
        user: User,
        instanceId: String,
        key: String,
        value: Int,
    ) {
        val stringValue = value.toString()
        logger.debug("[$instanceId] Updating script preference: [$key] to [$stringValue]")
        setValueByKey(user, instanceId, key, stringValue, ValueType.INT)
    }

    override fun getLong(
        user: User,
        instanceId: String,
        key: String,
    ): Flow<Long?> =
        getValueByKey(user, instanceId, key)
            .map {
                it?.toLongOrNull()
            }.onEach {
                logger.debug("[$instanceId] Received script preference: [$key] with [$it]")
            }

    override suspend fun setLong(
        user: User,
        instanceId: String,
        key: String,
        value: Long,
    ) {
        val stringValue = value.toString()
        logger.debug("[$instanceId] Updating script preference: [$key] to [$stringValue]")
        setValueByKey(user, instanceId, key, stringValue, ValueType.LONG)
    }

    override fun getFloat(
        user: User,
        instanceId: String,
        key: String,
    ): Flow<Float?> =
        getValueByKey(user, instanceId, key)
            .map {
                it?.toFloatOrNull()
            }.onEach {
                logger.debug("[$instanceId] Received script preference: [$key] with [$it]")
            }

    override suspend fun setFloat(
        user: User,
        instanceId: String,
        key: String,
        value: Float,
    ) {
        val stringValue = value.toString()
        logger.debug("[$instanceId] Updating script preference: [$key] to [$stringValue]")
        setValueByKey(user, instanceId, key, stringValue, ValueType.FLOAT)
    }

    override fun getBoolean(
        user: User,
        instanceId: String,
        key: String,
    ): Flow<Boolean?> =
        getValueByKey(user, instanceId, key)
            .map {
                it?.toBoolean()
            }.onEach {
                logger.debug("[$instanceId] Received script preference: [$key] with [$it]")
            }

    override suspend fun setBoolean(
        user: User,
        instanceId: String,
        key: String,
        value: Boolean,
    ) {
        val stringValue = value.toString()
        logger.debug("[$instanceId] Updating script preference: [$key] to [$stringValue]")
        setValueByKey(user, instanceId, key, stringValue, ValueType.BOOLEAN)
    }

    private fun getValueByKey(
        user: User,
        instanceId: String,
        key: String,
    ): Flow<String?> {
        val dao = roomDatabases.getUserDatabase(user).scriptPreferenceDao()
        return dao
            .getByInstanceIdAndKeyFlow(instanceId, key)
            .map { it?.value?.value }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun setValueByKey(
        user: User,
        instanceId: String,
        key: String,
        value: String?,
        type: ValueType,
    ) {
        val dao = roomDatabases.getUserDatabase(user).scriptPreferenceDao()
        val now =
            Clock.System
                .now()

        val existingEntity = dao.getByInstanceIdAndKey(instanceId, key)

        if (existingEntity == null) {
            // Create new entity
            val newEntity =
                ScriptPreferenceEntity(
                    id = UUID.randomUUID(),
                    instanceId = instanceId,
                    key = key,
                    value = EncryptedString.from(value),
                    type = type,
                    createdAt = now,
                    updatedAt = now,
                )
            dao.insert(newEntity)
        } else {
            // Update existing entity
            val updatedEntity =
                existingEntity.copy(
                    value = EncryptedString.from(value),
                    type = type,
                    updatedAt = now,
                )
            dao.update(updatedEntity)
        }
    }
}
