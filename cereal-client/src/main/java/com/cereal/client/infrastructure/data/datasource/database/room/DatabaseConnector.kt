package com.cereal.client.infrastructure.data.datasource.database.room

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Handles database connections for Room multiplatform
 */
object DatabaseConnector {
    /**
     * Migration from version 1 to 2: Add script_notification_override table
     */
    internal val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS script_notification_override (
                        id TEXT NOT NULL PRIMARY KEY,
                        package_id TEXT NOT NULL,
                        discord_webhook_url TEXT,
                        telegram_bot_token TEXT,
                        telegram_chat_id TEXT,
                        email_smtp_host TEXT,
                        email_smtp_port INTEGER,
                        email_username TEXT,
                        email_password TEXT,
                        email_from TEXT,
                        email_to TEXT,
                        email_use_tls INTEGER,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY (package_id) REFERENCES script_package(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_script_notification_override_package_id ON script_notification_override(package_id)",
                )
            }
        }

    internal val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                // Drop Account related tables
                connection.execSQL("DROP TABLE IF EXISTS account_group")
                connection.execSQL("DROP TABLE IF EXISTS account")

                // Drop BillingProfile related tables
                connection.execSQL("DROP TABLE IF EXISTS billing_profile_group")
                connection.execSQL("DROP TABLE IF EXISTS billing_profile")
                connection.execSQL("DROP TABLE IF EXISTS credit_card")
                connection.execSQL("DROP TABLE IF EXISTS address")
            }
        }

    internal val MIGRATION_3_4 =
        object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE finished_task_status ADD COLUMN stack_trace TEXT")
            }
        }

    internal val MIGRATION_4_5 =
        object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                // Rename tables
                connection.execSQL("ALTER TABLE finished_task RENAME TO task")
                connection.execSQL("ALTER TABLE finished_task_configuration RENAME TO task_configuration")
                connection.execSQL("ALTER TABLE task_configuration RENAME COLUMN finished_task_id TO task_id")
                connection.execSQL("ALTER TABLE finished_task_status RENAME TO task_status")
                connection.execSQL("ALTER TABLE task_status RENAME COLUMN finished_task_id TO task_id")

                // Update indexes
                connection.execSQL("DROP INDEX IF EXISTS index_finished_task_script_id")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_task_script_id ON task(script_id)")

                connection.execSQL("DROP INDEX IF EXISTS index_finished_task_configuration_finished_task_id")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_task_configuration_task_id ON task_configuration(task_id)")

                connection.execSQL("DROP INDEX IF EXISTS index_finished_task_status_finished_task_id")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_task_status_task_id ON task_status(task_id)")
            }
        }

    internal val MIGRATION_5_6 =
        object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                // Drop redundant task_id column
                connection.execSQL("ALTER TABLE task DROP COLUMN task_id")
            }
        }

    internal val MIGRATION_6_7 =
        object : Migration(6, 7) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS log_event (
                        id TEXT NOT NULL PRIMARY KEY,
                        task_id TEXT NOT NULL,
                        priority TEXT NOT NULL,
                        message TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        FOREIGN KEY(task_id) REFERENCES task(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_log_event_task_id ON log_event(task_id)",
                )
            }
        }

    internal val MIGRATION_7_8 =
        object : Migration(7, 8) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE proxy ADD COLUMN health_status TEXT")
                connection.execSQL("ALTER TABLE proxy ADD COLUMN last_checked_at INTEGER")
                connection.execSQL("ALTER TABLE proxy ADD COLUMN latency_ms INTEGER")
                connection.execSQL("ALTER TABLE proxy ADD COLUMN last_error TEXT")
            }
        }

    /**
     * Migration from version 8 to 9: Add notification_history and notification_history_attempt tables
     */
    internal val MIGRATION_8_9 =
        object : Migration(8, 9) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notification_history (
                        id TEXT NOT NULL PRIMARY KEY,
                        task_id TEXT NOT NULL,
                        title TEXT,
                        message TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notification_history_task_id ON notification_history(task_id)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notification_history_timestamp ON notification_history(timestamp)",
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notification_history_attempt (
                        id TEXT NOT NULL PRIMARY KEY,
                        notification_id TEXT NOT NULL,
                        channel TEXT NOT NULL,
                        status TEXT NOT NULL,
                        payload TEXT,
                        error_message TEXT,
                        timestamp INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        FOREIGN KEY (notification_id) REFERENCES notification_history(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notification_history_attempt_notification_id ON notification_history_attempt(notification_id)",
                )
            }
        }

    /**
     * Migration from version 9 to 10: Add artifact table for script-emitted downloadable output.
     */
    internal val MIGRATION_9_10 =
        object : Migration(9, 10) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS artifact (
                        id TEXT NOT NULL PRIMARY KEY,
                        task_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        mime_type TEXT,
                        size_bytes INTEGER NOT NULL,
                        relative_path TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_artifact_task_id ON artifact(task_id)",
                )
            }
        }

    /**
     * Migration from version 10 to 11: Add proxy_provider_connector table for the proxy-provider connector.
     */
    internal val MIGRATION_10_11 =
        object : Migration(10, 11) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS proxy_provider_connector (
                        provider TEXT NOT NULL PRIMARY KEY,
                        connected_at INTEGER NOT NULL,
                        last_sync_at INTEGER NOT NULL,
                        subuser_hash TEXT NOT NULL,
                        available_traffic_gb REAL NOT NULL,
                        subuser_count INTEGER NOT NULL,
                        credential_key TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

    /**
     * Migration from version 11 to 12: Add provider + geo_label columns to proxy_group for the
     * MarsProxies sync (stamps the group with its source provider and the geo targeting label).
     */
    internal val MIGRATION_11_12 =
        object : Migration(11, 12) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE proxy_group ADD COLUMN provider TEXT")
                connection.execSQL("ALTER TABLE proxy_group ADD COLUMN geo_label TEXT")
            }
        }

    /**
     * Migration from version 12 to 13: `List<String>` is no longer a configuration return type, and the
     * surviving list type is relabelled from `OBJECT_LIST` to `LIST`.
     *
     * The two configuration tables hold values whose type label is written from a script's *declared*
     * configuration. A `STRING_LIST` row there belongs to an item the platform now rejects, so the row
     * describes a value nothing can consume — it is deleted rather than translated: the replacement
     * record's field key is unknowable from the old data.
     *
     * `script_parameter` is deliberately left alone. That table is a script's own key-value state, which
     * a script writes directly, so a `STRING_LIST` row there is live data rather than the residue of a
     * removed configuration type.
     */
    internal val MIGRATION_12_13 =
        object : Migration(12, 13) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("DELETE FROM script_configuration_item WHERE type = 'STRING_LIST'")
                connection.execSQL("DELETE FROM task_configuration WHERE type = 'STRING_LIST'")

                connection.execSQL("UPDATE script_configuration_item SET type = 'LIST' WHERE type = 'OBJECT_LIST'")
                connection.execSQL("UPDATE task_configuration SET type = 'LIST' WHERE type = 'OBJECT_LIST'")
            }
        }

    /**
     * Connect to an application Room database
     *
     * @param directory The directory where the database file should be stored
     * @param databaseName The name of the database file
     * @return Application Room database instance
     */
    fun connectApplication(
        directory: File,
        databaseName: String,
    ): ApplicationRoomDatabase {
        directory.mkdirs()

        val databaseFile = File(directory, databaseName)

        return buildRoomDatabase<ApplicationRoomDatabase>(databaseFile) {
            // No migrations for the application database (v1 only)
        }
    }

    /**
     * Connect to a user-specific Room database with encryption
     *
     * @param directory The directory where the database file should be stored
     * @param userId The user ID for the database name
     * @return User Room database instance
     */
    fun connectUser(
        directory: File,
        userId: String,
    ): UserRoomDatabase {
        directory.mkdirs()

        val databaseFile = File(directory, "$userId.db")

        return buildRoomDatabase<UserRoomDatabase>(databaseFile) {
            addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
            )
        }
    }

    private inline fun <reified T : androidx.room.RoomDatabase> buildRoomDatabase(
        databaseFile: File,
        builder: androidx.room.RoomDatabase.Builder<T>.() -> Unit,
    ): T =
        try {
            Room
                .databaseBuilder<T>(
                    name = databaseFile.absolutePath,
                ).setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .apply(builder)
                .build()
        } catch (e: ExceptionInInitializerError) {
            if (e.cause is UnsatisfiedLinkError) {
                throw nativeLibraryFailure(e)
            }
            throw e
        } catch (e: NoClassDefFoundError) {
            if (e.message?.contains("BundledSQLiteDriver") == true) {
                throw nativeLibraryFailure(e)
            }
            throw e
        }

    private fun nativeLibraryFailure(cause: Throwable): RuntimeException {
        val osName = System.getProperty("os.name", "").lowercase()
        val guidance =
            if ("win" in osName) {
                "This usually happens when your IT security policy (WDAC/AppLocker) blocks Cereal from running. " +
                    "Ask your IT administrator to allow Cereal in Windows Application Control, " +
                    "or install Cereal outside a managed environment."
            } else {
                "The bundled SQLite native library could not be loaded. " +
                    "Please ensure your system has the required dependencies."
            }
        return RuntimeException(
            "Unable to initialize the local database. $guidance",
            cause,
        )
    }
}
