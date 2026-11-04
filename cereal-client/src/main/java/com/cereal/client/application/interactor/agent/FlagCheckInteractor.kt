package com.cereal.client.application.interactor.agent

import com.cereal.client.application.Interactor
import com.cereal.client.application.agent.FailureReporter

/** Reports an ok check from Recent checks as a wrong result, with the user's optional note. */
class FlagCheckInteractor(
    private val failureReporter: FailureReporter,
) : Interactor<Unit, FlagCheckInteractor.Params>() {
    override suspend fun run(params: Params) = failureReporter.flag(params.traceId, params.note)

    data class Params(
        val traceId: String,
        val note: String?,
    )
}
