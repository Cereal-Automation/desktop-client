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
    /** Additional entry scripts by their stable manifest id. The [mainScript] is the default entry. */
    val variants: Map<String, ScriptVariant> = emptyMap(),
) {
    init {
        require(variants.keys.none { it.isBlank() }) { "A variant id must not be blank." }
    }
}

/** An additional, user-selectable entry script of a package, declared in the manifest's `variants`. */
data class ScriptVariant(
    val name: String,
    val instructions: String?,
    val script: MainScript,
) {
    init {
        require(name.isNotBlank()) { "A variant name must not be blank." }
    }
}

/**
 * The entry script for [variantId]: the [ScriptPackage.mainScript] when null, otherwise that variant. An id the
 * package doesn't contain throws [UnknownScriptVariantException]; it never falls back to the default entry.
 */
fun ScriptPackage.entry(variantId: String?): MainScript = if (variantId == null) mainScript else variant(variantId).script

/** The display name of the entry for [variantId]: the variant's name, or the package name for the default entry. */
fun ScriptPackage.entryName(variantId: String?): String = if (variantId == null) manifest.name else variant(variantId).name

/** The instructions of the entry for [variantId]; a variant never falls back to the package's instructions. */
fun ScriptPackage.entryInstructions(variantId: String?): String? = if (variantId == null) manifest.instructions else variant(variantId).instructions

private fun ScriptPackage.variant(variantId: String): ScriptVariant = variants[variantId] ?: throw UnknownScriptVariantException(variantId)

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
