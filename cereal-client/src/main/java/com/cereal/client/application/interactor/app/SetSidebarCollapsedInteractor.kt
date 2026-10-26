package com.cereal.client.application.interactor.app

import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.ApplicationPreferenceRepository

class SetSidebarCollapsedInteractor(
    private val applicationPreferenceRepository: ApplicationPreferenceRepository,
) : Interactor<Unit, SetSidebarCollapsedInteractor.Params>() {
    override suspend fun run(params: Params) {
        applicationPreferenceRepository.setSidebarCollapsed(params.collapsed)
    }

    data class Params(
        val collapsed: Boolean,
    )
}
