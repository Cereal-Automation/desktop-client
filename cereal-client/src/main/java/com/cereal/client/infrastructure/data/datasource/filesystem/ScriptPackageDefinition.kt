package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.domain.model.script.ChildScript
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptFeature
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptVariant
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger(ScriptPackageDefinition::class.java)

data class ManifestDefinition(
    val packageName: String,
    val name: String,
    val versionCode: Long,
    val instructions: String? = null,
    val sdkVersion: String? = null,
    val features: List<String> = emptyList(),
)

data class ScriptPackageDefinition(
    val source: File,
    val manifest: ManifestDefinition,
    val mainScript: MainScript,
    val childScripts: Map<String, ChildScript>,
    val variants: Map<String, ScriptVariant> = emptyMap(),
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
                features = manifest.toScriptFeatures(),
            ),
        mainScript = mainScript,
        childScripts = childScripts,
        variants = variants,
    )

// Unknown values come from scripts built for a newer client. Don't block them: sdk_version is what asks for an update.
private fun ManifestDefinition.toScriptFeatures(): Set<ScriptFeature> =
    features.mapNotNullTo(mutableSetOf()) { value ->
        ScriptFeature.fromManifestValue(value).also {
            if (it == null) logger.warn("Ignoring unknown script feature '{}' in package {}", value, packageName)
        }
    }
