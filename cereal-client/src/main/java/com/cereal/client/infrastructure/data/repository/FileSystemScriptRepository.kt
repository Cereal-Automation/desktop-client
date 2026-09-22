package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemScriptsDataSource
import com.cereal.client.infrastructure.data.datasource.filesystem.toScriptPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Script repository used by the sandboxed `mock` flavor. Reads real script JARs from the filesystem
 * (via [FileSystemScriptsDataSource]) so that scripts placed on disk are visible in the UI during
 * local development. Installation and updates are unsupported because they require the marketplace
 * download API.
 *
 * Before reading, it seeds the sample script JAR bundled into the app (via [SandboxScriptSeeder])
 * into the user's script directory, so a runnable script is present out-of-the-box. Seeding is
 * idempotent and must run before [FileSystemScriptsDataSource] first scans the directory, hence the
 * call at the start of [getInstalledScripts] / [getScript].
 *
 * Tests use the hermetic
 * [com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository] instead; this
 * implementation is layered over `InMemoryRepositoryModule` only for the sandbox flavor (see
 * `SandboxRepositoryModule`).
 */
class FileSystemScriptRepository(
    private val fileSystemScriptsDataSource: FileSystemScriptsDataSource,
    private val userSession: UserSession,
    private val sandboxScriptSeeder: SandboxScriptSeeder,
) : ScriptRepository {
    override suspend fun getInstalledScripts(): Flow<List<ScriptPackage>> {
        val user = userSession.requireUser()
        withContext(Dispatchers.IO) { sandboxScriptSeeder.seedFor(user) }
        return fileSystemScriptsDataSource.getScriptDefinitions(user).map { definitions ->
            definitions.map { it.toScriptPackage(supportUrl = null) }
        }
    }

    override suspend fun getScript(packageName: String): ScriptPackage? {
        val user = userSession.requireUser()
        withContext(Dispatchers.IO) { sandboxScriptSeeder.seedFor(user) }
        val definition = fileSystemScriptsDataSource.getScriptPackageDefinition(packageName, user) ?: return null
        return definition.toScriptPackage(supportUrl = null)
    }

    override suspend fun removeScript(scriptPackage: ScriptPackage) {
        fileSystemScriptsDataSource.deleteScript(scriptPackage, userSession.requireUser())
    }
}
