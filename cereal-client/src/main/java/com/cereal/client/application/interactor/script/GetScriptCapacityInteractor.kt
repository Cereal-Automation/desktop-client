package com.cereal.client.application.interactor.script

import com.cereal.client.application.Interactor
import com.cereal.client.application.script.ScriptLicenseChecker
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptPackage

/**
 * The entitled [ScriptCapacity] for an installed [ScriptPackage] at script-start time — the single
 * read point the start/config display and start-time soft-enforcement share.
 *
 * Reads the cached subscription/team entitlement via [ScriptLicenseChecker] (no fresh network call)
 * and returns [ScriptCapacity.None] when the script is unlicensed, unmatched, or carries no capacity,
 * honouring the contract that capacity is only meaningful when licensed.
 */
class GetScriptCapacityInteractor(
    private val scriptLicenseChecker: ScriptLicenseChecker,
) : Interactor<ScriptCapacity, GetScriptCapacityInteractor.Params>() {
    override suspend fun run(params: Params): ScriptCapacity = scriptLicenseChecker.getEntitlement(params.scriptPackage)?.capacity ?: ScriptCapacity.None

    data class Params(
        val scriptPackage: ScriptPackage,
    )
}
