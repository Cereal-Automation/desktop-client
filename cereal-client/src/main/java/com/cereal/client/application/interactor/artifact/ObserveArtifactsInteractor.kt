package com.cereal.client.application.interactor.artifact

import com.cereal.client.application.FlowInteractor
import com.cereal.client.domain.model.artifact.Artifact
import com.cereal.client.domain.repository.ArtifactRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@OptIn(FlowPreview::class)
class ObserveArtifactsInteractor(
    private val artifactRepository: ArtifactRepository,
) : FlowInteractor<List<Artifact>, ObserveArtifactsInteractor.Params>() {
    override suspend fun run(params: Params): Flow<List<Artifact>> = artifactRepository.observeArtifacts(params.taskId)

    data class Params(
        val taskId: String,
    )
}
