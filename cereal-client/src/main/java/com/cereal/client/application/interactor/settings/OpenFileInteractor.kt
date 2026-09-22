package com.cereal.client.application.interactor.settings

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.OpenFileResult
import com.cereal.client.domain.provider.SystemProvider
import java.io.File

class OpenFileInteractor(
    private val systemRepository: SystemProvider,
) : Interactor<OpenFileResult, OpenFileInteractor.Params>() {
    override suspend fun run(params: Params): OpenFileResult = systemRepository.open(params.file)

    data class Params(
        val file: File,
    )
}
