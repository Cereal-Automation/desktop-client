package com.cereal.client.infrastructure.provider.inmemory

import com.cereal.client.domain.model.telemetry.GoalRunFinishReport
import com.cereal.client.domain.model.telemetry.GoalRunStartReport
import com.cereal.client.domain.provider.GoalRunReportProvider
import java.util.concurrent.CopyOnWriteArrayList

/** Keeps reports in memory instead of sending them, for the sandboxed `mock` flavor and tests. */
class InMemoryGoalRunReportProvider : GoalRunReportProvider {
    val starts: MutableList<GoalRunStartReport> = CopyOnWriteArrayList()
    val finishes: MutableList<GoalRunFinishReport> = CopyOnWriteArrayList()

    override suspend fun reportStart(report: GoalRunStartReport) {
        starts += report
    }

    override suspend fun reportFinish(report: GoalRunFinishReport) {
        finishes += report
    }
}
