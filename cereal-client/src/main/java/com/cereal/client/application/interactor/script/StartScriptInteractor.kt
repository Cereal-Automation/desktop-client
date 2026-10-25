package com.cereal.client.application.interactor.script

import com.cereal.client.application.Interactor
import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.exception.ClientUpdateRequiredException
import com.cereal.client.application.interactor.script.validator.ScriptConfigurationValuesValidator
import com.cereal.client.application.script.ScriptInstanceFactory
import com.cereal.client.application.script.TaskSetupLimits
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.notification.ScriptNotificationOverrides
import com.cereal.client.domain.model.script.ScriptConfigurationItemDefinitionConflict
import com.cereal.client.domain.model.script.ScriptConfigurationValues
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
    private val taskSetupLimits: TaskSetupLimits,
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
                params.variantId,
            )
        configValuesValidator.validate()

        taskSetupLimits.checkEntitlement(params.scriptPackage, params.mainScriptConfiguration, params.variantId)
        if (!params.ignoreConcurrencyConflicts) {
            taskSetupLimits.checkConcurrency(
                params.scriptPackage,
                params.mainScriptConfiguration,
                params.childConfigurations,
                params.numberOfConcurrentTasks,
                params.variantId,
            )
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
                params.variantId,
            )

        val scriptInstance =
            scriptInstanceFactory.createMainScriptInstance(
                UUID.randomUUID().toString(),
                scriptPackageInstance.entryScript,
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

    data class Params(
        val groupId: String,
        val scriptPackage: ScriptPackage,
        val mainScriptConfiguration: ScriptConfigurationValues,
        val childConfigurations: Map<String, ScriptConfigurationValues>,
        val numberOfConcurrentTasks: Int,
        val ignoreConcurrencyConflicts: Boolean,
        val notificationOverrides: ScriptNotificationOverrides? = null,
        /** The [ScriptPackage.variants] id to start; null starts the default entry. */
        val variantId: String? = null,
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
