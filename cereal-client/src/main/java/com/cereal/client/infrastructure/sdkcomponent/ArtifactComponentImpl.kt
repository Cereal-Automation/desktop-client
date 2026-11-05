package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.application.agent.FailureReporter
import com.cereal.client.domain.model.diagnostics.isRunTraceMimeType
import com.cereal.client.domain.repository.ArtifactRepository
import com.cereal.sdk.component.artifact.ArtifactComponent

class ArtifactComponentImpl(
    private val artifactRepository: ArtifactRepository,
    private val failureReporter: FailureReporter,
    private val taskId: String,
) : ArtifactComponent {
    override suspend fun emit(
        name: String,
        bytes: ByteArray,
        mimeType: String?,
    ) {
        // A run trace is diagnostics, never an artifact the user sees.
        if (isRunTraceMimeType(mimeType)) {
            failureReporter.receive(taskId, name, mimeType, bytes)
        } else {
            artifactRepository.emit(taskId, name, bytes, mimeType)
        }
    }
}
