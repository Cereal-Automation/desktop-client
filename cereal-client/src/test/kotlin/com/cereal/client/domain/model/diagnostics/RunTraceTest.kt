package com.cereal.client.domain.model.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class RunTraceTest {
    @ParameterizedTest
    @CsvSource("ok,false", "crash,true", "fail,true", "OK,true")
    fun `a check failed unless its outcome is exactly ok`(
        outcome: String,
        failed: Boolean,
    ) {
        assertEquals(failed, runTrace(outcome = outcome).failed)
    }
}

@OptIn(ExperimentalTime::class)
fun runTrace(
    outcome: String = "ok",
    uploadState: RunTrace.UploadState = RunTrace.UploadState.None,
    receivedAt: Instant = Instant.parse("2026-11-04T12:00:00Z"),
) = RunTrace(
    id = "trace-1",
    taskId = "task",
    key = "check-1",
    runId = "run-1",
    outcome = outcome,
    label = "In stock",
    notable = false,
    learned = false,
    sizeBytes = 16,
    receivedAt = receivedAt,
    uploadState = uploadState,
)
