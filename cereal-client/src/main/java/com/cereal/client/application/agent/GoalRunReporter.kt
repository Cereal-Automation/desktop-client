package com.cereal.client.application.agent

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.telemetry.GoalRunFinishReport
import com.cereal.client.domain.model.telemetry.GoalRunOutcome
import com.cereal.client.domain.model.telemetry.GoalRunStartReport
import com.cereal.client.domain.provider.GoalRunReportProvider
import com.cereal.client.domain.repository.GoalSharingRepository
import com.cereal.client.domain.repository.LlmProviderConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Reports each AI browser agent task start and its finish as a **goal run report** (ADR 0011), when the user shares
 * their goals. Fire-and-forget: reports go out on [scope] and a failure is logged, never thrown at the caller.
 */
@OptIn(ExperimentalTime::class)
class GoalRunReporter(
    private val reportProvider: GoalRunReportProvider,
    private val goalSharingRepository: GoalSharingRepository,
    private val llmProviderConfigRepository: LlmProviderConfigRepository,
    private val applicationConfig: ApplicationConfig,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    private val logger = LoggerFactory.getLogger(GoalRunReporter::class.java)

    /** Started runs by task id. Not persisted: a run the app closed during never gets a finish report. */
    private val runs = ConcurrentHashMap<String, Run>()

    private class Run(
        val id: String,
        val startedAt: Instant,
        /** The start report; the finish waits for it so the server sees them in order. */
        val start: Job,
    )

    fun onStarted(task: Task) {
        runs.remove(task.id)
        if (!task.scriptInstance.packageInstance.definition.isAiBrowserAgent || !goalSharingRepository.isEnabled()) return
        val runId = UUID.randomUUID().toString()
        val startedAt = Clock.System.now()
        runs[task.id] = Run(runId, startedAt, launchReport { reportProvider.reportStart(startReport(task, runId, startedAt)) })
    }

    fun onFinished(
        task: Task,
        result: TaskStatus,
    ) {
        val run = runs.remove(task.id) ?: return
        // Checked again so turning sharing off stops reporting for runs already going.
        if (!goalSharingRepository.isEnabled()) return
        val outcome =
            when (result) {
                is TaskStatus.Success -> GoalRunOutcome.Succeeded
                is TaskStatus.Error -> GoalRunOutcome.Failed
                else -> GoalRunOutcome.Stopped
            }
        val cost =
            task
                .goalRunDetails()
                .cost
                ?.removePrefix("$")
                ?.toBigDecimalOrNull()
        val duration = (Clock.System.now() - run.startedAt).inWholeSeconds
        launchReport {
            run.start.join()
            reportProvider.reportFinish(GoalRunFinishReport(run.id, outcome, cost, duration))
        }
    }

    private suspend fun startReport(
        task: Task,
        runId: String,
        startedAt: Instant,
    ): GoalRunStartReport {
        val details = task.goalRunDetails()
        val packageInstance = task.scriptInstance.packageInstance
        val llm = llmProviderConfigRepository.get()
        // Only these fields leave the machine: never the rest of the task's config, which may hold secrets.
        return GoalRunStartReport(
            runId = runId,
            installId = goalSharingRepository.installId(),
            goal = scrub(details.goal),
            startUrl = scrub(details.startUrl.substringBefore('?').substringBefore('#')),
            runMode = details.mode,
            variantId = packageInstance.variantId,
            agentVersion = packageInstance.definition.manifest.versionCode,
            clientVersion = applicationConfig.versionName,
            llmProvider = llm?.provider?.name,
            llmModel = llm?.model,
            startedAt = startedAt,
        )
    }

    private fun launchReport(block: suspend () -> Unit): Job =
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("Dropping goal run report: ${e.message}")
            }
        }

    private companion object {
        // ponytail: regex masking is defence in depth, not a guarantee; the server scrubs again.
        // With or without a scheme: "nike.com/shop?size=44" counts too.
        val URL_USERINFO = Regex("""(?<=://)[^\s/@]+@""")
        val URL_QUERY = Regex("""\b((?:https?://)?(?:[\w-]+\.)+[a-z]{2,}(?:/[^\s?#]*)?)[?#]\S*""", RegexOption.IGNORE_CASE)
        val EMAIL = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")
        val CARD = Regex("""\b(?:\d[ -]?){12,18}\d\b""")
        val PHONE = Regex("""\+?\d[\d ()-]{7,}\d""")
        val THREE_DIGITS = Regex("""\d{3}""")
        const val MIN_PHONE_DIGITS = 9

        fun scrub(text: String): String =
            text
                .replace(URL_USERINFO, "")
                .replace(URL_QUERY, "$1")
                .replace(EMAIL, "[email]")
                .replace(CARD) { if (it.value.filter(Char::isDigit).passesLuhn()) "[card]" else it.value }
                // A digit count tells a phone number from a date like 2026-11-01, and a run of three digits from a
                // size list like "40 41 42 43 44".
                .replace(PHONE) {
                    if (it.value.count(Char::isDigit) >= MIN_PHONE_DIGITS && THREE_DIGITS.containsMatchIn(it.value)) "[phone]" else it.value
                }

        // Luhn's doubled digit, with the digits of the product summed: 5 → 10 → 1.
        const val LUHN_DOUBLED = "0246813579"
        const val LUHN_MODULUS = 10

        fun String.passesLuhn(): Boolean =
            reversed()
                .mapIndexed { i, c -> if (i % 2 == 1) LUHN_DOUBLED[c.digitToInt()].digitToInt() else c.digitToInt() }
                .sum() % LUHN_MODULUS == 0
    }
}
