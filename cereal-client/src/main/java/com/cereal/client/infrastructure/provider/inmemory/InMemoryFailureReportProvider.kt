package com.cereal.client.infrastructure.provider.inmemory

import com.cereal.client.domain.model.diagnostics.FailureReport
import com.cereal.client.domain.provider.FailureReportProvider
import java.util.concurrent.CopyOnWriteArrayList

/** Keeps failure reports in memory instead of sending them, for the sandboxed `mock` flavor and tests. */
class InMemoryFailureReportProvider : FailureReportProvider {
    /** Every report that got past its first step, whatever [answer] said. */
    val uploads: MutableList<FailureReport> = CopyOnWriteArrayList()

    @Volatile
    var answer = FailureReportProvider.Result.Registered

    override suspend fun upload(
        report: FailureReport,
        mayContinue: () -> Boolean,
    ): FailureReportProvider.Result {
        if (!mayContinue()) return FailureReportProvider.Result.Retry
        uploads += report
        return answer
    }
}
