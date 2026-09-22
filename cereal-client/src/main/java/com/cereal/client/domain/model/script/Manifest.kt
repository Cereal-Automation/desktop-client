package com.cereal.client.domain.model.script

import com.cereal.client.domain.model.exception.InvalidManifestException

data class Manifest(
    val packageName: ScriptPublicIdentifier,
    val name: String,
    val versionCode: Long,
    val instructions: String? = null,
    val supportUrl: String? = null,
    val sdkVersion: String? = null,
) {
    init {
        if (packageName.isBlank()) throw InvalidManifestException("PackageName cannot be blank")
        if (name.isBlank()) throw InvalidManifestException("Name cannot be blank")
        if (versionCode < 0) throw InvalidManifestException("VersionCode cannot be negative")
    }
}
