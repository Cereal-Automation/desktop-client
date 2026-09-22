package com.cereal.client.application.script

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.domain.provider.ScriptInstallProvider
import com.cereal.client.domain.repository.ApplicationPreferenceRepository
import com.cereal.client.domain.repository.ScriptRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

/**
 * Progress of a [ScriptSyncManager.syncWithProgress] run.
 */
sealed class SyncProgress {
    /**
     * Emitted as the sync advances. [total] is the number of scripts in the sync list (0 until the
     * list has been resolved); [completed] advances to [total] regardless of per-script failures.
     */
    data class InProgress(
        val completed: Int,
        val total: Int,
    ) : SyncProgress()

    /**
     * Terminal emission carrying any per-script failures. Syncing is best-effort: a failed script does
     * not abort the sync, it is collected here instead.
     */
    data class Finished(
        val exceptions: Map<String, Exception>,
    ) : SyncProgress()
}

/**
 * Deletes, updates and installs scripts. If installing or updating a script fails this will not result in an exception
 * but instead a map with the scripts' package name with exception is returned.
 */
class ScriptSyncManager(
    private val authProvider: AuthProvider,
    private val scriptRepository: ScriptRepository,
    private val scriptInstallProvider: ScriptInstallProvider,
    private val scriptManager: ScriptManager,
    private val applicationPreferenceRepository: ApplicationPreferenceRepository,
    private val applicationConfig: ApplicationConfig,
) {
    private val logger = LoggerFactory.getLogger(ScriptSyncManager::class.java)

    /**
     * @param updateScripts when true the sync will also update outdated scripts.
     *
     * @return A map of script package names and exception, if any exceptions occurred during the sync process.
     */
    suspend fun sync(updateScripts: Boolean = false): Map<String, Exception> =
        syncWithProgress(updateScripts)
            .filterIsInstance<SyncProgress.Finished>()
            .first()
            .exceptions

    /**
     * Same as [sync] but emits [SyncProgress] as it advances, so callers can show per-script progress.
     * Emits an initial [SyncProgress.InProgress] (with [SyncProgress.InProgress.total] = 0 until the
     * sync list is known), one update per processed script, and a terminal [SyncProgress.Finished]
     * carrying any per-script failures.
     *
     * @param updateScripts when true the sync will also update outdated scripts.
     */
    fun syncWithProgress(updateScripts: Boolean = false): Flow<SyncProgress> =
        flow {
            val exceptions = mutableMapOf<String, Exception>()
            // Surface immediately so the UI can show "syncing" before the (network-bound) list is resolved.
            emit(SyncProgress.InProgress(completed = 0, total = 0))

            val syncMyTeamScripts = applicationPreferenceRepository.isDevelopmentScriptsEnabled().first()
            val scriptStoreListings = scopeToBrand(getScriptsToSync(syncMyTeamScripts))

            val installedScripts = scriptRepository.getInstalledScripts().first().toMutableList()
            removeScripts(scriptStoreListings, installedScripts)

            val total = scriptStoreListings.size
            var completed = 0
            emit(SyncProgress.InProgress(completed, total))

            scriptStoreListings.forEach { scriptStoreListing ->
                val packageName = scriptStoreListing.publicIdentifier
                val release =
                    if (syncMyTeamScripts && scriptStoreListing.latestDraftRelease != null) {
                        scriptStoreListing.latestDraftRelease
                    } else {
                        scriptStoreListing.latestRelease
                    }

                if (release == null) {
                    logger.warn("There's no release that can be downloaded so skipping '$packageName'.")
                } else {
                    // It's not blocking if a script couldn't be installed or updated so using try catch.
                    try {
                        // Make sure the subscribed script is installed.
                        val scriptPackage =
                            // If installed script is not present, download it.
                            installedScripts.firstOrNull { it.manifest.packageName == packageName }
                                ?: run {
                                    logger.info("Installing new script: $packageName")
                                    scriptInstallProvider.installScript(packageName, release)
                                }

                        if (updateScripts) {
                            updateScript(scriptPackage, release)
                        }
                    } catch (e: Exception) {
                        exceptions[packageName] = e
                    }
                }

                completed++
                emit(SyncProgress.InProgress(completed, total))
            }

            emit(SyncProgress.Finished(exceptions))
        }

    /**
     * Deletes the scripts that the user isn't allowed to have on its system.
     */
    private suspend fun removeScripts(
        scriptSubscriptions: List<ScriptEntitlement>,
        installedScripts: List<ScriptPackage>,
    ) {
        // Check for which installed scripts the user doesn't have subscriptions and remove those.
        val packages = scriptSubscriptions.map { it.publicIdentifier }
        val scriptsToRemove = installedScripts.map { it }.toMutableList()
        scriptsToRemove.removeAll {
            packages.contains(it.manifest.packageName)
        }

        scriptsToRemove.forEach {
            logger.info("Deleting script: $it")

            // Find script instances currently running and delete those.
            scriptManager.deleteScript(it)
        }
    }

    /***
     * Updates a [scriptPackage] to the provided [release] if needed.
     */
    private suspend fun updateScript(
        scriptPackage: ScriptPackage,
        release: Release,
    ) {
        val installedVersion = scriptPackage.manifest.versionCode
        val latestVersionCode = release.versionCode

        if (installedVersion < latestVersionCode) {
            // Installed version is older than available, update.
            logger.info("Updating script '${scriptPackage.manifest.packageName}' from $installedVersion to $latestVersionCode")
            scriptManager.updateScript(scriptPackage, release)
        } else if (latestVersionCode < installedVersion) {
            // Installed version is newer than available. The only way this should be possible is when
            // a developer stops syncing their development scripts (by turning the "Show development scripts"
            // option off in settings) so downgrade the script.
            logger.info("Downgrading script '${scriptPackage.manifest.packageName}' from $installedVersion to $latestVersionCode")
            scriptManager.updateScript(scriptPackage, release)
        } else {
            logger.info("Script '${scriptPackage.manifest.packageName}' already up-to-date: $installedVersion equals $latestVersionCode")
        }
    }

    /**
     * In a white-label build the appliance is locked to its Brand scripts (see docs/adr/0003): only
     * those are installed/kept, and any other subscribed script is treated as not-to-sync (and so is
     * removed by [removeScripts]). Stock Cereal returns the full list unchanged.
     */
    private fun scopeToBrand(listings: List<ScriptEntitlement>): List<ScriptEntitlement> {
        if (!applicationConfig.isBranded) return listings
        val brandScriptIds = applicationConfig.brandScriptIds.toSet()
        return listings.filter { it.publicIdentifier in brandScriptIds }
    }

    private suspend fun getScriptsToSync(includeMyTeamScripts: Boolean): List<ScriptEntitlement> =
        coroutineScope {
            val subscriptionScriptsAsync = async { authProvider.getSubscriptions(true).map { it.entitlement } }

            if (includeMyTeamScripts) {
                val teamScriptsAsync = async { authProvider.getMyTeamScripts(true) }

                val subscriptionScripts = subscriptionScriptsAsync.await()
                val teamScripts = teamScriptsAsync.await()

                // Deduplicate by removing the myteam scripts from the subscription scripts to prevent duplicates.
                // The "myteam scripts" are preferred over the subscription ones because those also contain the draft
                // release.
                subscriptionScripts.filter { subscriptionScript ->
                    teamScripts.none { teamScript ->
                        teamScript.publicIdentifier == subscriptionScript.publicIdentifier
                    }
                } + teamScripts
            } else {
                subscriptionScriptsAsync.await()
            }
        }
}
