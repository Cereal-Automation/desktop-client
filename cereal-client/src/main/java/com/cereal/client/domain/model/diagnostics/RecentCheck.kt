package com.cereal.client.domain.model.diagnostics

/** One row of Recent checks: a kept own [trace], and whether the user may report it as a wrong result. */
data class RecentCheck(
    val trace: RunTrace,
    val canReport: Boolean,
) {
    /** The row shows "Reported": its report reached the server, or the user flagged it and it's on its way. */
    val reported: Boolean
        get() =
            trace.uploadState == RunTrace.UploadState.Reported ||
                (!trace.failed && trace.uploadState == RunTrace.UploadState.Pending)
}
