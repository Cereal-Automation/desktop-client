package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.exception.ClientUpdateRequiredException
import com.cereal.client.application.exception.LoadScriptException
import com.cereal.client.domain.model.extensions.isJar
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.isClientUpdateRequired
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.filesystem.reader.ScriptJarReader
import com.cereal.client.infrastructure.data.datasource.filesystem.security.Encryption
import com.cereal.sdk.ChildScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import net.swiftzer.semver.SemVer
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.reflect.full.findAnnotations
import com.cereal.client.domain.model.script.ChildScript as DomainChildScript

class FileSystemScriptsDataSource(
    private val config: ApplicationConfig,
    private val scriptConfigurationDefinitionBuilder: ScriptConfigurationDefinitionBuilder,
    private val encryption: Encryption,
) {
    private val logger = LoggerFactory.getLogger(FileSystemScriptsDataSource::class.java)
    private val scriptsFlow: MutableMap<String, MutableStateFlow<List<ScriptPackageDefinition>>> = HashMap()

    fun getScriptDefinitions(user: User): Flow<List<ScriptPackageDefinition>> = getOrCreateScriptsFlow(user)

    suspend fun getScriptPackageDefinition(
        packageName: String,
        user: User,
    ): ScriptPackageDefinition? {
        val definitions = getOrCreateScriptsFlow(user).first()
        return definitions.firstOrNull {
            it.manifest.packageName == packageName
        }
    }

    suspend fun updateScript(
        scriptPackage: ScriptPackage,
        release: Release,
        inputStream: InputStream,
        user: User,
        installedSdkVersion: SemVer,
    ): ScriptPackageDefinition {
        deleteScript(scriptPackage, user)

        return storeScript(scriptPackage.manifest.packageName, release, inputStream, user, installedSdkVersion)
    }

    suspend fun storeScript(
        packageName: String,
        release: Release,
        inputStream: InputStream,
        user: User,
        installedSdkVersion: SemVer,
    ): ScriptPackageDefinition {
        val file = resolveScriptFile(packageName, release, user)

        withContext(Dispatchers.IO) {
            // Ensure parent directory exists
            val parentDir = file.parentFile
            if (parentDir != null && !parentDir.exists()) {
                val created = parentDir.mkdirs()
                logger.debug("Creating directory: ${parentDir.absolutePath}, success: $created")
                if (!created && !parentDir.exists()) {
                    throw RuntimeException("Failed to create directory: ${parentDir.absolutePath}")
                }
            }
            // Create FileOutputStream after directory creation
            FileOutputStream(file).use { outputStream ->
                encryption.writeJar(inputStream, outputStream, getFileEncryptionKey(user))
            }
        }

        return try {
            readJar(file, user, enforceAgainstSdkVersion = installedSdkVersion)?.let { script ->
                val scriptPackages = getOrCreateScriptsFlow(user)
                scriptPackages.update {
                    val newList = scriptPackages.value.toMutableList()
                    newList.add(script)
                    newList
                }
                script
            } ?: run {
                // Delete the file if it couldn't be loaded
                logger.error("Failed to load downloaded script JAR, deleting file: ${file.absolutePath}")
                file.delete()
                throw LoadScriptException(packageName)
            }
        } catch (e: ClientUpdateRequiredException) {
            file.delete()
            throw e
        }
    }

    fun deleteScript(
        scriptPackage: ScriptPackage,
        user: User,
    ) {
        scriptPackage.source.delete()

        val scriptPackages = getOrCreateScriptsFlow(user)
        scriptPackages.update {
            val newList = scriptPackages.value.toMutableList()
            newList.removeIf { it.manifest.packageName == scriptPackage.manifest.packageName }
            newList
        }
    }

    private fun getFileEncryptionKey(user: User) = Encryption.getEncryptionKey(config.fileEncryptionKey, user.encryptionKey, ENCRYPTION_KEY_SIZE_BYTES)

    private fun readJar(
        file: File,
        user: User,
        enforceAgainstSdkVersion: SemVer? = null,
    ): ScriptPackageDefinition? {
        try {
            val scriptJarReader = ScriptJarReader(file, getFileEncryptionKey(user), encryption)

            if (scriptJarReader.mainScriptCls == null ||
                scriptJarReader.manifest == null ||
                scriptJarReader.mainScriptConfigurationCls == null
            ) {
                logger.error("${scriptJarReader.file.absoluteFile} does not contain a manifest or Script subclass.")
                return null
            }

            val manifest = scriptJarReader.manifest!!
            // Only reject incompatible scripts at install/update time (enforceAgainstSdkVersion != null).
            // When loading already-installed scripts we keep them so the UI can mark them as
            // "update required" instead of silently deleting them. The version compared against comes
            // from ApplicationRepository (threaded down from the repository), the same source the UI
            // badges use, so the install-time check and the badges can never disagree.
            if (enforceAgainstSdkVersion != null && isClientUpdateRequired(manifest.sdkVersion, enforceAgainstSdkVersion)) {
                throw ClientUpdateRequiredException(manifest.sdkVersion!!)
            }

            logger.info("Found compatible script: ${manifest.name}")
            val childScripts = mutableMapOf<String, DomainChildScript>()

            scriptJarReader.childScripts.forEach {
                val childScript = it.key.findAnnotations(ChildScript::class).first()

                childScripts[childScript.id] =
                    DomainChildScript(
                        childScript.name,
                        it.key,
                        scriptConfigurationDefinitionBuilder.createFrom(it.value),
                    )
            }

            return ScriptPackageDefinition(
                source = scriptJarReader.file,
                manifest = manifest,
                mainScript =
                    MainScript(
                        scriptJarReader.mainScriptCls!!,
                        scriptConfigurationDefinitionBuilder.createFrom(scriptJarReader.mainScriptConfigurationCls!!),
                    ),
                childScripts = childScripts,
            )
        } catch (e: ClientUpdateRequiredException) {
            throw e
        } catch (e: Exception) {
            logger.error("Ignore script jar because an unexpected error occurred when reading it: ${e.message}")
            return null
        }
    }

    private fun initializeScripts(user: User): MutableStateFlow<List<ScriptPackageDefinition>> {
        val scriptPackages = mutableListOf<ScriptPackageDefinition>()
        val scriptDir = File(getUserScriptDirectory(user))

        if (!scriptDir.exists()) {
            scriptDir.mkdirs()
        } else if (scriptDir.isDirectory) {
            scriptDir.listFiles()?.forEach { file ->
                if (file.isJar()) {
                    loadScriptJar(file, user, scriptPackages)
                }
            }
        } else if (scriptDir.isJar()) {
            loadScriptJar(scriptDir, user, scriptPackages)
        }

        return MutableStateFlow(scriptPackages)
    }

    private fun loadScriptJar(
        file: File,
        user: User,
        scriptPackages: MutableList<ScriptPackageDefinition>,
    ) {
        // Incompatible scripts (sdkVersion too high) are intentionally loaded here so the UI can mark them
        // as "update required"; the SDK version is only enforced at install/update time (see storeScript).
        readJar(file = file, user)?.let { scriptDefinition ->
            scriptPackages.add(scriptDefinition)
        } ?: run {
            // Delete the JAR file if it can't be loaded
            logger.warn("Deleting corrupted or invalid JAR file: ${file.absolutePath}")
            file.delete()
        }
    }

    private fun getUserScriptDirectory(user: User): String = "${config.getScriptsDirectory}${File.separator}${user.id}${File.separator}"

    /**
     * Builds the on-disk JAR path for a script, treating [packageName] and [release] version fields as
     * untrusted: they originate from the script manifest and server metadata, neither of which is signed
     * (see #494). The path components are validated against a strict allow-list and the resolved file is
     * asserted to stay inside the user's script directory, so a crafted manifest cannot escape it via
     * `..`, an absolute path, or a platform-reserved name.
     */
    private fun resolveScriptFile(
        packageName: String,
        release: Release,
        user: User,
    ): File {
        validatePathComponent(packageName, "packageName")
        validatePathComponent(release.versionName, "versionName")

        val userScriptDir = File(getUserScriptDirectory(user))
        val file = File(userScriptDir, "$packageName-${release.versionName}-${release.versionCode}.jar")

        val canonicalDir = userScriptDir.canonicalPath
        val canonicalFile = file.canonicalPath
        if (canonicalFile != canonicalDir && !canonicalFile.startsWith(canonicalDir + File.separator)) {
            logger.error("Refusing to store script '$packageName': resolved path escapes the user script directory.")
            throw LoadScriptException(packageName)
        }

        return file
    }

    private fun validatePathComponent(
        value: String,
        fieldName: String,
    ) {
        if (!PATH_COMPONENT_REGEX.matches(value)) {
            logger.error("Refusing to store script: $fieldName '$value' contains disallowed characters.")
            throw LoadScriptException(value)
        }
        if (value.substringBefore('.').uppercase() in WINDOWS_RESERVED_NAMES) {
            logger.error("Refusing to store script: $fieldName '$value' is a reserved name.")
            throw LoadScriptException(value)
        }
    }

    private fun getOrCreateScriptsFlow(user: User): MutableStateFlow<List<ScriptPackageDefinition>> {
        var stateFlow = scriptsFlow[user.id]
        if (stateFlow == null) {
            stateFlow = initializeScripts(user)
            scriptsFlow[user.id] = stateFlow
        }

        return stateFlow
    }

    companion object {
        private const val ENCRYPTION_KEY_SIZE_BYTES = 32

        // Allow-list for untrusted path components (packageName, versionName) used to build the on-disk
        // JAR filename. Restricting to this set blocks path separators, `..`, and absolute paths.
        private val PATH_COMPONENT_REGEX = Regex("^[A-Za-z0-9._-]{1,64}$")

        // Windows device names are reserved regardless of extension; reject them so a manifest cannot
        // produce an unusable or surprising file on that platform.
        private val WINDOWS_RESERVED_NAMES =
            setOf("CON", "PRN", "AUX", "NUL") +
                (1..9).map { "COM$it" } +
                (1..9).map { "LPT$it" }
    }
}
