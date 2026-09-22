package com.cereal.client.infrastructure.data.datasource.database.room

import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.KeyValueDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.entity.SettingsEntity
import com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString
import com.cereal.client.infrastructure.data.datasource.preference.ApplicationPreferenceKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Room implementation of KeyValueDataSource
 */
class RoomKeyValueDataSource(
    private val roomDatabases: RoomDatabases,
) : KeyValueDataSource {
    private val logger = LoggerFactory.getLogger(KeyValueDataSource::class.java)

    // Memory cache for storing key-value pairs
    private val memoryCache = ConcurrentHashMap<String, String>()

    // Mutex for cache operations to ensure thread safety during cache updates
    private val cacheMutex = Mutex()

    private fun loggableValue(
        key: String,
        value: String?,
    ): String =
        if (key in ApplicationPreferenceKey.sensitiveKeys) {
            "<redacted, ${value?.length ?: 0} chars>"
        } else {
            value.toString()
        }

    override suspend fun deleteValueByKey(
        key: String,
        user: User?,
    ) {
        val dao =
            if (user != null) {
                roomDatabases.getUserDatabase(user).applicationSettingsDao()
            } else {
                roomDatabases.getApplicationDatabase().applicationSettingsDao()
            }

        dao.deleteByKey(key)

        // Remove from cache after successful database deletion
        cacheMutex.withLock {
            memoryCache.remove(key)
            logger.debug("Removed key [$key] from memory cache")
        }
    }

    override fun getStringByKey(
        key: String,
        user: User?,
    ): Flow<String?> {
        // Check cache first for immediate response
        val cachedValue = memoryCache[key]
        if (cachedValue != null) {
            logger.debug("Cache hit for key [$key] with value [${loggableValue(key, cachedValue)}]")
        } else {
            logger.debug("Cache miss for key [$key], will load from database")
        }

        return getValueByKey(key, user)
            .onEach {
                logger.debug("Received application setting: [$key] with [${loggableValue(key, it)}]")
            }
    }

    override suspend fun setStringByKey(
        key: String,
        value: String?,
        user: User?,
    ) {
        if (value == null) {
            // Setting null value is equivalent to deleting the key
            deleteValueByKey(key, user)
            return
        }

        logger.debug("Updating application setting: [$key] to [${loggableValue(key, value)}]")
        setValueByKey(key, value, user)

        // Update cache after successful database write
        cacheMutex.withLock {
            memoryCache[key] = value
            logger.debug("Updated memory cache for key [$key] with value [${loggableValue(key, value)}]")
        }
    }

    override fun getIntByKey(
        key: String,
        user: User?,
    ): Flow<Int?> =
        getValueByKey(key, user)
            .map {
                it?.toIntOrNull()
            }.onEach {
                logger.debug("Received application setting: [$key] with [${loggableValue(key, it?.toString())}]")
            }

    override suspend fun setIntByKey(
        key: String,
        value: Int?,
        user: User?,
    ) {
        if (value == null) {
            // Setting null value is equivalent to deleting the key
            deleteValueByKey(key, user)
            return
        }

        val stringValue = value.toString()
        logger.debug("Updating application setting: [$key] to [${loggableValue(key, stringValue)}]")

        setValueByKey(key, stringValue, user)

        // Update cache after successful database write
        cacheMutex.withLock {
            memoryCache[key] = stringValue
            logger.debug("Updated memory cache for key [$key] with value [${loggableValue(key, stringValue)}]")
        }
    }

    override fun getLongByKey(
        key: String,
        user: User?,
    ): Flow<Long?> =
        getValueByKey(key, user)
            .map {
                it?.toLongOrNull()
            }.onEach {
                logger.debug("Received application setting: [$key] with [${loggableValue(key, it?.toString())}]")
            }

    override suspend fun setLongByKey(
        key: String,
        value: Long?,
        user: User?,
    ) {
        if (value == null) {
            // Setting null value is equivalent to deleting the key
            deleteValueByKey(key, user)
            return
        }

        val stringValue = value.toString()
        logger.debug("Updating application setting: [$key] to [${loggableValue(key, stringValue)}]")
        setValueByKey(key, stringValue, user)

        // Update cache after successful database write
        cacheMutex.withLock {
            memoryCache[key] = stringValue
            logger.debug("Updated memory cache for key [$key] with value [${loggableValue(key, stringValue)}]")
        }
    }

    override fun getFloatByKey(
        key: String,
        user: User?,
    ): Flow<Float?> =
        getValueByKey(key, user)
            .map {
                it?.toFloatOrNull()
            }.onEach {
                logger.debug("Received application setting: [$key] with [${loggableValue(key, it?.toString())}]")
            }

    override suspend fun setFloatByKey(
        key: String,
        value: Float?,
        user: User?,
    ) {
        if (value == null) {
            // Setting null value is equivalent to deleting the key
            deleteValueByKey(key, user)
            return
        }

        val stringValue = value.toString()
        logger.debug("Updating application setting: [$key] to [${loggableValue(key, stringValue)}]")
        setValueByKey(key, stringValue, user)

        // Update cache after successful database write
        cacheMutex.withLock {
            memoryCache[key] = stringValue
            logger.debug("Updated memory cache for key [$key] with value [${loggableValue(key, stringValue)}]")
        }
    }

    override fun getBooleanByKey(
        key: String,
        user: User?,
    ): Flow<Boolean?> =
        getValueByKey(key, user)
            .map {
                it?.toBoolean()
            }.onEach {
                logger.debug("Received application setting: [$key] with [${loggableValue(key, it?.toString())}]")
            }

    override suspend fun setBooleanByKey(
        key: String,
        value: Boolean?,
        user: User?,
    ) {
        if (value == null) {
            // Setting null value is equivalent to deleting the key
            deleteValueByKey(key, user)
            return
        }

        val stringValue = value.toString()
        logger.debug("Updating application setting: [$key] to [${loggableValue(key, stringValue)}]")
        setValueByKey(key, stringValue, user)

        // Update cache after successful database write
        cacheMutex.withLock {
            memoryCache[key] = stringValue
            logger.debug("Updated memory cache for key [$key] with value [${loggableValue(key, stringValue)}]")
        }
    }

    private fun getValueByKey(
        key: String,
        user: User? = null,
    ): Flow<String?> {
        val dao =
            if (user != null) {
                roomDatabases.getUserDatabase(user).applicationSettingsDao()
            } else {
                roomDatabases.getApplicationDatabase().applicationSettingsDao()
            }

        return dao
            .getByKeyFlow(key)
            .map { it?.value?.value }
            .onEach { databaseValue ->
                // Update cache with database value
                try {
                    cacheMutex.withLock {
                        if (databaseValue != null) {
                            memoryCache[key] = databaseValue
                        } else {
                            memoryCache.remove(key) // Remove from cache if value is null
                        }
                        logger.debug("Updated cache for key [$key] with database value [${loggableValue(key, databaseValue)}]")
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    logger.error("Error updating cache for key [$key]: ${e.message}", e)
                    CrashReporter.report(e)
                }
            }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun setValueByKey(
        key: String,
        value: String?,
        user: User? = null,
    ) {
        val dao =
            if (user != null) {
                roomDatabases.getUserDatabase(user).applicationSettingsDao()
            } else {
                roomDatabases.getApplicationDatabase().applicationSettingsDao()
            }

        val existingEntity = dao.getByKey(key)
        val now =
            Clock.System
                .now()

        if (existingEntity == null) {
            dao.insert(
                SettingsEntity(
                    id = UUID.randomUUID(),
                    key = key,
                    value = EncryptedString.of(value ?: ""),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            dao.update(
                existingEntity.copy(
                    value = EncryptedString.of(value ?: ""),
                    updatedAt = now,
                ),
            )
        }
    }

    /**
     * Clears all cached values from memory.
     * Useful for testing or when you want to force fresh database reads.
     */
    suspend fun clearCache() {
        cacheMutex.withLock {
            val cacheSize = memoryCache.size
            memoryCache.clear()
            logger.debug("Cleared memory cache, removed [$cacheSize] entries")
        }
    }

    /**
     * Removes a specific key from the cache.
     * The next read will fetch from the database.
     */
    suspend fun invalidateCacheKey(key: String) {
        cacheMutex.withLock {
            val removed = memoryCache.remove(key)
            logger.debug("Invalidated cache for key [$key], was cached: [${removed != null}]")
        }
    }

    /**
     * Returns the current size of the memory cache.
     * Useful for monitoring and debugging.
     */
    fun getCacheSize(): Int = memoryCache.size
}
