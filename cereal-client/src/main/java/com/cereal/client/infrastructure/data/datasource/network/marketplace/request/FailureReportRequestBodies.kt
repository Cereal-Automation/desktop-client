package com.cereal.client.infrastructure.data.datasource.network.marketplace.request

import kotlinx.serialization.Serializable

/** Body of `POST telemetry/goal-runs/{runId}/failure-reports/{key}/uploads`. */
@Serializable
data class FailureReportUploadRequestBody(
    /** Whether to presign a URL for `learned-trace.jsonl.gz` too. */
    val learnedTrace: Boolean,
)

/** Body of `PUT telemetry/goal-runs/{runId}/failure-reports/{key}`, which registers the uploaded report. */
@Serializable
data class FailureReportRegisterRequestBody(
    /** The failed check's outcome tag, or "flagged". */
    val outcome: String,
    val note: String?,
    val clientVersion: String,
)
