package com.cereal.client.application.agent

import com.cereal.client.domain.model.diagnostics.FailureReport
import com.cereal.client.domain.model.diagnostics.RunTrace
import com.cereal.client.domain.provider.FailureReportProvider
import com.cereal.client.domain.repository.DiagnosticsSharingRepository
import com.cereal.client.domain.repository.RunTraceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

/**
 * The client side of **failure reports** (spec #291, ADR 0012): takes in the run traces the AI browser agent emits,
 * keeps recent ones locally, lists them as Recent checks and uploads a failed check's report in the background. A
 * sibling of [GoalRunReporter], whose goal run id it stores with each trace. Never throws at the script: a trace it
 * can't keep is logged and dropped, and an upload never blocks or fails the task.
 */
@OptIn(ExperimentalTime::class)
class FailureReporter(
    private val runTraceRepository: RunTraceRepository,
    private val goalRunReporter: GoalRunReporter,
    private val diagnosticsSharingRepository: DiagnosticsSharingRepository,
    private val failureReportProvider: FailureReportProvider,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val clock: Clock = Clock.System,
) {
    private val logger = LoggerFactory.getLogger(FailureReporter::class.java)

    /** Intake, retention and settling an upload read, decide and write over all traces, so they run one at a time. */
    private val mutex = Mutex()

    /** Ids of traces being uploaded, so a tick and an intake never send the same report at once. */
    private val uploading = ConcurrentHashMap.newKeySet<String>()

    private var ticks: Job? = null

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
            val added =
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
                        !trace.learned && trace.failed && runId != null && diagnosticsSharingRepository.isEnabled() && !capReached(runId, kept)
                    (if (awaitsUpload) trace.copy(uploadState = RunTrace.UploadState.Pending) else trace).also {
                        runTraceRepository.add(it, bytes)
                        prune()
                    }
                }
            if (added.uploadState == RunTrace.UploadState.Pending) scope.launch { upload(added) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Dropping a run trace: ${e.message}")
        }
    }

    /**
     * [taskId]'s kept checks, newest first: its own traces only, never the learned ones. Whether Share diagnostics is on
     * is left to the caller, which hides the list while it's off; [flag] checks it again.
     */
    fun observeRecentChecks(taskId: String): Flow<List<RecentCheck>> =
        runTraceRepository.observe(taskId).map { traces ->
            traces
                .filterNot { it.learned }
                .sortedByDescending { it.receivedAt }
                .map { RecentCheck(it, it.canReport(traces)) }
        }

    /**
     * Reports the ok check [traceId] as a wrong result with the user's [note] (unmasked, at most [MAX_NOTE_LENGTH]
     * characters): it goes out as a `flagged` failure report through the same upload as a failed check. Does nothing
     * when the check can no longer be reported.
     */
    suspend fun flag(
        traceId: String,
        note: String?,
    ) {
        val flagged =
            mutex.withLock {
                val kept = runTraceRepository.getAll()
                val trace = kept.find { it.id == traceId }?.takeIf { diagnosticsSharingRepository.isEnabled() && it.canReport(kept) } ?: return
                trace
                    .copy(uploadState = RunTrace.UploadState.Pending, note = note?.trim()?.take(MAX_NOTE_LENGTH)?.ifEmpty { null })
                    .also { runTraceRepository.update(it) }
            }
        scope.launch { upload(flagged) }
    }

    /**
     * Applies retention and resumes uploads waiting to retry: on app start and hourly. While Share diagnostics is off it
     * releases them instead, as [releasePendingUploads] does.
     */
    suspend fun resumePendingUploads() {
        val pending =
            try {
                mutex.withLock {
                    if (!diagnosticsSharingRepository.isEnabled()) releasePending()
                    prune()
                    runTraceRepository.getAll().filter { it.uploadState == RunTrace.UploadState.Pending }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return logger.warn("Couldn't resume failure report uploads: ${e.message}")
            }
        pending.forEach { upload(it) }
    }

    /** For Share diagnostics turned off: takes every pending trace's upload claim away for good. */
    suspend fun releasePendingUploads() = mutex.withLock { releasePending() }

    /** Runs [resumePendingUploads] whenever a user signs in, then hourly while they stay signed in. */
    fun start(users: Flow<Any?>) {
        if (ticks?.isActive == true) return
        ticks =
            scope.launch {
                users.collectLatest { user ->
                    while (user != null) {
                        resumePendingUploads()
                        delay(TICK_INTERVAL)
                    }
                }
            }
    }

    /** Removes every trace of [taskId], for a task being deleted. */
    suspend fun forgetTask(taskId: String) = mutex.withLock { runTraceRepository.deleteForTask(taskId) }

    /**
     * Uploads [trace]'s failure report, with the learned check's trace when one is kept: a failed check under its
     * outcome tag, an ok one as `flagged` with its note. Then settles the row by the result: reported (bytes deleted),
     * dropped, still pending to retry, or back to the ordinary keep when Share diagnostics went off meanwhile.
     */
    private suspend fun upload(trace: RunTrace) {
        val runId = trace.runId ?: return
        if (!uploading.add(trace.id)) return
        try {
            goalRunReporter.awaitStartReport(runId)
            val learned = runTraceRepository.getAll().find { it.learned && it.check() == trace.check() }
            val bytes = runTraceRepository.readBytes(trace) ?: return
            val report =
                FailureReport(
                    runId = runId,
                    key = trace.key,
                    outcome = if (trace.failed) trace.outcome else FailureReport.OUTCOME_FLAGGED,
                    note = trace.note,
                    trace = bytes,
                    learnedTrace = learned?.let { runTraceRepository.readBytes(it) },
                )
            val result = failureReportProvider.upload(report, diagnosticsSharingRepository::isEnabled)
            mutex.withLock {
                val current = runTraceRepository.getAll().find { it.id == trace.id } ?: return
                when {
                    // Stands even when sharing went off meanwhile: an upload already in flight may finish.
                    result == FailureReportProvider.Result.Registered -> {
                        runTraceRepository.update(current.copy(uploadState = RunTrace.UploadState.Reported))
                        runTraceRepository.deleteBytes(current)
                        learned?.let { runTraceRepository.delete(listOf(it)) }
                    }

                    current.uploadState == RunTrace.UploadState.Pending && result == FailureReportProvider.Result.Drop -> {
                        runTraceRepository.update(current.copy(uploadState = RunTrace.UploadState.Dropped))
                    }

                    current.uploadState == RunTrace.UploadState.Pending && !diagnosticsSharingRepository.isEnabled() -> {
                        runTraceRepository.update(current.copy(uploadState = RunTrace.UploadState.None))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failure report ${trace.key} stays pending: ${e.message}")
        } finally {
            uploading.remove(trace.id)
        }
    }

    /** Pending traces lose their upload claim and fall back to the ordinary keep; turning sharing on won't revive them. */
    private suspend fun releasePending() =
        runTraceRepository
            .getAll()
            .filter { it.uploadState == RunTrace.UploadState.Pending }
            .forEach { runTraceRepository.update(it.copy(uploadState = RunTrace.UploadState.None)) }

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
                    // A reported row holds no bytes; it stays so the goal run's cap still counts it.
                    traces.filter { it in latest || it.notable || it.uploadState in CLAIMED }
                }

        // A learned trace lives as long as its check's own trace, which arrives just after it.
        val ownChecks = all.filterNot { it.learned }.map { it.check() }.toSet()
        val keptChecks = own.map { it.check() }.toSet()
        val learned = fresh.filter { it.learned && (it.check() in keptChecks || it.check() !in ownChecks) }
        val keep = (own + learned).map { it.id }.toSet()
        all.filterNot { it.id in keep }.takeIf { it.isNotEmpty() }?.let { runTraceRepository.delete(it) }
    }

    /** Whether "Report wrong result" shows for this own trace: kept, ok, not yet flagged, its goal run under the cap. */
    private fun RunTrace.canReport(kept: List<RunTrace>): Boolean = !learned && !failed && uploadState == RunTrace.UploadState.None && runId != null && !capReached(runId, kept)

    /** The goal run already has its 5 reports, failed and flagged counted together. */
    private fun capReached(
        runId: String,
        kept: List<RunTrace>,
    ) = kept.count { it.runId == runId && it.uploadState in CLAIMED } >= MAX_REPORTS_PER_GOAL_RUN

    /** The check a trace belongs to: a learned trace shares it with the check's own trace. */
    private fun RunTrace.check() = Triple(taskId, runId, key)

    companion object {
        /** The longest note a user can add when reporting a wrong result. */
        const val MAX_NOTE_LENGTH = 500
        private const val MAX_TRACE_BYTES = 1024 * 1024
        private const val OWN_TRACES_PER_TASK = 20
        private const val MAX_REPORTS_PER_GOAL_RUN = 5
        private val MAX_AGE = 7.days
        private val TICK_INTERVAL = 1.hours
        private val CLAIMED = setOf(RunTrace.UploadState.Pending, RunTrace.UploadState.Reported)
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
