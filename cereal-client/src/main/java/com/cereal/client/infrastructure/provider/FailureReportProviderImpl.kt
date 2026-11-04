package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.diagnostics.FailureReport
import com.cereal.client.domain.provider.FailureReportProvider
import com.cereal.client.domain.provider.FailureReportProvider.Result
import com.cereal.client.infrastructure.data.datasource.network.exception.ApiException
import com.cereal.client.infrastructure.data.datasource.network.marketplace.MarketplaceApiClient
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.FailureReportRegisterRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.FailureReportUploadRequestBody
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

/**
 * Uploads a failure report through the Web app: presign, PUT each trace straight to its presigned Spaces URL, then
 * register. Every step is idempotent on run id + key on the server, so a retry starts over from the presign.
 */
class FailureReportProviderImpl(
    private val apiClient: MarketplaceApiClient,
    private val clientVersion: String,
) : FailureReportProvider {
    private val logger = LoggerFactory.getLogger(FailureReportProviderImpl::class.java)

    override suspend fun upload(
        report: FailureReport,
        mayContinue: () -> Boolean,
    ): Result =
        try {
            uploadOrThrow(report, mayContinue)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            val status = e.httpStatus
            val drop = status != null && status in HTTP_CLIENT_ERRORS && status != HTTP_TOO_MANY_REQUESTS
            logger.warn("Failure report ${report.key} ${if (drop) "dropped" else "will be retried"}: ${e.message}")
            if (drop) Result.Drop else Result.Retry
        } catch (e: Exception) {
            // A network error, or anything else that may pass: the report is kept locally and tried again.
            logger.warn("Failure report ${report.key} will be retried: ${e.message}")
            Result.Retry
        }

    private suspend fun uploadOrThrow(
        report: FailureReport,
        mayContinue: () -> Boolean,
    ): Result {
        if (!mayContinue()) return Result.Retry
        val urls = apiClient.presignFailureReport(report.runId, report.key, FailureReportUploadRequestBody(learnedTrace = report.learnedTrace != null))
        val puts = listOf(urls.traceUrl to report.trace) + listOfNotNull(report.learnedTrace?.let { bytes -> urls.learnedTraceUrl?.let { it to bytes } })
        for ((url, bytes) in puts) {
            if (!mayContinue()) return Result.Retry
            apiClient.putToPresignedUrl(url, bytes)
        }
        if (!mayContinue()) return Result.Retry
        apiClient.registerFailureReport(report.runId, report.key, FailureReportRegisterRequestBody(report.outcome, report.note, clientVersion))
        return Result.Registered
    }

    private companion object {
        val HTTP_CLIENT_ERRORS = 400..499
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
