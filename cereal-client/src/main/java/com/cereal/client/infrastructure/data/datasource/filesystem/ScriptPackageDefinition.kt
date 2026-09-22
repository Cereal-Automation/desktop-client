package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.domain.model.script.ChildScript
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptPackage
import java.io.File

data class ManifestDefinition(
    val packageName: String,
    val name: String,
    val versionCode: Long,
    val instructions: String? = null,
    val sdkVersion: String? = null,
)

data class ScriptPackageDefinition(
    val source: File,
    val manifest: ManifestDefinition,
    val mainScript: MainScript,
    val childScripts: Map<String, ChildScript>,
)

fun ScriptPackageDefinition.toScriptPackage(supportUrl: String?) =
    ScriptPackage(
        source = source,
        manifest =
            Manifest(
                packageName = manifest.packageName,
                name = manifest.name,
                versionCode = manifest.versionCode,
                instructions = manifest.instructions,
                sdkVersion = manifest.sdkVersion,
                supportUrl = supportUrl,
            ),
        mainScript = mainScript,
        childScripts = childScripts,
    )
