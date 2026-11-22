package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.diagnostics.FailureReport
import com.cereal.client.domain.provider.FailureReportProvider
import com.cereal.client.infrastructure.data.datasource.filesystem.SandboxPlainArtifactFileDataSource.Companion.gunzipIfGzip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * [FailureReportProvider] for the sandboxed `mock` flavor: instead of uploading, writes each report to its own folder
 * under [directory] (`report.txt` with run id, key, outcome and note, plus `trace.jsonl` and, when present,
 * `learned-trace.jsonl`, decompressed to plain JSON Lines) so what would have been sent can be inspected.
 */
class SandboxFailureReportProvider(
    private val directory: File,
) : FailureReportProvider {
    private val logger = LoggerFactory.getLogger(SandboxFailureReportProvider::class.java)

    override suspend fun upload(
        report: FailureReport,
        mayContinue: () -> Boolean,
    ): FailureReportProvider.Result {
        if (!mayContinue()) return FailureReportProvider.Result.Retry
        return withContext(Dispatchers.IO) {
            try {
                val folder = File(directory, "${Instant.now().toEpochMilli()}-${report.key.safeFileName()}")
                folder.mkdirs()
                File(folder, "report.txt").writeText(
                    """
                    runId: ${report.runId}
                    key: ${report.key}
                    outcome: ${report.outcome}
                    note: ${report.note.orEmpty()}
                    """.trimIndent() + "\n",
                )
                File(folder, "trace.jsonl").writeBytes(report.trace.gunzipIfGzip())
                report.learnedTrace?.let { File(folder, "learned-trace.jsonl").writeBytes(it.gunzipIfGzip()) }
                FailureReportProvider.Result.Registered
            } catch (e: IOException) {
                logger.warn("Couldn't write failure report ${report.key} to $directory; will retry.", e)
                FailureReportProvider.Result.Retry
            }
        }
    }

    private fun String.safeFileName() = replace(Regex("[^A-Za-z0-9._-]"), "_")
}
