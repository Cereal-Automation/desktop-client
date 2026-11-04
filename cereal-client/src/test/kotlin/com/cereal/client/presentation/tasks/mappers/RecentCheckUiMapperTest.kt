package com.cereal.client.presentation.tasks.mappers

import com.cereal.client.application.agent.RecentCheck
import com.cereal.client.domain.model.diagnostics.RunTrace
import com.cereal.client.domain.model.diagnostics.runTrace
import com.cereal.client.presentation.tasks.model.RecentCheckUiModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneId
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class RecentCheckUiMapperTest {
    private val amsterdam = ZoneId.of("Europe/Amsterdam")

    @Test
    fun `a check maps to its row with the time in the user's zone`() {
        val check =
            RecentCheck(
                runTrace(outcome = "crash", uploadState = RunTrace.UploadState.Reported, receivedAt = Instant.parse("2026-11-04T12:34:56Z")),
                canReport = false,
            )

        assertEquals(
            RecentCheckUiModel(
                traceId = "trace-1",
                label = "In stock",
                time = "2026-11-04 13:34:56",
                failed = true,
                reported = true,
                canReport = false,
                noteMaxLength = 500,
            ),
            check.toUiModel(amsterdam),
        )
    }

    @Test
    fun `an ok check the user may report offers it`() {
        val row = RecentCheck(runTrace(outcome = "ok"), canReport = true).toUiModel(amsterdam)

        assertEquals(false, row.failed)
        assertEquals(false, row.reported)
        assertEquals(true, row.canReport)
    }
}
