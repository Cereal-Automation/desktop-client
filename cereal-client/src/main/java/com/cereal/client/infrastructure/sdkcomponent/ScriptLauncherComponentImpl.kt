package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.application.interactor.script.StartChildScriptInteractor
import com.cereal.client.domain.model.script.ChildScriptInstance
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.sdk.Script
import com.cereal.sdk.component.script.ScriptHandle
import com.cereal.sdk.component.script.ScriptLauncherComponent
import com.cereal.sdk.component.script.ScriptParameters
import com.cereal.sdk.component.script.StartScriptException

class ScriptLauncherComponentImpl(
    private val startChildScriptInteractor: StartChildScriptInteractor,
    private val scriptInstance: ScriptInstance,
    private val tasksRepository: TasksRepository,
) : ScriptLauncherComponent {
    // StartScriptException only carries a message; the cause cannot be chained so the message is surfaced instead.
    @Suppress("SwallowedException")
    override suspend fun start(
        scriptCls: Class<out Script<*>>,
        parameters: ScriptParameters?,
    ): ScriptHandle =
        try {
            val scriptInstance =
                startChildScriptInteractor.run(
                    StartChildScriptInteractor.Params(
                        scriptInstance,
                        scriptCls.kotlin,
                        parameters,
                    ),
                )
            ScriptInstanceHandle(tasksRepository, scriptInstance)
        } catch (e: Exception) {
            throw StartScriptException(e.message)
        }
}

class ScriptInstanceHandle(
    private val tasksRepository: TasksRepository,
    private val scriptInstance: ChildScriptInstance,
) : ScriptHandle {
    override suspend fun getStatus(): ScriptHandle.Status {
        val tasks = tasksRepository.getTasks(scriptInstance)

        var runningTasks = 0
        var erroredTasks = 0
        var succeededTasks = 0

        tasks.forEach {
            when (it.status) {
                is TaskStatus.Success -> succeededTasks++
                is TaskStatus.Error -> erroredTasks++
                else -> runningTasks++
            }
        }

        return ScriptHandle.Status(tasks.size, runningTasks, succeededTasks, erroredTasks)
    }
}
