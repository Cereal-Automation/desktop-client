package com.cereal.client.infrastructure.data.datasource.filesystem.reader

import com.cereal.client.infrastructure.data.datasource.filesystem.ManifestDefinition
import com.cereal.client.infrastructure.data.datasource.filesystem.reader.models.ManifestJson
import com.cereal.client.infrastructure.data.datasource.filesystem.security.Encryption
import com.cereal.client.infrastructure.data.datasource.filesystem.security.EncryptionKey
import com.cereal.sdk.Script
import com.cereal.sdk.ScriptConfiguration
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass

/**
 * This class is designed to have 1 instance per jar that needs to be read so that there's a separate classloader
 * created for each script.
 */
class ScriptJarReader(
    val file: File,
    private val encryptionKey: EncryptionKey,
    private val encryption: Encryption,
) {
    private val logger = LoggerFactory.getLogger(ScriptJarReader::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val classLoader = ScriptClassLoader(file, encryptionKey, encryption)

    var mainScriptCls: KClass<Script<*>>? = null
    var mainScriptConfigurationCls: KClass<ScriptConfiguration>? = null
    val childScripts: MutableMap<KClass<Script<*>>, KClass<ScriptConfiguration>> =
        mutableMapOf()
    var manifest: ManifestDefinition? = null

    init {
        // First, read the manifest from the root of the jar
        val manifestJson = readManifest()
        manifest =
            manifestJson?.let { json ->
                ManifestDefinition(
                    packageName = json.packageName,
                    name = json.name,
                    versionCode = json.versionCode,
                    instructions = json.instructions,
                    sdkVersion = json.sdkVersion,
                )
            }

        manifestJson?.let { json ->
            json.scriptPackageClass.let { mainScriptClassName ->
                loadScript(mainScriptClassName)?.let { (scriptClass, configurationClass) ->
                    mainScriptCls = scriptClass
                    mainScriptConfigurationCls = configurationClass
                }
            }

            json.childScriptPackageClasses?.forEach { childScriptClassName ->
                loadScript(childScriptClassName)?.let { (scriptClass, configurationClass) ->
                    configurationClass?.let { configuration ->
                        childScripts[scriptClass] = configuration
                    }
                }
            }
        }
    }

    private fun readManifest(): ManifestJson? =
        try {
            val manifestBytes = encryption.readJarEntry(file, MANIFEST_FILE_NAME, encryptionKey)
            manifestBytes?.let { bytes ->
                val content = String(bytes)
                json.decodeFromString<ManifestJson>(content)
            }
        } catch (e: Exception) {
            logger.error("Failed to read manifest.json", e)
            null
        }

    // Loading untrusted script classes can fail with linkage Errors, not just Exceptions; catch broadly.
    @Suppress("TooGenericExceptionCaught")
    private fun loadScript(className: String): Pair<KClass<Script<*>>, KClass<ScriptConfiguration>?>? =
        try {
            val clazz = classLoader.loadClass(className)
            tryLoadScriptClass(clazz)?.let { scriptClass ->
                val configurationClass = getScriptConfigurationClass(scriptClass)
                scriptClass.kotlin to configurationClass?.kotlin
            }
        } catch (e: Throwable) {
            logger.error("Failed to load script class: $className", e)
            null
        }

    @Suppress("UNCHECKED_CAST")
    private fun tryLoadScriptClass(clazz: Class<*>): Class<Script<*>>? =
        try {
            if (clazz != Script::class.java) {
                // Try to assign Script to check if this is the main entry point.
                clazz.asSubclass(Script::class.java)
                clazz as Class<Script<*>>
            } else {
                null
            }
        } catch (_: Exception) {
            // Ignore, this class isn't a subclass of Script.
            null
        }

    @Suppress("UNCHECKED_CAST")
    private fun getScriptConfigurationClass(clazz: Class<*>): Class<ScriptConfiguration>? {
        val genericInterfaces: Array<Type> = clazz.genericInterfaces
        for (genericInterface in genericInterfaces) {
            if (genericInterface is ParameterizedType) {
                val genericTypes = genericInterface.actualTypeArguments
                for (genericType in genericTypes) {
                    return genericType as Class<ScriptConfiguration>
                }
            }
        }
        return null
    }

    companion object {
        private const val MANIFEST_FILE_NAME = "manifest.json"
    }
}
