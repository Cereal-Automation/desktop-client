package com.cereal.client.infrastructure.data.datasource.database.integration

import androidx.room.execSQL
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.cereal.client.domain.model.diagnostics.RunTrace
import com.cereal.client.infrastructure.data.datasource.database.room.RoomRunTraceDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** [RoomRunTraceDataSource] against a real SQLite database: the queries retention and Recent checks rely on. */
@OptIn(ExperimentalTime::class)
class RoomRunTraceDataSourceIntegrationTest : RoomUserDatabaseTestBase() {
    private val dataSource by lazy { RoomRunTraceDataSource(roomDatabases) }

    private fun trace(
        id: String,
        taskId: String,
        runId: String? = "run-1",
    ) = RunTrace(
        id = id,
        taskId = taskId,
        key = "check-$id",
        runId = runId,
        outcome = "crash",
        label = "Sold out",
        notable = true,
        learned = false,
        sizeBytes = 512,
        receivedAt = Instant.fromEpochMilliseconds(1_000),
        uploadState = RunTrace.UploadState.Pending,
        note = "should be in stock",
    )

    @Test
    fun `a trace round-trips every field`() =
        runTest {
            val taskId = seedTask()
            val withRun = trace("1", taskId)
            val withoutRun = trace("2", taskId, runId = null).copy(learned = true, notable = false, uploadState = RunTrace.UploadState.None, note = null)

            dataSource.insert(testUser, withRun)
            dataSource.insert(testUser, withoutRun)

            assertEquals(setOf(withRun, withoutRun), dataSource.getAll(testUser).toSet())
        }

    @Test
    fun `observing a task sees only its own traces`() =
        runTest {
            val taskA = seedTask()
            val taskB = seedTask()
            dataSource.insert(testUser, trace("1", taskA))
            dataSource.insert(testUser, trace("2", taskB))

            assertEquals(listOf("1"), dataSource.observeByTaskId(testUser, taskA).first().map { it.id })
        }

    @Test
    fun `delete removes only the given traces`() =
        runTest {
            val taskId = seedTask()
            dataSource.insert(testUser, trace("1", taskId))
            dataSource.insert(testUser, trace("2", taskId))
            dataSource.insert(testUser, trace("3", taskId))

            dataSource.delete(testUser, listOf("1", "3"))

            assertEquals(listOf("2"), dataSource.getAll(testUser).map { it.id })
        }

    @Test
    fun `delete for task removes every trace of that task`() =
        runTest {
            val taskA = seedTask()
            val taskB = seedTask()
            dataSource.insert(testUser, trace("1", taskA))
            dataSource.insert(testUser, trace("2", taskB))

            dataSource.deleteForTask(testUser, taskA)

            assertEquals(listOf("2"), dataSource.getAll(testUser).map { it.id })
        }

    @Test
    fun `a trace goes with its task row`() =
        runTest {
            val taskId = seedTask()
            dataSource.insert(testUser, trace("1", taskId))

            deleteTaskRow(taskId)

            assertTrue(dataSource.getAll(testUser).isEmpty())
        }

    private suspend fun deleteTaskRow(taskId: String) {
        roomDatabases.getUserDatabase(testUser).useWriterConnection { transactor ->
            transactor.immediateTransaction { execSQL("DELETE FROM task WHERE id = '$taskId'") }
        }
    }
}
