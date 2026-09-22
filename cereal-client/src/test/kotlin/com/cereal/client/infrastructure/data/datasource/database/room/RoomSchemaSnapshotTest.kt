package com.cereal.client.infrastructure.data.datasource.database.room

import androidx.room.RoomDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.reflect.KClass

/**
 * Verifies that the committed schema snapshots can be materialised into a real database at their
 * current version. This guards against the exported JSON drifting from a deserializable, creatable
 * state. Future schema bumps should additionally call
 * [MigrationTestHelper.runMigrationsAndValidate] from the previous version's snapshot to validate
 * the migration produces the expected schema.
 */
class RoomSchemaSnapshotTest {
    @Test
    fun applicationDatabaseMatchesExportedSchema(
        @TempDir tempDir: Path,
    ) {
        helper(tempDir, "application.db", ApplicationRoomDatabase::class)
            .createDatabase(1)
            .close()
    }

    @Test
    fun userDatabaseMatchesExportedSchema(
        @TempDir tempDir: Path,
    ) {
        helper(tempDir, "user.db", UserRoomDatabase::class)
            .createDatabase(9)
            .close()
    }

    private fun helper(
        tempDir: Path,
        databaseName: String,
        databaseClass: KClass<out RoomDatabase>,
    ) = MigrationTestHelper(
        schemaDirectoryPath = Path("schemas"),
        databasePath = tempDir.resolve(databaseName),
        driver = BundledSQLiteDriver(),
        databaseClass = databaseClass,
    )
}
