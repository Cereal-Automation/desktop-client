package com.cereal.client.domain.provider

import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.model.script.ScriptPackage

/**
 * Installs and updates scripts by downloading releases from the marketplace and writing them to
 * disk. This is a *provider* (an outbound integration with the marketplace download API), distinct
 * from [com.cereal.client.domain.repository.ScriptRepository], which owns the locally installed
 * scripts (listing, reading and removing them).
 */
interface ScriptInstallProvider {
    suspend fun updateScript(
        scriptPackage: ScriptPackage,
        release: Release,
    ): ScriptPackage

    suspend fun installScript(
        packageName: String,
        release: Release,
    ): ScriptPackage
}
