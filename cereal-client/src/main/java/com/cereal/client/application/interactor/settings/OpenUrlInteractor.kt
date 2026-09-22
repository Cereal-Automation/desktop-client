package com.cereal.client.application.interactor.settings

import com.cereal.client.application.Interactor
import com.cereal.client.domain.provider.SystemProvider

class OpenUrlInteractor(
    private val systemRepository: SystemProvider,
) : Interactor<Unit, OpenUrlInteractor.Params>() {
    override suspend fun run(params: Params) {
        systemRepository.browser(params.url)
    }

    data class Params(
        val url: String,
    )
}
