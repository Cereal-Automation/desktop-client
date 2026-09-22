package com.cereal.client.application.script

import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.domain.repository.ApplicationPreferenceRepository
import kotlinx.coroutines.flow.first

class ScriptLicenseChecker(
    private val authProvider: AuthProvider,
    private val applicationPreferenceRepository: ApplicationPreferenceRepository,
) {
    suspend fun isLicensed(scriptPackage: ScriptPackage): Boolean = getEntitlement(scriptPackage) != null

    /**
     * The user's entitlement for [scriptPackage] — the matched subscription or team-script
     * [ScriptEntitlement], or `null` when the script is not licensed. This is the same match the
     * [isLicensed] gate performs, exposed so callers can read the entitled
     * [com.cereal.client.domain.model.script.ScriptCapacity] without a fresh network call.
     *
     * Reads the cached subscription/team data (up to ~1h stale, per the auth provider's caching); a
     * start-time read tolerates that staleness for an honor-system capacity check.
     */
    suspend fun getEntitlement(scriptPackage: ScriptPackage): ScriptEntitlement? {
        val myTeamScripts =
            if (applicationPreferenceRepository.isDevelopmentScriptsEnabled().first()) {
                authProvider.getMyTeamScripts()
            } else {
                emptyList()
            }

        val licensedScripts = authProvider.getSubscriptions().map { it.entitlement } + myTeamScripts

        return licensedScripts.firstOrNull {
            it.publicIdentifier == scriptPackage.manifest.packageName
        }
    }
}
