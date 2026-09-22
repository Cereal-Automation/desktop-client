package com.cereal.client.domain.model.artifact

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A named blob of binary output a script produced during a run, persisted against the task that emitted it and
 * downloadable by the user afterwards.
 *
 * Carries metadata only — the bytes live encrypted on disk and are loaded on demand (see
 * [com.cereal.client.domain.repository.ArtifactRepository.writeToFile]). Distinct from a dataset (user-managed
 * input) and from a script's execution result (the run's outcome).
 */
data class Artifact
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: String,
        val taskId: String,
        val name: String,
        val mimeType: String?,
        val sizeBytes: Long,
        val createdAt: Instant,
    )
