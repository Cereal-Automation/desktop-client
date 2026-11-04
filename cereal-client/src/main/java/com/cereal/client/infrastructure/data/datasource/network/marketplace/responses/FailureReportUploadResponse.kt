package com.cereal.client.infrastructure.data.datasource.network.marketplace.responses

import kotlinx.serialization.Serializable

/** Presigned PUT URLs for a failure report's traces; [learnedTraceUrl] only when one was asked for. */
@Serializable
data class FailureReportUploadResponse(
    val traceUrl: String,
    val learnedTraceUrl: String? = null,
)
