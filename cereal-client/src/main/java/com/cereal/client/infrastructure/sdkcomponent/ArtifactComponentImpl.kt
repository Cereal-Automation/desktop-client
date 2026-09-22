package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.domain.repository.ArtifactRepository
import com.cereal.sdk.component.artifact.ArtifactComponent

class ArtifactComponentImpl(
    private val artifactRepository: ArtifactRepository,
    private val taskId: String,
) : ArtifactComponent {
    override suspend fun emit(
        name: String,
        bytes: ByteArray,
        mimeType: String?,
    ) {
        artifactRepository.emit(taskId, name, bytes, mimeType)
    }
}
