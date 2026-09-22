package com.cereal.client.infrastructure.provider.inmemory

import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.provider.ScriptInstallProvider

/**
 * Hermetic [ScriptInstallProvider] for UI tests and the sandboxed (`mock`) flavor. Marketplace
 * downloads are unavailable here: [updateScript] is a no-op that echoes the package back, and
 * [installScript] is unsupported.
 */
class InMemoryScriptInstallProvider : ScriptInstallProvider {
    override suspend fun updateScript(
        scriptPackage: ScriptPackage,
        release: Release,
    ): ScriptPackage = scriptPackage

    override suspend fun installScript(
        packageName: String,
        release: Release,
    ): ScriptPackage = throw UnsupportedOperationException("Script installation is not available in the mock flavor.")
}
