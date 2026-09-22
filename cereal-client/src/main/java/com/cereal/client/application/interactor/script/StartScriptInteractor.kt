package com.cereal.client.application.interactor.script

import com.cereal.client.application.Interactor
import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.exception.ClientUpdateRequiredException
import com.cereal.client.application.interactor.script.validator.CapacityValidator
import com.cereal.client.application.interactor.script.validator.ScriptConcurrencyValidator
import com.cereal.client.application.interactor.script.validator.ScriptConfigurationItemDefinitionConflict
import com.cereal.client.application.interactor.script.validator.ScriptConfigurationValuesValidator
import com.cereal.client.application.script.ScriptInstanceFactory
import com.cereal.client.application.script.ScriptLicenseChecker
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.notification.ScriptNotificationOverrides
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.isClientUpdateRequired
import com.cereal.client.domain.repository.ApplicationRepository
import com.cereal.client.domain.repository.ScriptInstanceRepository
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class StartScriptInteractor(
    private val scriptInstanceRepository: ScriptInstanceRepository,
    private val taskManager: TaskManager,
    private val scriptLicenseChecker: ScriptLicenseChecker,
    private val applicationRepository: ApplicationRepository,
    private val scriptInstanceFactory: ScriptInstanceFactory,
) : Interactor<ScriptPackageInstance, StartScriptInteractor.Params>() {
    @OptIn(ExperimentalTime::class)
    override suspend fun run(params: Params): ScriptPackageInstance {
        val installedVersion = applicationRepository.getSdkVersion()
        if (params.scriptPackage.isClientUpdateRequired(installedVersion)) {
            val sdkVersionStr = params.scriptPackage.manifest.sdkVersion!!
            throw ClientUpdateRequiredException(sdkVersionStr)
        }

        val configValuesValidator =
            ScriptConfigurationValuesValidator(
                params.scriptPackage,
                params.mainScriptConfiguration,
                params.childConfigurations,
            )
        configValuesValidator.validate()

        val entitlement = scriptLicenseChecker.getEntitlement(params.scriptPackage) ?: throw UnlicensedException()

        checkCapacity(params, entitlement)

        if (!params.ignoreConcurrencyConflicts) {
            checkNumberOfConcurrentTasksConflicts(params)
        }

        val scriptPackageInstance =
            ScriptPackageInstance(
                UUID.randomUUID().toString(),
                params.mainScriptConfiguration,
                params.childConfigurations,
                params.scriptPackage,
                Clock.System.now(),
                params.numberOfConcurrentTasks,
                params.notificationOverrides,
            )

        val scriptInstance =
            scriptInstanceFactory.createMainScriptInstance(
                UUID.randomUUID().toString(),
                params.scriptPackage.mainScript,
                params.mainScriptConfiguration,
                Clock.System.now(),
                scriptPackageInstance,
            )

        scriptInstanceRepository.addScriptPackageInstance(
            params.groupId,
            scriptPackageInstance,
            scriptInstance,
        )
        taskManager.createTasks(scriptInstance)

        return scriptPackageInstance
    }

    /**
     * Soft-enforces the [entitlement]'s record capacity: refuses to start (throws
     * [CapacityExceededException]) when the run's dataset-record count exceeds a
     * [ScriptCapacity.Limited] cap. The allow/deny decision is delegated to
     * [ScriptEntitlement.allows] so the rule lives in the domain. Honor-system and local —
     * [ScriptCapacity.Unlimited] and [ScriptCapacity.None] never block, and nothing is reported upstream.
     */
    private fun checkCapacity(
        params: Params,
        entitlement: ScriptEntitlement,
    ) {
        val limited = entitlement.capacity as? ScriptCapacity.Limited ?: return

        val recordCount =
            CapacityValidator(
                params.scriptPackage,
                params.mainScriptConfiguration,
            ).runRecordCount()

        if (!entitlement.allows(recordCount)) {
            throw CapacityExceededException(recordCount, limited.records, limited.unit)
        }
    }

    /**
     * Checks if any of the number of concurrent tasks exceed the number of items in any of the task configurations
     * and throws a [NumberOfConcurrentTasksConflictException] when that's the case.
     */
    private fun checkNumberOfConcurrentTasksConflicts(params: Params) {
        val validator =
            ScriptConcurrencyValidator(
                params.scriptPackage,
                params.mainScriptConfiguration,
                params.childConfigurations,
                params.numberOfConcurrentTasks,
            )
        val numberOfConcurrentTasksLimited = validator.validateTaskLimits()
        val recordsUsedByMultipleTasks = validator.validateRecordsReused()

        if (numberOfConcurrentTasksLimited != null || recordsUsedByMultipleTasks.isNotEmpty()) {
            throw NumberOfConcurrentTasksConflictException(
                params.numberOfConcurrentTasks,
                recordsUsedByMultipleTasks,
                numberOfConcurrentTasksLimited,
            )
        }
    }

    data class Params(
        val groupId: String,
        val scriptPackage: ScriptPackage,
        val mainScriptConfiguration: ScriptConfigurationValues,
        val childConfigurations: Map<String, ScriptConfigurationValues>,
        val numberOfConcurrentTasks: Int,
        val ignoreConcurrencyConflicts: Boolean,
        val notificationOverrides: ScriptNotificationOverrides? = null,
    )
}

class UnlicensedException : CerealException("Sorry, but you need a valid license to use this script. Please subscribe to gain access.")

/**
 * Thrown when a run's configured dataset-record count ([recordCount]) exceeds the entitled [capacity]
 * of [unit] records. Carries the numbers so the presentation layer can show a clear message; the
 * default failure path renders [message] via the `ErrorResolver`.
 */
class CapacityExceededException(
    val recordCount: Int,
    val capacity: Int,
    val unit: String,
) : CerealException(
        "This run uses $recordCount $unit, but your plan allows $capacity. Reduce the dataset to start the run.",
    )

class NumberOfConcurrentTasksConflictException(
    val numberOfConcurrentTasks: Int,
    /**
     * When not enough records are available in the group, it requires the reuse of these records.
     */
    val recordsUsedByMultipleTasks: List<ScriptConfigurationItemDefinitionConflict>,
    /**
     * When the numberOfConcurrentTasks is changed to a lower value because of limited task data available.
     */
    val numberOfConcurrentTasksLimited: ScriptConfigurationItemDefinitionConflict?,
) : CerealException("There are too many concurrent tasks for the available data.")
