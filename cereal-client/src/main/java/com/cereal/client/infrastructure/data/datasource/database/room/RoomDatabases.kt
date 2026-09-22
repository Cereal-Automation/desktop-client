package com.cereal.client.infrastructure.data.datasource.database.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.room.converter.ApplicationEncryptedStringConverter
import com.cereal.client.infrastructure.data.datasource.database.room.converter.DateTimeConverter
import com.cereal.client.infrastructure.data.datasource.database.room.converter.JsonConverter
import com.cereal.client.infrastructure.data.datasource.database.room.converter.UserEncryptedStringConverter
import com.cereal.client.infrastructure.data.datasource.database.room.converter.UuidConverter
import com.cereal.client.infrastructure.data.datasource.database.room.converter.ValueTypeConverter
import com.cereal.client.infrastructure.data.datasource.database.room.dao.ApplicationSettingsDao
import com.cereal.client.infrastructure.data.datasource.database.room.dao.ArtifactDao
import com.cereal.client.infrastructure.data.datasource.database.room.dao.DatasetDao
import com.cereal.client.infrastructure.data.datasource.database.room.dao.LogEventDao
import com.cereal.client.infrastructure.data.datasource.database.room.dao.NotificationHistoryDao
import com.cereal.client.infrastructure.data.datasource.database.room.dao.ProxyDao
import com.cereal.client.infrastructure.data.datasource.database.room.dao.ProxyProviderConnectorDao
import com.cereal.client.infrastructure.data.datasource.database.room.dao.ScriptInstanceDao
import com.cereal.client.infrastructure.data.datasource.database.room.dao.ScriptPreferenceDao
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ArtifactEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.DatasetEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.DatasetGroupEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.DatasetGroupItemDefinitionEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.DatasetItemEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.LogEventEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.NotificationHistoryAttemptEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.NotificationHistoryEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ProxyEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ProxyGroupEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ProxyProviderConnectorEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptConfigurationEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptConfigurationItemEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptNotificationOverrideEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptPackageEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptPackageGroupEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptParameterEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptPreferenceEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.SettingsEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.TaskConfigurationEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.TaskEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.TaskStatusEntity
import com.cereal.client.infrastructure.data.datasource.filesystem.security.Encryption
import org.koin.core.component.KoinComponent

/**
 * Room database configuration for application-level data
 */
@Database(
    entities = [
        SettingsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(
    DateTimeConverter::class,
    UuidConverter::class,
    JsonConverter::class,
    ApplicationEncryptedStringConverter::class,
)
abstract class ApplicationRoomDatabase : RoomDatabase() {
    abstract fun applicationSettingsDao(): ApplicationSettingsDao
}

/**
 * Room database configuration for user-specific data
 */
@Database(
    entities = [
        SettingsEntity::class,
        ProxyGroupEntity::class,
        ProxyEntity::class,
        ProxyProviderConnectorEntity::class,
        ScriptPackageGroupEntity::class,
        ScriptPackageEntity::class,
        ScriptConfigurationEntity::class,
        ScriptConfigurationItemEntity::class,
        ScriptEntity::class,
        ScriptParameterEntity::class,
        TaskEntity::class,
        TaskStatusEntity::class,
        TaskConfigurationEntity::class,
        ScriptPreferenceEntity::class,
        ScriptNotificationOverrideEntity::class,
        DatasetGroupEntity::class,
        DatasetGroupItemDefinitionEntity::class,
        DatasetEntity::class,
        DatasetItemEntity::class,
        LogEventEntity::class,
        NotificationHistoryEntity::class,
        NotificationHistoryAttemptEntity::class,
        ArtifactEntity::class,
    ],
    version = 13,
    exportSchema = true,
)
@TypeConverters(
    DateTimeConverter::class,
    UuidConverter::class,
    JsonConverter::class,
    UserEncryptedStringConverter::class,
    ValueTypeConverter::class,
)
abstract class UserRoomDatabase : RoomDatabase() {
    abstract fun applicationSettingsDao(): ApplicationSettingsDao

    abstract fun proxyDao(): ProxyDao

    abstract fun proxyProviderConnectorDao(): ProxyProviderConnectorDao

    abstract fun scriptInstanceDao(): ScriptInstanceDao

    abstract fun scriptPreferenceDao(): ScriptPreferenceDao

    abstract fun datasetDao(): DatasetDao

    abstract fun logEventDao(): LogEventDao

    abstract fun notificationHistoryDao(): NotificationHistoryDao

    abstract fun artifactDao(): ArtifactDao
}

/**
 * Manages Room database instances for application and user data
 * Thread-safe implementation with single active user database to simplify code
 */
class RoomDatabases(
    private val applicationConfig: ApplicationConfig,
) : KoinComponent {
    @Volatile
    private var applicationDatabase: ApplicationRoomDatabase? = null

    private val applicationDatabaseLock = Any()

    val applicationEncryptionKey by lazy {
        Encryption.getEncryptionKey(applicationConfig.databaseEncryptionKey, null, 32)
    }

    /**
     * Get or create the application database
     * Thread-safe implementation using double-checked locking pattern
     */
    fun getApplicationDatabase(): ApplicationRoomDatabase =
        applicationDatabase ?: synchronized(applicationDatabaseLock) {
            applicationDatabase ?: run {
                val database =
                    DatabaseConnector.connectApplication(
                        applicationConfig.databaseDirectory,
                        applicationConfig.databaseName,
                    )
                applicationDatabase = database
                database
            }
        }

    /**
     * Get or create a user-specific database
     * Thread-safe implementation with single active user database
     * Throws an exception if a different user is requested while another user's database is open
     */
    fun getUserDatabase(user: User): UserRoomDatabase {
        val scope =
            getKoin().getScopeOrNull(user.id)
                ?: error("User scope not found for user ${user.id}. Is the user authenticated?")
        return scope.get()
    }

    /**
     * Close all database connections
     * Thread-safe cleanup of all cached database instances
     */
    internal fun closeAll() {
        closeApplicationDatabase()
        // User database is closed when the scope is closed by MarketplaceUser
    }

    fun closeApplicationDatabase() {
        synchronized(applicationDatabaseLock) {
            applicationDatabase?.close()
            applicationDatabase = null
        }
    }
}
