package com.cereal.client.application.interactor.marketplace

import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.ScriptRepository

class IsScriptInstalledInteractor(
    private val scriptRepository: ScriptRepository,
) : Interactor<Boolean, IsScriptInstalledInteractor.Params>() {
    override suspend fun run(params: Params): Boolean = scriptRepository.getScript(params.publicIdentifier) != null

    data class Params(
        val publicIdentifier: String,
    )
}
