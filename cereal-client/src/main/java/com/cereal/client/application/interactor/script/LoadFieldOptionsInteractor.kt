package com.cereal.client.application.interactor.script

import com.cereal.client.application.Interactor
import com.cereal.client.application.exception.FieldOptionsTimeoutException
import com.cereal.client.application.exception.ScriptCallException
import com.cereal.client.application.task.SCRIPT_CALL_TIMEOUT
import com.cereal.sdk.FieldOption
import com.cereal.sdk.FieldOptions
import com.cereal.sdk.statemodifier.ScriptConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Runs a field's options `load` in script code, bounded by [SCRIPT_CALL_TIMEOUT]; a script failure keeps its message. */
class LoadFieldOptionsInteractor : Interactor<List<FieldOption>, LoadFieldOptionsInteractor.Params>() {
    override suspend fun run(params: Params): List<FieldOption> =
        try {
            withTimeout(SCRIPT_CALL_TIMEOUT) { params.fieldOptions.load(params.config) }
        } catch (e: TimeoutCancellationException) {
            throw FieldOptionsTimeoutException(SCRIPT_CALL_TIMEOUT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ScriptCallException(e)
        }

    data class Params(
        val fieldOptions: FieldOptions,
        val config: ScriptConfig,
    )
}
