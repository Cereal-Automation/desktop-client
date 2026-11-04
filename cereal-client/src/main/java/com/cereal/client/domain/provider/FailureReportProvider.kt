package com.cereal.client.domain.provider

import com.cereal.client.domain.model.diagnostics.FailureReport

/**
 * Uploads **failure reports** to our own server (spec #291): presign, PUT the traces to the presigned URLs, register.
 * Never throws: the result says what to do with the report, so the caller holds the retry rules.
 */
interface FailureReportProvider {
    /** Uploads [report], checking [mayContinue] before each step; stopping before a step answers [Result.Retry]. */
    suspend fun upload(
        report: FailureReport,
        mayContinue: () -> Boolean,
    ): Result

    enum class Result {
        /** The server holds the report. */
        Registered,

        /** Trying again later may work: a network error, a 5xx, a 429, any failed PUT to a presigned URL, or a stop before a step. */
        Retry,

        /** Trying again won't help: a 404 (no goal run row) or another 4xx from the presign or register. */
        Drop,
    }
}
