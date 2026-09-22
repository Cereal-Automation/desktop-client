package com.cereal.client.domain.model.script

import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import java.io.File
import kotlin.reflect.KClass

data class ScriptPackage(
    val source: File,
    val manifest: Manifest,
    val mainScript: MainScript,
    val childScripts: Map<String, ChildScript>,
)

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
