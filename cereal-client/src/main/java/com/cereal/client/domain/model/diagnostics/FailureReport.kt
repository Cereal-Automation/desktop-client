package com.cereal.client.domain.model.diagnostics

/** What a **failure report** sends: a check's trace, the learned check's trace when there is one, and why. */
class FailureReport(
    val runId: String,
    val key: String,
    /** The failed check's outcome tag, or [OUTCOME_FLAGGED] for an ok check the user reported as wrong. */
    val outcome: String,
    /** The user's note when flagging. */
    val note: String?,
    val trace: ByteArray,
    val learnedTrace: ByteArray?,
) {
    companion object {
        const val OUTCOME_FLAGGED = "flagged"
    }
}
