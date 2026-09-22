package com.cereal.client.infrastructure.data.repository

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.user.User
import org.slf4j.LoggerFactory
import java.io.File
import java.net.JarURLConnection
import java.security.MessageDigest

/**
 * Seeds script JARs that are bundled into the app (under the `sandbox-scripts/` classpath resource,
 * wired in by the `mock` flavor's `processResources`) into the user's script directory, so the
 * sandboxed flavor shows runnable scripts out-of-the-box without depending on whatever is on the
 * developer's filesystem.
 *
 * Every `*.jar` under `sandbox-scripts/` is seeded — the bundled sample plus anything dropped into
 * `cereal-client/sandbox-scripts/`. Re-seeding keeps a seeded JAR in sync with the bundle: when a
 * rebuild changes a bundled JAR (e.g. adds `sdk_version` to its manifest), the previously-seeded
 * copy is refreshed instead of being pinned forever. A `.seedhash` sidecar records the bundle that
 * was last seeded so genuine local edits — a seeded copy the developer has since changed — are
 * still left untouched.
 *
 * Assumes the LOCAL environment, where [com.cereal.client.infrastructure.data.datasource.filesystem.security.Encryption]
 * reads JAR entries as plaintext — the bundled JARs are unencrypted. In any other environment the
 * loader would try to decrypt them and discard them as invalid.
 */
class SandboxScriptSeeder(
    private val config: ApplicationConfig,
) {
    private val logger = LoggerFactory.getLogger(SandboxScriptSeeder::class.java)

    fun seedFor(user: User) {
        val targetDir = File("${config.getScriptsDirectory}${File.separator}${user.id}")
        bundledScriptNames().forEach { resourceName ->
            val resource = javaClass.getResourceAsStream("/$SANDBOX_SCRIPTS_DIR/$resourceName")
            if (resource == null) {
                logger.warn("Bundled sandbox script '$resourceName' not found on the classpath; skipping.")
                return@forEach
            }
            val bundledBytes = resource.use { it.readBytes() }
            val bundledHash = sha256(bundledBytes)

            val target = File(targetDir, resourceName)
            val marker = File(targetDir, "$resourceName$SEED_MARKER_EXTENSION")
            if (target.exists() && !shouldReseed(target, marker, bundledHash)) {
                return@forEach
            }

            targetDir.mkdirs()
            target.writeBytes(bundledBytes)
            marker.writeText(bundledHash)
            logger.info("Seeded bundled sandbox script: ${target.absolutePath}")
        }
    }

    /**
     * Decides whether an already-present seeded JAR should be replaced by the current bundle.
     *
     * The [marker] records the hash of the bundle that was last seeded. We re-seed only when the
     * [target] is still byte-identical to what we seeded — i.e. the developer hasn't touched it —
     * but the bundle has since changed. A target that diverges from the recorded hash was edited
     * locally and is left alone. When there is no marker (a JAR seeded before this provenance
     * tracking existed) we refresh a stale copy once, so pre-existing seeds pick up the new bundle.
     */
    private fun shouldReseed(
        target: File,
        marker: File,
        bundledHash: String,
    ): Boolean {
        val currentHash = sha256(target.readBytes())
        if (currentHash == bundledHash) return false // already up to date

        val seededHash = marker.takeIf { it.exists() }?.readText()?.trim()
        return when (seededHash) {
            // No provenance: refresh the (possibly stale) seeded copy once.
            null -> true

            // Untouched since seeding, but the bundle changed -> refresh.
            currentHash -> true

            // Edited locally since seeding -> preserve.
            else -> false
        }
    }

    /**
     * Names of every `*.jar` bundled under the `sandbox-scripts/` classpath resource. Enumerating
     * instead of hardcoding names means dropping a JAR into `cereal-client/sandbox-scripts/` (or
     * adding another bundled script module) needs no code change here. Handles both a directory on
     * the classpath (`./gradlew run`) and entries inside the packaged application JAR.
     */
    private fun bundledScriptNames(): List<String> {
        val url = javaClass.getResource("/$SANDBOX_SCRIPTS_DIR")
        if (url == null) {
            logger.info("No bundled sandbox scripts found on the classpath.")
            return emptyList()
        }

        return when (url.protocol) {
            "file" -> {
                File(url.toURI())
                    .listFiles { file -> file.isFile && file.name.endsWith(JAR_EXTENSION) }
                    ?.map { it.name }
                    .orEmpty()
            }

            "jar" -> {
                val connection = url.openConnection() as JarURLConnection
                connection.jarFile
                    .entries()
                    .asSequence()
                    .map { it.name }
                    .filter { it.startsWith("$SANDBOX_SCRIPTS_DIR/") && it.endsWith(JAR_EXTENSION) }
                    .map { it.substringAfterLast('/') }
                    .filter { it.isNotBlank() }
                    .toList()
            }

            else -> {
                emptyList()
            }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val SANDBOX_SCRIPTS_DIR = "sandbox-scripts"
        private const val JAR_EXTENSION = ".jar"

        /** Suffix of the sidecar file recording the hash of the bundle last seeded for a JAR. */
        private const val SEED_MARKER_EXTENSION = ".seedhash"
    }
}
