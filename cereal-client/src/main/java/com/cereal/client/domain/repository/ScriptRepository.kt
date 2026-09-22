package com.cereal.client.domain.repository

import com.cereal.client.domain.model.script.ScriptPackage
import kotlinx.coroutines.flow.Flow

/**
 * Owns the scripts installed locally on disk: listing, reading and removing them. Downloading and
 * installing/updating scripts from the marketplace is a provider concern — see
 * [com.cereal.client.domain.provider.ScriptInstallProvider].
 */
interface ScriptRepository {
    suspend fun getInstalledScripts(): Flow<List<ScriptPackage>>

    suspend fun getScript(packageName: String): ScriptPackage?

    suspend fun removeScript(scriptPackage: ScriptPackage)
}
