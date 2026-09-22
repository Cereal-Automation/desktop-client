package com.cereal.client.application.interactor.artifact

import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.ArtifactRepository
import java.io.File

class SaveArtifactToFileInteractor(
    private val artifactRepository: ArtifactRepository,
) : Interactor<Unit, SaveArtifactToFileInteractor.Params>() {
    override suspend fun run(params: Params) {
        artifactRepository.writeToFile(params.artifactId, params.file)
    }

    data class Params(
        val artifactId: String,
        val file: File,
    )
}
