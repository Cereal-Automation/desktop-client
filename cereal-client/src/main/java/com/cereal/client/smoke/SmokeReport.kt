package com.cereal.client.smoke

/** A single named probe result. */
data class SmokeResult(
    val name: String,
    val outcome: ProbeOutcome,
)

/**
 * Aggregates [SmokeResult]s into a process exit code and a greppable, human-readable report for CI
 * logs. The gate blocks the release (non-zero exit) if any result is [ProbeOutcome.Failed].
 */
object SmokeReport {
    const val EXIT_OK = 0
    const val EXIT_FAILED = 1

    fun exitCode(results: List<SmokeResult>): Int = if (results.any { it.outcome.isBlocking }) EXIT_FAILED else EXIT_OK

    fun render(results: List<SmokeResult>): String {
        val lines = mutableListOf("=== Cereal obfuscation smoke test ===")
        results.forEach { lines.add(line(it)) }

        val failed = results.count { it.outcome is ProbeOutcome.Failed }
        val skipped = results.count { it.outcome is ProbeOutcome.Skipped }
        val passed = results.size - failed - skipped
        val verdict = if (failed == 0) "PASS" else "FAIL"

        lines.add("-------------------------------------")
        lines.add("$verdict — $passed passed, $skipped skipped, $failed failed")
        return lines.joinToString(System.lineSeparator())
    }

    private fun line(result: SmokeResult): String =
        when (val outcome = result.outcome) {
            is ProbeOutcome.Pass -> format("PASS", result.name, outcome.detail)
            is ProbeOutcome.Skipped -> format("SKIP", result.name, outcome.reason)
            is ProbeOutcome.Failed -> format("FAIL", result.name, outcome.reason)
        }

    private fun format(
        tag: String,
        name: String,
        detail: String?,
    ): String = if (detail.isNullOrBlank()) "[$tag] $name" else "[$tag] $name — $detail"
}
