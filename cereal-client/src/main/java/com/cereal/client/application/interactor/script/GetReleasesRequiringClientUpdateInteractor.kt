package com.cereal.client.application.interactor.script

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.application.script.ScriptManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

/** Version code per package of a release rejected at update time because it needs a newer client. */
@OptIn(FlowPreview::class)
class GetReleasesRequiringClientUpdateInteractor(
    private val scriptManager: ScriptManager,
) : FlowInteractor<Map<String, Long>, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Flow<Map<String, Long>> = scriptManager.releasesRequiringClientUpdate
}
