package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.telemetry.GoalRunFinishReport
import com.cereal.client.domain.model.telemetry.GoalRunStartReport
import com.cereal.client.domain.provider.GoalRunReportProvider
import com.cereal.client.infrastructure.data.datasource.network.marketplace.MarketplaceApiClient
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.GoalRunFinishRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.GoalRunStartRequestBody
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ExperimentalTime

/** Posts goal run reports to the Web app. A report that fails is logged and dropped: no queue, no retry. */
@OptIn(ExperimentalTime::class)
class GoalRunReportProviderImpl(
    private val apiClient: MarketplaceApiClient,
) : GoalRunReportProvider {
    private val logger = LoggerFactory.getLogger(GoalRunReportProviderImpl::class.java)

    override suspend fun reportStart(report: GoalRunStartReport) =
        dropOnFailure {
            apiClient.reportGoalRunStart(
                with(report) {
                    GoalRunStartRequestBody(
                        runId = runId,
                        installId = installId,
                        goal = goal,
                        startUrl = startUrl,
                        runMode = runMode,
                        variantId = variantId,
                        agentVersion = agentVersion,
                        clientVersion = clientVersion,
                        llmProvider = llmProvider,
                        llmModel = llmModel,
                        startedAt = startedAt.toString(),
                    )
                },
            )
        }

    override suspend fun reportFinish(report: GoalRunFinishReport) =
        dropOnFailure {
            apiClient.reportGoalRunFinish(
                report.runId,
                GoalRunFinishRequestBody(report.outcome.name.lowercase(), report.cost?.toDouble(), report.durationSeconds),
            )
        }

    private suspend fun dropOnFailure(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Dropping goal run report: ${e.message}")
        }
    }
}
