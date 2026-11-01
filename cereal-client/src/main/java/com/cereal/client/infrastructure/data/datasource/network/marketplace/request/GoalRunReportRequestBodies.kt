package com.cereal.client.infrastructure.data.datasource.network.marketplace.request

import kotlinx.serialization.Serializable

/** Body of `POST telemetry/goal-runs`; the server ignores fields it doesn't know. */
@Serializable
data class GoalRunStartRequestBody(
    val runId: String,
    val installId: String,
    val goal: String,
    val startUrl: String,
    val runMode: String,
    val variantId: String?,
    val agentVersion: Long,
    val clientVersion: String,
    val llmProvider: String?,
    val llmModel: String?,
    /** ISO-8601 instant. */
    val startedAt: String,
)

/** Body of `PATCH telemetry/goal-runs/{runId}`. */
@Serializable
data class GoalRunFinishRequestBody(
    /** "succeeded", "failed" or "stopped". */
    val outcome: String,
    /** Decimal string such as "0.42", exact as the agent reported it. */
    val cost: String?,
    val durationSeconds: Long,
)
