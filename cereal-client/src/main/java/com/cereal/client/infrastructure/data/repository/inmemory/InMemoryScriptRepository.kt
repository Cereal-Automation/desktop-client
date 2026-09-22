package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.repository.ScriptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hermetic [ScriptRepository] for UI tests and the sandboxed (`mock`) flavor. Reads no JARs from
 * disk. Starts with no installed scripts; seed via [seed].
 */
class InMemoryScriptRepository : ScriptRepository {
    private val installed = MutableStateFlow<List<ScriptPackage>>(emptyList())

    fun seed(scripts: List<ScriptPackage>) {
        installed.value = scripts
    }

    override suspend fun getInstalledScripts(): Flow<List<ScriptPackage>> = installed

    override suspend fun getScript(packageName: String): ScriptPackage? = installed.value.firstOrNull { it.manifest.packageName == packageName }

    override suspend fun removeScript(scriptPackage: ScriptPackage) {
        installed.value = installed.value.filterNot { it.manifest.packageName == scriptPackage.manifest.packageName }
    }
}
