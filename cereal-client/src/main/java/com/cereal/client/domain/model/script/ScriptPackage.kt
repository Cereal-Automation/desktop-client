package com.cereal.client.domain.model.script

import com.cereal.client.domain.model.exception.UnknownScriptVariantException
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import java.io.File
import kotlin.reflect.KClass

data class ScriptPackage(
    val source: File,
    val manifest: Manifest,
    val mainScript: MainScript,
    val childScripts: Map<String, ChildScript>,
    /**
     * User-selectable entry scripts by their stable manifest id. When present they replace the [mainScript] as the
     * package's entries; the [mainScript] then only serves clients that don't read variants.
     */
    val variants: Map<String, ScriptVariant> = emptyMap(),
) {
    init {
        require(variants.keys.none { it.isBlank() }) { "A variant id must not be blank." }
    }
}

/** A user-selectable entry script of a package, declared in the manifest's `variants`. */
data class ScriptVariant(
    val name: String,
    val instructions: String?,
    val script: MainScript,
) {
    init {
        require(name.isNotBlank()) { "A variant name must not be blank." }
    }
}

/** The ids of the entries a user can pick, in manifest order: the variants, or only the default entry (null) without them. */
val ScriptPackage.entryIds: List<String?>
    get() = variants.keys.toList().ifEmpty { listOf(null) }

/**
 * The entry script for [variantId]: the [ScriptPackage.mainScript] when null and the package has no variants,
 * otherwise that variant. Any other id, or null in a package with variants, throws [UnknownScriptVariantException].
 */
fun ScriptPackage.entry(variantId: String?): MainScript = if (isDefaultEntry(variantId)) mainScript else variant(variantId).script

/** The display name of the entry for [variantId]: the variant's name, or the package name for the default entry. */
fun ScriptPackage.entryName(variantId: String?): String = if (isDefaultEntry(variantId)) manifest.name else variant(variantId).name

/** The instructions of the entry for [variantId]; a variant never falls back to the package's instructions. */
fun ScriptPackage.entryInstructions(variantId: String?): String? = if (isDefaultEntry(variantId)) manifest.instructions else variant(variantId).instructions

private fun ScriptPackage.isDefaultEntry(variantId: String?) = variantId == null && variants.isEmpty()

private fun ScriptPackage.variant(variantId: String?): ScriptVariant = variants[variantId] ?: throw UnknownScriptVariantException(variantId)

interface Script {
    val clazz: KClass<out com.cereal.sdk.Script<*>>
    val configuration: ScriptConfigurationDefinition
}

data class MainScript(
    override val clazz: KClass<out com.cereal.sdk.Script<*>>,
    override val configuration: ScriptConfigurationDefinition,
) : Script

data class ChildScript(
    val name: String,
    override val clazz: KClass<out com.cereal.sdk.Script<*>>,
    override val configuration: ScriptConfigurationDefinition,
) : Script
