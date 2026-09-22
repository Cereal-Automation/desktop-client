package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.auth.OAuthProvider
import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.domain.model.user.User
import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.domain.repository.ScriptRepository
import kotlinx.coroutines.flow.first

/**
 * Auth provider for the sandboxed `mock` flavor. Authentication always succeeds with a fixed mock
 * user.
 *
 * Subscriptions are derived from whatever scripts are actually installed (the bundled sample plus
 * any local JARs seeded by `SandboxScriptSeeder`). `ScriptSyncManager` deletes installed scripts
 * that aren't subscribed, so reporting every installed script as subscribed makes the sync a no-op
 * that preserves them instead of pruning them.
 */
class SandboxAuthProvider(
    private val scriptRepository: ScriptRepository,
) : AuthProvider {
    private val mockUser =
        User(
            id = "e9eba725-cbbd-4086-9986-9d6617938291",
            name = "Cereal test user",
            email = "noreply@cereal-automation.com",
            encryptionKey = "",
            accessToken = "mock-token",
            isGuest = true,
        )

    /**
     * Demo record capacities per installed script, keyed by package name, so the running `mock` app
     * exercises the capacity display on the start/config surface. Package "com.cereal" is the bundled
     * sample script (see `SandboxScriptSeeder`); its cap here mirrors the marketplace catalog entry in
     * [com.cereal.client.infrastructure.di.modules.SandboxSampleData]. Scripts absent from this map
     * derive [ScriptCapacity.None] and show no capacity — exercising the absent-field path too.
     */
    private val demoCapacities: Map<String, ScriptCapacity> =
        mapOf(
            "com.cereal" to ScriptCapacity.Limited(records = 50, unit = "records"),
        )

    override suspend fun authenticate(
        email: String,
        password: String,
    ): User = mockUser

    override suspend fun authenticateGuest(): User = mockUser

    override suspend fun authenticateWith(provider: OAuthProvider): User = mockUser

    override suspend fun register(
        name: String,
        email: String,
        password: String,
    ): User = mockUser

    override suspend fun getSubscriptions(ignoreCache: Boolean): List<Subscription> =
        scriptRepository.getInstalledScripts().first().map { scriptPackage ->
            Subscription(
                id = "sandbox_${scriptPackage.manifest.packageName}",
                entitlement =
                    ScriptEntitlement(
                        publicIdentifier = scriptPackage.manifest.packageName,
                        title = scriptPackage.manifest.name,
                        latestRelease =
                            Release(
                                versionName = scriptPackage.manifest.versionCode.toString(),
                                versionCode = scriptPackage.manifest.versionCode,
                                releaseNotes = null,
                            ),
                        latestDraftRelease = null,
                        shortDescription = null,
                        price = null,
                        supportUrl = scriptPackage.manifest.supportUrl,
                        capacity = demoCapacities[scriptPackage.manifest.packageName] ?: ScriptCapacity.None,
                    ),
            )
        }

    override suspend fun invalidateSubscriptionsCache() {
        // No-op: subscriptions are derived live from installed scripts.
    }

    override suspend fun getMyTeamScripts(ignoreCache: Boolean): List<ScriptEntitlement> = emptyList()

    override suspend fun forgotPassword(email: String) {
        // No-op in the sandbox.
    }
}
