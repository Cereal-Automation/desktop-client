package com.cereal.client.application.interactor.agent

import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.marketplace.InstallMarketplaceScriptInteractor
import com.cereal.client.domain.model.script.ScriptPackage

/** Installs the AI browser agent through the normal marketplace install. */
class InstallAiBrowserAgentInteractor(
    private val getAiBrowserAgentListingInteractor: GetAiBrowserAgentListingInteractor,
    private val installMarketplaceScriptInteractor: InstallMarketplaceScriptInteractor,
) : Interactor<ScriptPackage, Interactor.None>() {
    override suspend fun run(params: None): ScriptPackage {
        val listing = getAiBrowserAgentListingInteractor.run(None())
        return installMarketplaceScriptInteractor.run(InstallMarketplaceScriptInteractor.Params(listing))
    }
}
