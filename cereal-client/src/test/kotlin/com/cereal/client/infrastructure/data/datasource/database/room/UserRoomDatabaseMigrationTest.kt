package com.cereal.client.infrastructure.data.datasource.database.room

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UserRoomDatabaseMigrationTest {
    @Test
    fun testMigration1To2() {
        val driver = BundledSQLiteDriver()
        // Use in-memory database
        val connection = driver.open(":memory:")

        try {
            // Run migration
            DatabaseConnector.MIGRATION_1_2.migrate(connection)

            // Verify table exists
            var exists = false
            connection.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='script_notification_override'").use { stmt ->
                if (stmt.step()) {
                    exists = true
                }
            }
            assertTrue(exists, "script_notification_override should exist after migration")

            // Verify columns
            val columns = mutableListOf<String>()
            connection.prepare("PRAGMA table_info(script_notification_override)").use { stmt ->
                while (stmt.step()) {
                    columns.add(stmt.getText(1)) // name is usually index 1
                }
            }
            assertTrue(columns.contains("discord_webhook_url"), "Column discord_webhook_url should exist")
            assertTrue(columns.contains("telegram_bot_token"), "Column telegram_bot_token should exist")
        } finally {
            connection.close()
        }
    }

    @Test
    fun testMigration2To3() {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(":memory:")

        try {
            // Setup V2 state (tables exist)
            connection.execSQL("CREATE TABLE account (id INTEGER PRIMARY KEY)")
            connection.execSQL("CREATE TABLE account_group (id INTEGER PRIMARY KEY)")
            connection.execSQL("CREATE TABLE billing_profile (id INTEGER PRIMARY KEY)")
            connection.execSQL("CREATE TABLE billing_profile_group (id INTEGER PRIMARY KEY)")
            connection.execSQL("CREATE TABLE credit_card (id INTEGER PRIMARY KEY)")
            connection.execSQL("CREATE TABLE address (id INTEGER PRIMARY KEY)")

            // Run migration
            DatabaseConnector.MIGRATION_2_3.migrate(connection)

            // Verify tables are gone
            val tablesToCheck = listOf("account", "account_group", "billing_profile", "billing_profile_group", "credit_card", "address")

            tablesToCheck.forEach { tableName ->
                var exists = false
                connection.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'").use { stmt ->
                    if (stmt.step()) {
                        exists = true
                    }
                }
                assertFalse(exists, "Table $tableName should be dropped")
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun testMigration3To4() {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(":memory:")

        try {
            // Create a minimal v3 finished_task_status table (no stack_trace column)
            connection.execSQL(
                """
                CREATE TABLE finished_task_status (
                    id TEXT NOT NULL PRIMARY KEY,
                    finished_task_id TEXT NOT NULL,
                    message TEXT,
                    timestamp INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )

            // Insert a row to verify data survives the migration
            connection.execSQL(
                """
                INSERT INTO finished_task_status (id, finished_task_id, message, timestamp, status, created_at, updated_at)
                VALUES ('test-id', 'task-id', NULL, 1000, 'ERROR', 1000, 1000)
                """.trimIndent(),
            )

            // Run migration
            DatabaseConnector.MIGRATION_3_4.migrate(connection)

            // Verify stack_trace column exists
            val columns = mutableListOf<String>()
            connection.prepare("PRAGMA table_info(finished_task_status)").use { stmt ->
                while (stmt.step()) {
                    columns.add(stmt.getText(1))
                }
            }
            assertTrue(columns.contains("stack_trace"), "Column stack_trace should exist after migration")

            // Verify existing row is still readable and stack_trace is null
            connection.prepare("SELECT stack_trace FROM finished_task_status WHERE id='test-id'").use { stmt ->
                assertTrue(stmt.step(), "Existing row should still be present after migration")
                assertTrue(stmt.isNull(0), "stack_trace should be null for pre-migration rows")
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun testMigration4To5() {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(":memory:")

        try {
            // Setup V4 state: original table names + indexes
            connection.execSQL("CREATE TABLE finished_task (id TEXT NOT NULL PRIMARY KEY, script_id TEXT NOT NULL)")
            connection.execSQL("CREATE INDEX index_finished_task_script_id ON finished_task(script_id)")
            connection.execSQL("CREATE TABLE finished_task_configuration (id TEXT NOT NULL PRIMARY KEY, finished_task_id TEXT NOT NULL)")
            connection.execSQL(
                "CREATE INDEX index_finished_task_configuration_finished_task_id ON finished_task_configuration(finished_task_id)",
            )
            connection.execSQL("CREATE TABLE finished_task_status (id TEXT NOT NULL PRIMARY KEY, finished_task_id TEXT NOT NULL)")
            connection.execSQL(
                "CREATE INDEX index_finished_task_status_finished_task_id ON finished_task_status(finished_task_id)",
            )

            // Run migration
            DatabaseConnector.MIGRATION_4_5.migrate(connection)

            // Verify renamed tables exist and old ones are gone
            assertTrue(tableExists(connection, "task"), "finished_task should be renamed to task")
            assertTrue(tableExists(connection, "task_configuration"), "finished_task_configuration should be renamed")
            assertTrue(tableExists(connection, "task_status"), "finished_task_status should be renamed")
            assertFalse(tableExists(connection, "finished_task"), "Old finished_task should not exist")

            // Verify renamed columns
            assertTrue(columnsOf(connection, "task_configuration").contains("task_id"), "Column finished_task_id should be renamed to task_id")
            assertTrue(columnsOf(connection, "task_status").contains("task_id"), "Column finished_task_id should be renamed to task_id")

            // Verify indexes were recreated
            assertTrue(indexExists(connection, "index_task_script_id"), "index_task_script_id should exist")
            assertFalse(indexExists(connection, "index_finished_task_script_id"), "Old index should be dropped")
        } finally {
            connection.close()
        }
    }

    @Test
    fun testMigration5To6() {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(":memory:")

        try {
            // Setup V5 state: task table still has the redundant task_id column
            connection.execSQL("CREATE TABLE task (id TEXT NOT NULL PRIMARY KEY, script_id TEXT NOT NULL, task_id TEXT)")

            // Run migration
            DatabaseConnector.MIGRATION_5_6.migrate(connection)

            // Verify redundant column is gone
            assertFalse(columnsOf(connection, "task").contains("task_id"), "Redundant task_id column should be dropped")
            assertTrue(columnsOf(connection, "task").contains("script_id"), "script_id column should be preserved")
        } finally {
            connection.close()
        }
    }

    @Test
    fun testMigration6To7() {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(":memory:")

        try {
            // Run migration on an empty database
            DatabaseConnector.MIGRATION_6_7.migrate(connection)

            // Verify log_event table and index exist
            assertTrue(tableExists(connection, "log_event"), "log_event table should be created")
            val columns = columnsOf(connection, "log_event")
            assertTrue(columns.contains("task_id"), "Column task_id should exist")
            assertTrue(columns.contains("priority"), "Column priority should exist")
            assertTrue(columns.contains("message"), "Column message should exist")
            assertTrue(indexExists(connection, "index_log_event_task_id"), "index_log_event_task_id should exist")
        } finally {
            connection.close()
        }
    }

    @Test
    fun testMigration7To8() {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(":memory:")

        try {
            // Setup V7 proxy table without the health columns
            connection.execSQL("CREATE TABLE proxy (id TEXT NOT NULL PRIMARY KEY, host TEXT NOT NULL)")

            // Run migration
            DatabaseConnector.MIGRATION_7_8.migrate(connection)

            // Verify new columns exist
            val columns = columnsOf(connection, "proxy")
            assertTrue(columns.contains("health_status"), "Column health_status should exist")
            assertTrue(columns.contains("last_checked_at"), "Column last_checked_at should exist")
            assertTrue(columns.contains("latency_ms"), "Column latency_ms should exist")
            assertTrue(columns.contains("last_error"), "Column last_error should exist")
        } finally {
            connection.close()
        }
    }

    @Test
    fun testMigration8To9() {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(":memory:")

        try {
            // Notification history references task(id); create a minimal task table so the FK target exists.
            connection.execSQL("CREATE TABLE task (id TEXT NOT NULL PRIMARY KEY, script_id TEXT NOT NULL)")

            // Run migration on a database without the notification history tables.
            DatabaseConnector.MIGRATION_8_9.migrate(connection)

            // Verify notification_history table, columns and indexes exist.
            assertTrue(tableExists(connection, "notification_history"), "notification_history table should be created")
            val historyColumns = columnsOf(connection, "notification_history")
            assertTrue(historyColumns.contains("task_id"), "Column task_id should exist")
            assertTrue(historyColumns.contains("title"), "Column title should exist")
            assertTrue(historyColumns.contains("message"), "Column message should exist")
            assertTrue(historyColumns.contains("timestamp"), "Column timestamp should exist")
            assertTrue(indexExists(connection, "index_notification_history_task_id"), "task_id index should exist")
            assertTrue(indexExists(connection, "index_notification_history_timestamp"), "timestamp index should exist")

            // Verify notification_history_attempt table, columns and index exist.
            assertTrue(tableExists(connection, "notification_history_attempt"), "notification_history_attempt table should be created")
            val attemptColumns = columnsOf(connection, "notification_history_attempt")
            assertTrue(attemptColumns.contains("notification_id"), "Column notification_id should exist")
            assertTrue(attemptColumns.contains("channel"), "Column channel should exist")
            assertTrue(attemptColumns.contains("status"), "Column status should exist")
            assertTrue(attemptColumns.contains("position"), "Column position should exist")
            assertTrue(
                indexExists(connection, "index_notification_history_attempt_notification_id"),
                "notification_id index should exist",
            )
        } finally {
            connection.close()
        }
    }

    @Test
    fun testMigration10To11() {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(":memory:")

        try {
            // Run migration on a database without the connector table.
            DatabaseConnector.MIGRATION_10_11.migrate(connection)

            assertTrue(tableExists(connection, "proxy_provider_connector"), "proxy_provider_connector table should be created")
            val columns = columnsOf(connection, "proxy_provider_connector")
            assertTrue(columns.contains("provider"), "Column provider should exist")
            assertTrue(columns.contains("connected_at"), "Column connected_at should exist")
            assertTrue(columns.contains("last_sync_at"), "Column last_sync_at should exist")
            assertTrue(columns.contains("subuser_hash"), "Column subuser_hash should exist")
            assertTrue(columns.contains("available_traffic_gb"), "Column available_traffic_gb should exist")
            assertTrue(columns.contains("subuser_count"), "Column subuser_count should exist")
            assertTrue(columns.contains("credential_key"), "Column credential_key should exist")

            // The new table is writable and provider is the primary key.
            connection.execSQL(
                "INSERT INTO proxy_provider_connector (provider, connected_at, last_sync_at, subuser_hash, available_traffic_gb, subuser_count, credential_key) " +
                    "VALUES ('MARSPROXIES', 1000, 2000, 'hash', 84.2, 3, 'key_mars_proxies_api_token')",
            )
            connection.prepare("SELECT subuser_hash FROM proxy_provider_connector WHERE provider='MARSPROXIES'").use { stmt ->
                assertTrue(stmt.step(), "Inserted connector row should be readable")
                assertTrue(stmt.getText(0) == "hash", "subuser_hash should round-trip")
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun testMigration11To12() {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(":memory:")

        try {
            // Setup V11 proxy_group without the provider + geo_label columns and seed a row.
            connection.execSQL("CREATE TABLE proxy_group (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
            connection.execSQL("INSERT INTO proxy_group (id, name) VALUES ('g1', 'Existing')")

            // Run migration
            DatabaseConnector.MIGRATION_11_12.migrate(connection)

            // Verify new nullable columns exist.
            val columns = columnsOf(connection, "proxy_group")
            assertTrue(columns.contains("provider"), "Column provider should exist")
            assertTrue(columns.contains("geo_label"), "Column geo_label should exist")

            // Existing rows survive and the new columns default to NULL.
            connection.prepare("SELECT name, provider, geo_label FROM proxy_group WHERE id='g1'").use { stmt ->
                assertTrue(stmt.step(), "Existing row should still be present after migration")
                assertTrue(stmt.getText(0) == "Existing", "name should round-trip")
                assertTrue(stmt.isNull(1), "provider should be null for pre-migration rows")
                assertTrue(stmt.isNull(2), "geo_label should be null for pre-migration rows")
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun `testMigration12To13 removes string list configuration values`() {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(":memory:")

        try {
            seedVersion12ValueTables(connection)

            DatabaseConnector.MIGRATION_12_13.migrate(connection)

            assertEquals(emptyList<String>(), keysWithType(connection, "script_configuration_item", "STRING_LIST"))
            assertEquals(emptyList<String>(), keysWithType(connection, "task_configuration", "STRING_LIST"))

            // Everything the removal does not concern is left where it was.
            assertEquals(listOf("name"), keysWithType(connection, "script_configuration_item", "STRING"))
            assertEquals(listOf("name"), keysWithType(connection, "task_configuration", "STRING"))
        } finally {
            connection.close()
        }
    }

    @Test
    fun `testMigration12To13 leaves string list script parameters untouched`() {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(":memory:")

        try {
            seedVersion12ValueTables(connection)

            DatabaseConnector.MIGRATION_12_13.migrate(connection)

            // A script writes its own key-value state here, so a STRING_LIST row is live data rather
            // than the residue of a removed configuration type.
            assertEquals(listOf("history"), keysWithType(connection, "script_parameter", "STRING_LIST"))
            assertEquals(listOf("rows"), keysWithType(connection, "script_parameter", "OBJECT_LIST"))
        } finally {
            connection.close()
        }
    }

    @Test
    fun `testMigration12To13 relabels object list configuration values as list`() {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(":memory:")

        try {
            seedVersion12ValueTables(connection)

            DatabaseConnector.MIGRATION_12_13.migrate(connection)

            assertEquals(listOf("rows"), keysWithType(connection, "script_configuration_item", "LIST"))
            assertEquals(listOf("rows"), keysWithType(connection, "task_configuration", "LIST"))
            assertEquals(emptyList<String>(), keysWithType(connection, "script_configuration_item", "OBJECT_LIST"))
            assertEquals(emptyList<String>(), keysWithType(connection, "task_configuration", "OBJECT_LIST"))

            // The stored rows themselves are only relabelled, never rewritten.
            connection.prepare("SELECT value FROM script_configuration_item WHERE key = 'rows'").use { stmt ->
                assertTrue(stmt.step(), "Relabelled row should still be present")
                assertEquals("""[{"sku":"ABC"}]""", stmt.getText(0))
            }
        } finally {
            connection.close()
        }
    }

    /**
     * The version-12 shape of the three tables holding typed values, each seeded with one row per type
     * the migration touches plus a plain string row that must survive.
     */
    private fun seedVersion12ValueTables(connection: SQLiteConnection) {
        listOf(
            "script_configuration_item" to "configuration_id",
            "script_parameter" to "script_id",
            "task_configuration" to "task_id",
        ).forEach { (table, ownerColumn) ->
            connection.execSQL(
                "CREATE TABLE $table (id TEXT NOT NULL PRIMARY KEY, $ownerColumn TEXT NOT NULL, " +
                    "key TEXT NOT NULL, value TEXT, type TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)",
            )
            connection.execSQL(
                "INSERT INTO $table (id, $ownerColumn, key, value, type, created_at, updated_at) VALUES " +
                    "('$table-1', 'owner', 'history', '[\"a\",\"b\"]', 'STRING_LIST', 1000, 1000), " +
                    "('$table-2', 'owner', 'rows', '[{\"sku\":\"ABC\"}]', 'OBJECT_LIST', 1000, 1000), " +
                    "('$table-3', 'owner', 'name', 'Ada', 'STRING', 1000, 1000)",
            )
        }
    }

    private fun keysWithType(
        connection: SQLiteConnection,
        table: String,
        type: String,
    ): List<String> {
        val keys = mutableListOf<String>()
        connection.prepare("SELECT key FROM $table WHERE type = '$type' ORDER BY key").use { stmt ->
            while (stmt.step()) {
                keys.add(stmt.getText(0))
            }
        }
        return keys
    }

    private fun tableExists(
        connection: SQLiteConnection,
        name: String,
    ): Boolean =
        connection.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='$name'").use { stmt ->
            stmt.step()
        }

    private fun indexExists(
        connection: SQLiteConnection,
        name: String,
    ): Boolean =
        connection.prepare("SELECT name FROM sqlite_master WHERE type='index' AND name='$name'").use { stmt ->
            stmt.step()
        }

    private fun columnsOf(
        connection: SQLiteConnection,
        table: String,
    ): List<String> {
        val columns = mutableListOf<String>()
        connection.prepare("PRAGMA table_info($table)").use { stmt ->
            while (stmt.step()) {
                columns.add(stmt.getText(1))
            }
        }
        return columns
    }
}
