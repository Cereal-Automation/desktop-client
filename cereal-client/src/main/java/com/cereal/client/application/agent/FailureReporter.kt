package com.cereal.client.application.agent

import com.cereal.client.domain.model.diagnostics.RunTrace
import com.cereal.client.domain.repository.DiagnosticsSharingRepository
import com.cereal.client.domain.repository.RunTraceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

/**
 * The client side of **failure reports** (spec #291, ADR 0012): takes in the run traces the AI browser agent emits,
 * keeps recent ones locally and lists them as Recent checks. A sibling of [GoalRunReporter], whose goal run id it
 * stores with each trace. Never throws at the script: a trace it can't keep is logged and dropped.
 */
@OptIn(ExperimentalTime::class)
class FailureReporter(
    private val runTraceRepository: RunTraceRepository,
    private val goalRunReporter: GoalRunReporter,
    private val diagnosticsSharingRepository: DiagnosticsSharingRepository,
    private val clock: Clock = Clock.System,
) {
    private val logger = LoggerFactory.getLogger(FailureReporter::class.java)

    /** Intake and retention read, decide and delete over all traces, so they run one at a time. */
    private val mutex = Mutex()

    /** Receives a run trace [taskId]'s script emitted as [name] under the reserved [mimeType]. */
    suspend fun receive(
        taskId: String,
        name: String,
        mimeType: String,
        bytes: ByteArray,
    ) {
        try {
            val emit = RunTraceEmit.parse(name, mimeType) ?: return logger.warn("Refusing a run trace with a malformed type: $mimeType")
            if (bytes.size > MAX_TRACE_BYTES) return logger.warn("Refusing run trace ${emit.key}: ${bytes.size} bytes is over the limit")
            mutex.withLock {
                val kept = runTraceRepository.getAll()
                val runId = goalRunReporter.runId(taskId)
                val trace =
                    RunTrace(
                        id = UUID.randomUUID().toString(),
                        taskId = taskId,
                        key = emit.key,
                        runId = runId,
                        outcome = emit.outcome,
                        label = emit.label,
                        notable = emit.notable,
                        learned = emit.learned,
                        sizeBytes = bytes.size.toLong(),
                        receivedAt = clock.now(),
                    )
                val awaitsUpload =
                    !trace.learned && trace.failed && runId != null && diagnosticsSharingRepository.isEnabled() &&
                        kept.count { it.runId == runId && it.uploadState in CLAIMED } < MAX_REPORTS_PER_GOAL_RUN
                runTraceRepository.add(if (awaitsUpload) trace.copy(uploadState = RunTrace.UploadState.Pending) else trace, bytes)
                prune()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Dropping a run trace: ${e.message}")
        }
    }

    /** [taskId]'s kept checks, newest first: its own traces only, never the learned ones. */
    fun observeRecentChecks(taskId: String): Flow<List<RunTrace>> = runTraceRepository.observe(taskId).map { traces -> traces.filterNot { it.learned }.sortedByDescending { it.receivedAt } }

    /** Removes every trace of [taskId], for a task being deleted. */
    suspend fun forgetTask(taskId: String) = mutex.withLock { runTraceRepository.deleteForTask(taskId) }

    /** Deletes every trace the keep rules no longer hold (spec #291, "Retention"). */
    private suspend fun prune() {
        val all = runTraceRepository.getAll()
        val now = clock.now()
        val fresh = all.filter { now - it.receivedAt < MAX_AGE }
        val own =
            fresh
                .filterNot { it.learned }
                .groupBy { it.taskId }
                .values
                .flatMap { traces ->
                    val latest = traces.sortedByDescending { it.receivedAt }.take(OWN_TRACES_PER_TASK).toSet()
                    traces.filter { it in latest || it.notable || it.uploadState == RunTrace.UploadState.Pending }
                }

        // A learned trace lives as long as its check's own trace, which arrives just after it.
        fun RunTrace.check() = Triple(taskId, runId, key)
        val ownChecks = all.filterNot { it.learned }.map { it.check() }.toSet()
        val keptChecks = own.map { it.check() }.toSet()
        val learned = fresh.filter { it.learned && (it.check() in keptChecks || it.check() !in ownChecks) }
        val keep = (own + learned).map { it.id }.toSet()
        all.filterNot { it.id in keep }.takeIf { it.isNotEmpty() }?.let { runTraceRepository.delete(it) }
    }

    private companion object {
        const val MAX_TRACE_BYTES = 1024 * 1024
        const val OWN_TRACES_PER_TASK = 20
        const val MAX_REPORTS_PER_GOAL_RUN = 5
        val MAX_AGE = 7.days
        val CLAIMED = setOf(RunTrace.UploadState.Pending, RunTrace.UploadState.Reported)
    }

    /** The fields of a run trace emit: its name says own or learned, its MIME parameters say the rest. */
    private data class RunTraceEmit(
        val key: String,
        val outcome: String,
        val label: String,
        val notable: Boolean,
        val learned: Boolean,
    ) {
        companion object {
            // ponytail: parameters split on ';', so a label holding one is cut there; quote-aware parsing if it bites.
            fun parse(
                name: String,
                mimeType: String,
            ): RunTraceEmit? {
                val parameters = mimeType.split(';').drop(1).map { it.trim() }
                val values =
                    parameters
                        .filter { '=' in it }
                        .associate { it.substringBefore('=').trim().lowercase() to it.substringAfter('=').trim().removeSurrounding("\"") }
                return RunTraceEmit(
                    key = values["key"]?.takeIf { it.isNotEmpty() } ?: return null,
                    outcome = values["outcome"]?.takeIf { it.isNotEmpty() } ?: return null,
                    label = values["label"].orEmpty(),
                    notable = parameters.any { it.equals("notable", ignoreCase = true) },
                    learned = name.startsWith("learned-"),
                )
            }
        }
    }
}
