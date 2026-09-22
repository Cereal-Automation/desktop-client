package com.cereal.client.application.interactor.script

import com.cereal.client.application.Interactor
import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.script.ScriptInstanceFactory
import com.cereal.client.application.script.hasValidConfiguration
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.exception.InvalidScriptConfigurationException
import com.cereal.client.domain.model.script.ChildScriptInstance
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.getScriptPackageInstance
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.sdk.Script
import com.cereal.sdk.component.script.ScriptParameters
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class StartChildScriptInteractor(
    private val scriptInstanceRepository: ScriptInstanceRepository,
    private val taskManager: TaskManager,
    private val scriptInstanceFactory: ScriptInstanceFactory,
) : Interactor<ChildScriptInstance, StartChildScriptInteractor.Params>() {
    @OptIn(ExperimentalTime::class)
    override suspend fun run(params: Params): ChildScriptInstance {
        val scriptPackageInstance = params.launchedBy.getScriptPackageInstance()
        val childScriptEntry =
            scriptPackageInstance.definition.childScripts.firstNotNullOfOrNull {
                if (it.value.clazz == params.scriptCls) it else null
            }
                ?: throw ScriptNotFoundException()

        val scriptConfigurationValues =
            scriptPackageInstance.childConfigurations.get(key = childScriptEntry.key)
                ?: throw ScriptConfigurationNotFoundException()

        val scriptInstance =
            scriptInstanceFactory.createChildScriptInstance(
                UUID.randomUUID().toString(),
                childScriptEntry.value,
                scriptConfigurationValues,
                Clock.System
                    .now(),
                scriptPackageInstance,
                params.launchedBy,
                params.parameters,
            )

        if (!scriptInstance.hasValidConfiguration()) {
            throw InvalidScriptConfigurationException()
        }

        scriptInstanceRepository.addChildScriptInstance(params.launchedBy, childScriptEntry.key, scriptInstance)

        taskManager.createTasks(scriptInstance)
        taskManager.startTasksToConcurrencyLimit(scriptInstance)

        return scriptInstance
    }

    data class Params(
        val launchedBy: ScriptInstance,
        val scriptCls: KClass<out Script<*>>,
        val parameters: ScriptParameters?,
    )
}

class ScriptNotFoundException : CerealException("This script couldn't start because part of it is missing. Please reinstall it from the marketplace.")

class ScriptConfigurationNotFoundException : CerealException("This script couldn't start because part of its setup is missing. Please reinstall it from the marketplace.")
