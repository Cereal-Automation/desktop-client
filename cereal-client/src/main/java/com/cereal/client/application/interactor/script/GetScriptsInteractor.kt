package com.cereal.client.application.interactor.script

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.repository.ScriptRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@OptIn(FlowPreview::class)
class GetScriptsInteractor(
    private val scriptRepository: ScriptRepository,
) : FlowInteractor<List<ScriptPackage>, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Flow<List<ScriptPackage>> = scriptRepository.getInstalledScripts()
}
