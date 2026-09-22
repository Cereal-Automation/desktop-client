package com.cereal.client.smoke

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmokeReportTest {
    @Test
    fun `exit code is OK when nothing failed`() {
        val results =
            listOf(
                SmokeResult("boot", ProbeOutcome.Pass("ok")),
                SmokeResult("network", ProbeOutcome.Pass("environmental")),
                SmokeResult("di-resolve", ProbeOutcome.Skipped("needs params")),
            )

        assertEquals(SmokeReport.EXIT_OK, SmokeReport.exitCode(results))
    }

    @Test
    fun `exit code is FAILED when any probe failed`() {
        val results =
            listOf(
                SmokeResult("boot", ProbeOutcome.Pass()),
                SmokeResult("database", ProbeOutcome.Failed("obfuscation/packaging failure")),
            )

        assertEquals(SmokeReport.EXIT_FAILED, SmokeReport.exitCode(results))
    }

    @Test
    fun `render lists every probe with a tag and a summary verdict`() {
        val report =
            SmokeReport.render(
                listOf(
                    SmokeResult("boot", ProbeOutcome.Pass("App.initialize() completed")),
                    SmokeResult("di-resolve", ProbeOutcome.Skipped("resolved 40, skipped 3")),
                    SmokeResult("database", ProbeOutcome.Failed("NoSuchMethodError")),
                ),
            )

        assertTrue(report.contains("[PASS] boot"))
        assertTrue(report.contains("[SKIP] di-resolve"))
        assertTrue(report.contains("[FAIL] database"))
        assertTrue(report.contains("FAIL — 1 passed, 1 skipped, 1 failed"))
    }

    @Test
    fun `render reports PASS verdict when all pass`() {
        val report = SmokeReport.render(listOf(SmokeResult("boot", ProbeOutcome.Pass())))

        assertTrue(report.contains("PASS — 1 passed, 0 skipped, 0 failed"))
    }
}
