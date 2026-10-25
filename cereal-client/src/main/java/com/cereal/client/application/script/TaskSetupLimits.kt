package com.cereal.client.application.script

import com.cereal.client.application.interactor.script.CapacityExceededException
import com.cereal.client.application.interactor.script.NumberOfConcurrentTasksConflictException
import com.cereal.client.application.interactor.script.UnlicensedException
import com.cereal.client.application.interactor.script.validator.CapacityValidator
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptConcurrencyValidator
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptPackage

/** The limits a task setup is held to when it is created, when it is edited and when its concurrent-task count changes. */
class TaskSetupLimits(
    private val scriptLicenseChecker: ScriptLicenseChecker,
) {
    /**
     * Refuses an unlicensed script ([UnlicensedException]) and, soft-enforcing the entitlement's record capacity, a
     * run whose dataset-record count exceeds a [ScriptCapacity.Limited] cap ([CapacityExceededException]). The
     * allow/deny decision is the domain's ([com.cereal.client.domain.model.script.ScriptEntitlement.allows]).
     * Honor-system and local: [ScriptCapacity.Unlimited] and [ScriptCapacity.None] never block.
     */
    suspend fun checkEntitlement(
        scriptPackage: ScriptPackage,
        mainConfiguration: ScriptConfigurationValues,
        variantId: String? = null,
    ) {
        val entitlement = scriptLicenseChecker.getEntitlement(scriptPackage) ?: throw UnlicensedException()
        val limited = entitlement.capacity as? ScriptCapacity.Limited ?: return
        val recordCount = CapacityValidator(scriptPackage, mainConfiguration, variantId).runRecordCount()
        if (!entitlement.allows(recordCount)) {
            throw CapacityExceededException(recordCount, limited.records, limited.unit)
        }
    }

    /**
     * Throws a [NumberOfConcurrentTasksConflictException] when [numberOfConcurrentTasks] exceeds the records of any of
     * the configured groups; the user may accept the conflict and go ahead.
     */
    fun checkConcurrency(
        scriptPackage: ScriptPackage,
        mainConfiguration: ScriptConfigurationValues,
        childConfigurations: Map<String, ScriptConfigurationValues>,
        numberOfConcurrentTasks: Int,
        variantId: String? = null,
    ) {
        val validator = ScriptConcurrencyValidator(scriptPackage, mainConfiguration, childConfigurations, numberOfConcurrentTasks, variantId)
        val numberOfConcurrentTasksLimited = validator.validateTaskLimits()
        val recordsUsedByMultipleTasks = validator.validateRecordsReused()
        if (numberOfConcurrentTasksLimited != null || recordsUsedByMultipleTasks.isNotEmpty()) {
            throw NumberOfConcurrentTasksConflictException(
                numberOfConcurrentTasks,
                recordsUsedByMultipleTasks,
                numberOfConcurrentTasksLimited,
            )
        }
    }
}
