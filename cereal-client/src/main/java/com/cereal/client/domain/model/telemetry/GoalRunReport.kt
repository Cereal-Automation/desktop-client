package com.cereal.client.domain.model.telemetry

import java.math.BigDecimal
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A **goal run report**'s first half, sent when an AI browser agent task starts: the goal and a few facts about
 * the run, masked of sensitive-looking text. One per task start, so a restart is a new report. See ADR 0011.
 */
data class GoalRunStartReport
    @OptIn(ExperimentalTime::class)
    constructor(
        val runId: String,
        /** Pseudonymous id of this install, never tied to an account. */
        val installId: String,
        val goal: String,
        /** Scheme, host and path only. */
        val startUrl: String,
        /** The run mode constant's name ("Once"/"Repeat"); empty if unset. */
        val runMode: String,
        val variantId: String?,
        val agentVersion: Long,
        val clientVersion: String,
        val llmProvider: String?,
        val llmModel: String?,
        val startedAt: Instant,
    )

/** A goal run report's second half, joined to its start by [runId]. Missing when the app closed mid-run. */
data class GoalRunFinishReport(
    val runId: String,
    val outcome: GoalRunOutcome,
    /** Latest cost the agent reported; null if it reported none. */
    val cost: BigDecimal?,
    val durationSeconds: Long,
)

enum class GoalRunOutcome { Succeeded, Failed, Stopped }
