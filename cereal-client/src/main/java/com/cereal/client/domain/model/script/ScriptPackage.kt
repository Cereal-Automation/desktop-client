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
)

/** An additional, user-selectable entry script of a package, declared in the manifest's `variants`. */
data class ScriptVariant(
    val name: String,
    val instructions: String?,
    val script: MainScript,
)

/**
 * The entry script for [variantId]: the [ScriptPackage.mainScript] when null, otherwise that variant. An id the
 * package doesn't contain throws [UnknownScriptVariantException]; it never falls back to the default entry.
 */
fun ScriptPackage.entry(variantId: String?): MainScript = if (variantId == null) mainScript else variants[variantId]?.script ?: throw UnknownScriptVariantException(variantId)

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
