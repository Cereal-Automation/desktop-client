package com.cereal.client.domain.model.script

import net.swiftzer.semver.SemVer

fun ScriptPackage.isClientUpdateRequired(currentSdkVersion: SemVer): Boolean = isClientUpdateRequired(manifest.sdkVersion, currentSdkVersion)

/**
 * Whether a script declaring [requiredSdkVersion] needs a newer client than [currentSdkVersion].
 * Compared on major.minor only; a null [requiredSdkVersion] means the script imposes no requirement.
 *
 * Single source of truth for the rule, shared by the install-time check
 * ([com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemScriptsDataSource]) and the
 * UI badges, so the two can never disagree. [currentSdkVersion] always originates from
 * [com.cereal.client.domain.repository.ApplicationRepository.getSdkVersion].
 */
fun isClientUpdateRequired(
    requiredSdkVersion: String?,
    currentSdkVersion: SemVer,
): Boolean {
    val sdkVersionStr = requiredSdkVersion ?: return false
    val requiredVersion = SemVer.parse(sdkVersionStr)
    val currentMajorMinor = SemVer(currentSdkVersion.major, currentSdkVersion.minor)
    val requiredMajorMinor = SemVer(requiredVersion.major, requiredVersion.minor)
    return currentMajorMinor < requiredMajorMinor
}
