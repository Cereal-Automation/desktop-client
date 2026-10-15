package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.task.SCRIPT_CALL_TIMEOUT
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.script.ScriptPackageInstance
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class RunTaskActionInteractor(
    private val taskManager: TaskManager,
) : Interactor<RunTaskActionInteractor.Result, RunTaskActionInteractor.Params>() {
    override suspend fun run(params: Params): Result =
        try {
            Result(withTimeout(SCRIPT_CALL_TIMEOUT) { taskManager.runTaskAction(params.scriptPackageInstance, params.index) })
        } catch (e: TimeoutCancellationException) {
            throw CerealException("The action did not finish within ${SCRIPT_CALL_TIMEOUT.inWholeSeconds} seconds.", e)
        }

    data class Params(
        val scriptPackageInstance: ScriptPackageInstance,
        val index: Int,
    )

    /** [message] is what the action returned; null means it had nothing to report. */
    data class Result(
        val message: String?,
    )
}
