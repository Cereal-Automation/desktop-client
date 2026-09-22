package com.cereal.client.infrastructure.data.datasource.database.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Enum for value types used in key-value pairs
 */
enum class ValueType(
    val value: String,
) {
    UNKNOWN("unknown"),
    STRING("string"),

    /**
     * A user-supplied credential. Only the *type label* differs from [STRING] — the value column is an
     * `EncryptedString` for every type already, so no new ciphertext format is introduced (see ADR-0001).
     */
    SECRET("secret"),
    BOOLEAN("boolean"),
    INT("int"),
    FLOAT("float"),
    LONG("long"),
    SHORT("short"),
    DOUBLE("double"),
    PROXY("proxy"),
    PROXY_GROUP("proxy_group"),
    CUSTOM_DATASET_GROUP("custom_dataset_group"),
    CUSTOM_DATASET("custom_dataset"),
    FILE("file"),
    ENUM("enum"),
    INSTANT("instant"),

    /**
     * The rows of a list configuration item, stored as a JSON array of objects in the existing
     * encrypted value column.
     */
    LIST("list"),
}

/**
 * Enum for finished task status
 */
enum class TaskExecutionStatus(
    val value: String,
) {
    IDLE("idle"),
    RUNNING("running"),
    SUCCESS("success"),
    ERROR("error"),
}

/**
 * Room entity for script package groups
 */
@Entity(tableName = "script_package_group")
data class ScriptPackageGroupEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: UUID,
        val name: String,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
    )

/**
 * Room entity for script package groups with script package count
 */
data class ScriptPackageGroupWithCountEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: UUID,
        val name: String,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
        @ColumnInfo(name = "scriptPackageCount")
        val scriptPackageCount: Long,
    )

/**
 * Room entity for script packages with embedded configuration
 */
@Entity(
    tableName = "script_package",
    foreignKeys = [
        ForeignKey(
            entity = ScriptPackageGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["group_id"]),
    ],
)
data class ScriptPackageEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: UUID,
        @ColumnInfo(name = "group_id")
        val groupId: UUID,
        @ColumnInfo(name = "package_name")
        val packageName: String,
        @ColumnInfo(name = "number_of_concurrent_tasks")
        val numberOfConcurrentTasks: Int?,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
    )

/**
 * Room entity for scripts
 */
@Entity(
    tableName = "script",
    foreignKeys = [
        ForeignKey(
            entity = ScriptPackageEntity::class,
            parentColumns = ["id"],
            childColumns = ["package_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["package_id"]),
    ],
)
data class ScriptEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: UUID,
        @ColumnInfo(name = "package_id")
        val packageId: UUID,
        @ColumnInfo(name = "script_id")
        val scriptId: String?,
        @ColumnInfo(name = "parent_script_id")
        val parentScriptId: UUID?,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
    )

/**
 * Room entity for script configurations
 */
@Entity(
    tableName = "script_configuration",
    foreignKeys = [
        ForeignKey(
            entity = ScriptPackageEntity::class,
            parentColumns = ["id"],
            childColumns = ["package_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["package_id"]),
    ],
)
data class ScriptConfigurationEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: UUID,
        @ColumnInfo(name = "package_id")
        val packageId: UUID,
        @ColumnInfo(name = "script_id")
        val scriptId: String?,
        @ColumnInfo(name = "is_main_configuration")
        val isMainConfiguration: Boolean = false,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
    )

/**
 * Room entity for script configuration items (key-value pairs)
 */
@Entity(
    tableName = "script_configuration_item",
    foreignKeys = [
        ForeignKey(
            entity = ScriptConfigurationEntity::class,
            parentColumns = ["id"],
            childColumns = ["configuration_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["configuration_id"]),
    ],
)
data class ScriptConfigurationItemEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: UUID,
        @ColumnInfo(name = "configuration_id")
        val configurationId: UUID,
        val key: String,
        val value: EncryptedString?,
        val type: ValueType,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
    )

/**
 * Room entity for script parameters (key-value pairs)
 */
@Entity(
    tableName = "script_parameter",
    foreignKeys = [
        ForeignKey(
            entity = ScriptEntity::class,
            parentColumns = ["id"],
            childColumns = ["script_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["script_id"]),
    ],
)
data class ScriptParameterEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: UUID,
        @ColumnInfo(name = "script_id")
        val scriptId: UUID,
        val key: String,
        val value: EncryptedString?,
        val type: ValueType,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
    )

/**
 * Room entity for finished tasks
 */
@Entity(
    tableName = "task",
    foreignKeys = [
        ForeignKey(
            entity = ScriptEntity::class,
            parentColumns = ["id"],
            childColumns = ["script_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["script_id"]),
    ],
)
data class TaskEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: UUID,
        @ColumnInfo(name = "script_id")
        val scriptId: UUID,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
    )

/**
 * Room entity for finished task configuration snapshot
 */
@Entity(
    tableName = "task_configuration",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["task_id"]),
    ],
)
data class TaskConfigurationEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: UUID,
        @ColumnInfo(name = "task_id")
        val taskId: UUID,
        val key: String,
        val value: EncryptedString?,
        val type: ValueType,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
    )

/**
 * Room entity for finished task status history
 */
@Entity(
    tableName = "task_status",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["task_id"]),
    ],
)
data class TaskStatusEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: UUID,
        @ColumnInfo(name = "task_id")
        val taskId: UUID,
        val message: EncryptedString?,
        @ColumnInfo(name = "stack_trace")
        val stackTrace: EncryptedString?,
        val timestamp: Long,
        val status: TaskExecutionStatus,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
    )

/**
 * Room entity for script preferences (key-value pairs per script package instance)
 */
@Entity(
    tableName = "script_preference",
    indices = [
        Index(value = ["instance_id"]),
    ],
)
data class ScriptPreferenceEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: UUID,
        @ColumnInfo(name = "instance_id")
        val instanceId: String,
        val key: String,
        val value: EncryptedString?,
        val type: ValueType,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
    )

/**
 * Room entity for script notification overrides.
 * Stores per-script notification channel settings that override global settings.
 */
@Entity(
    tableName = "script_notification_override",
    foreignKeys = [
        ForeignKey(
            entity = ScriptPackageEntity::class,
            parentColumns = ["id"],
            childColumns = ["package_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["package_id"]),
    ],
)
data class ScriptNotificationOverrideEntity
    @OptIn(ExperimentalTime::class)
    constructor(
        @PrimaryKey
        val id: UUID,
        @ColumnInfo(name = "package_id")
        val packageId: UUID,
        // Discord overrides
        @ColumnInfo(name = "discord_webhook_url")
        val discordWebhookUrl: EncryptedString?,
        // Telegram overrides
        @ColumnInfo(name = "telegram_bot_token")
        val telegramBotToken: EncryptedString?,
        @ColumnInfo(name = "telegram_chat_id")
        val telegramChatId: EncryptedString?,
        // Email overrides
        @ColumnInfo(name = "email_smtp_host")
        val emailSmtpHost: EncryptedString?,
        @ColumnInfo(name = "email_smtp_port")
        val emailSmtpPort: Int?,
        @ColumnInfo(name = "email_username")
        val emailUsername: EncryptedString?,
        @ColumnInfo(name = "email_password")
        val emailPassword: EncryptedString?,
        @ColumnInfo(name = "email_from")
        val emailFrom: EncryptedString?,
        @ColumnInfo(name = "email_to")
        val emailTo: EncryptedString?,
        @ColumnInfo(name = "email_use_tls")
        val emailUseTls: Boolean?,
        @ColumnInfo(name = "created_at")
        val createdAt: Instant,
        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant,
    )
