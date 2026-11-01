package com.cereal.client.domain.provider

import com.cereal.client.domain.model.telemetry.GoalRunFinishReport
import com.cereal.client.domain.model.telemetry.GoalRunStartReport

/** Sends goal run reports to our own server (see ADR 0011). Fire-and-forget: implementations never throw. */
interface GoalRunReportProvider {
    suspend fun reportStart(report: GoalRunStartReport)

    suspend fun reportFinish(report: GoalRunFinishReport)
}
