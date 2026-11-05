package com.cereal.client.domain.model.diagnostics

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The MIME type a script emits a **run trace** under (spec #291): `application/vnd.cereal.run-trace; key=<key>;
 * outcome=<tag>; label=<label>[; notable]`. An emit of this type is a run trace, never an artifact.
 */
const val RUN_TRACE_MIME_TYPE = "application/vnd.cereal.run-trace"

/** The outcome tag of a check that ended well; every other tag is a failed ending. */
const val RUN_TRACE_OUTCOME_OK = "ok"

/** Whether [mimeType] is [RUN_TRACE_MIME_TYPE], with or without parameters. */
@OptIn(ExperimentalContracts::class)
fun isRunTraceMimeType(mimeType: String?): Boolean {
    contract { returns(true) implies (mimeType != null) }
    return mimeType
        ?.substringBefore(';')
        ?.trim()
        .equals(RUN_TRACE_MIME_TYPE, ignoreCase = true)
}

/**
 * One kept **run trace**: the record of one check, kept encrypted on this machine. Carries metadata only; the bytes
 * are never shown to the user.
 */
data class RunTrace
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: String,
        val taskId: String,
        /** Opaque to the client: the script's `check-<n>`. */
        val key: String,
        /** The goal run this check belonged to; null when it had none (Share diagnostics was off at Start). */
        val runId: String?,
        val outcome: String,
        /** Short text the script already masked, shown in Recent checks. */
        val label: String,
        /** The script notified the user of this check's result. */
        val notable: Boolean,
        /** The trace of the learned check whose replay cache this check used, rather than the check's own. */
        val learned: Boolean,
        val sizeBytes: Long,
        val receivedAt: Instant,
        val uploadState: UploadState = UploadState.None,
        /** The user's note when flagging the check. */
        val note: String? = null,
    ) {
        val failed: Boolean get() = outcome != RUN_TRACE_OUTCOME_OK

        enum class UploadState { None, Pending, Reported, Dropped }
    }
