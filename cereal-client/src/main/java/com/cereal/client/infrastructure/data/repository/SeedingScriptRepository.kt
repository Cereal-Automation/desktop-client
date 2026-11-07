package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * [ScriptRepository] for the sandboxed `mock` flavor: the real [delegate] (normally
 * [ScriptRepositoryImpl]), preceded by seeding the script JARs bundled into the app (via
 * [SandboxScriptSeeder]) so a runnable script is present out-of-the-box. Seeding is idempotent and
 * must run before the delegate first scans the scripts directory.
 */
class SeedingScriptRepository(
    private val delegate: ScriptRepository,
    private val userSession: UserSession,
    private val sandboxScriptSeeder: SandboxScriptSeeder,
) : ScriptRepository by delegate {
    override suspend fun getInstalledScripts(): Flow<List<ScriptPackage>> {
        seed()
        return delegate.getInstalledScripts()
    }

    override suspend fun getScript(packageName: String): ScriptPackage? {
        seed()
        return delegate.getScript(packageName)
    }

    private suspend fun seed() {
        val user = userSession.requireUser()
        withContext(Dispatchers.IO) { sandboxScriptSeeder.seedFor(user) }
    }
}
