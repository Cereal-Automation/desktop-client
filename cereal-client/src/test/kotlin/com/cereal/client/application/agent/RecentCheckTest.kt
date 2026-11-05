package com.cereal.client.application.agent

import com.cereal.client.domain.model.diagnostics.RunTrace.UploadState
import com.cereal.client.domain.model.diagnostics.runTrace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class RecentCheckTest {
    @ParameterizedTest
    @CsvSource(
        // A failed check's report reached the server; while pending or dropped the user did nothing to show.
        "crash,None,false",
        "crash,Pending,false",
        "crash,Reported,true",
        "crash,Dropped,false",
        // An ok check the user flagged: reported from the moment they did, even when its upload was dropped.
        "ok,None,false",
        "ok,Pending,true",
        "ok,Reported,true",
        "ok,Dropped,true",
    )
    fun `a row shows reported once its report reached the server or the user flagged it`(
        outcome: String,
        uploadState: UploadState,
        reported: Boolean,
    ) {
        assertEquals(reported, RecentCheck(runTrace(outcome = outcome, uploadState = uploadState), canReport = false).reported)
    }
}
