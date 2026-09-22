package com.cereal.client.domain.model.app

import com.cereal.client.domain.model.exception.InvalidVersionException
import net.swiftzer.semver.SemVer

data class Version(
    val version: SemVer,
    val minRequiredVersion: SemVer,
    /**
     * Direct installer URL, or blank when none is available (store builds update through the OS
     * store via [storeUrl]; some metadata carries no direct download at all). Presence is enforced
     * at download time, not here, so a blank value never blocks a plain version check.
     */
    val downloadUrl: String,
    val storeUrl: String? = null,
    /** Expected lowercase hex SHA-256 of the installer, used to verify the download (#484). */
    val downloadSha256: String? = null,
) {
    init {
        if (downloadSha256 != null && !downloadSha256.matches(SHA256_HEX_REGEX)) {
            throw InvalidVersionException("DownloadSha256 must be a 64-character hex SHA-256")
        }
    }

    companion object {
        private val SHA256_HEX_REGEX = Regex("^[0-9a-fA-F]{64}$")
    }
}
