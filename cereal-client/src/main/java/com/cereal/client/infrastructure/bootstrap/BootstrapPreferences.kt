package com.cereal.client.infrastructure.bootstrap

import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * A tiny boolean preference store that can be read and written *before* dependency injection starts.
 *
 * ## Why this exists separately from the Room-backed preferences
 *
 * [com.cereal.client.domain.repository.ApplicationPreferenceRepository] is the normal home for a
 * setting, and new settings belong there. It is unreachable from bootstrap for two independent
 * reasons:
 *
 * 1. **It is behind Koin.** Crash reporting is initialised in [com.cereal.client.App.initialize]
 *    *before* `Injector.initialize()`, deliberately — moving it later would blind us to crashes
 *    during startup, which is exactly when a crash report is most valuable.
 * 2. **It is per-user.** Those preferences are keyed by `UserSession.requireUser()`, so they do not
 *    exist until someone has logged in. A telemetry choice has to hold for the machine, from the
 *    first launch onwards.
 *
 * On top of that, the Room database is Sekret-encrypted, and the native secrets library is loaded a
 * few lines above the point where the value is needed.
 *
 * So: a plain `.properties` file next to the database, outside the encrypted store. The values here
 * are telemetry consent flags — not sensitive, which is what makes living outside the encrypted
 * database acceptable. Being plain text is also a feature for a client that asks to be audited: a
 * user can read the file and confirm their opt-out actually took effect.
 *
 * This is not a second preferences system and must not grow into one. See [BootstrapPreferenceKey].
 *
 * ## Failure behaviour
 *
 * Every operation is best-effort and never throws: bootstrap has no error UI, and failing to read a
 * preference file is not a reason to refuse to start. A missing, unreadable, or corrupt file reads
 * as "no preference recorded", so callers get [BootstrapPreferenceKey.defaultValue]. A corrupt file
 * is left in place rather than deleted — the next [set] replaces it wholesale, and until then it is
 * still the evidence of what went wrong. Note that a failed [set] is therefore silent to the caller
 * beyond the logs; it returns whether the write landed for callers that want to react.
 *
 * Reads hit the disk every time. The file is a few bytes and is read a handful of times per launch,
 * so caching would only add a staleness bug.
 */
class BootstrapPreferences(
    private val file: File,
) {
    /** Reads [key], falling back to its [BootstrapPreferenceKey.defaultValue]. */
    fun get(key: BootstrapPreferenceKey): Boolean = load()?.getProperty(key.key)?.toBooleanStrictOrNull() ?: key.defaultValue

    /**
     * Writes [key], preserving any other keys already in the file. Returns `false` if the write
     * failed, in which case a subsequent [get] will report the previous value.
     */
    fun set(
        key: BootstrapPreferenceKey,
        value: Boolean,
    ): Boolean {
        // Start from what is on disk so a corrupt file (load() == null) is replaced rather than
        // merged into, while a readable one keeps the keys this call is not touching.
        val properties = load() ?: Properties()
        properties.setProperty(key.key, value.toString())
        return store(properties)
    }

    private fun load(): Properties? {
        if (!file.isFile) return null
        return runCatching {
            Properties().apply { file.inputStream().use { load(it) } }
        }.onFailure {
            // IllegalArgumentException for a malformed \u escape, IOException for an unreadable file.
            logger.warn("Ignoring unreadable bootstrap preferences at ${file.path}: ${it.message}")
        }.getOrNull()
    }

    /**
     * Writes via a temporary file and an atomic rename, so an interrupted write cannot leave a
     * half-written file behind — which is the corruption [load] would otherwise have to absorb.
     */
    private fun store(properties: Properties): Boolean =
        runCatching {
            val directory = file.parentFile
            directory?.mkdirs()
            val temporaryFile = File.createTempFile(file.name, ".tmp", directory)
            try {
                temporaryFile.outputStream().use { properties.store(it, COMMENT) }
                moveIntoPlace(temporaryFile)
            } finally {
                temporaryFile.delete()
            }
        }.onFailure {
            logger.warn("Failed to write bootstrap preferences to ${file.path}: ${it.message}")
        }.isSuccess

    private fun moveIntoPlace(temporaryFile: File) {
        try {
            Files.move(
                temporaryFile.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: IOException) {
            // Some filesystems refuse an atomic replace (AtomicMoveNotSupportedException), and
            // Windows can refuse it while the destination is open. A plain replace still beats
            // writing to the destination in place. If this throws too, store() logs and reports it.
            Files.move(temporaryFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BootstrapPreferences::class.java)

        private const val FILE_NAME = "bootstrap.properties"

        private const val COMMENT =
            "Cereal settings that must be readable before the application is wired up. " +
                "Safe to edit or delete; deleting restores the defaults."

        /**
         * The store used by bootstrap, under the same brand-aware application home directory the
         * rest of the app's data lives in, so a branded or acceptance build never reads a stock
         * production build's choices.
         *
         * Resolved per access for the same reason [ApplicationHome.directory] is — an instance holds
         * nothing but a path, so there is no state worth caching and no init-order trap to create.
         *
         * Anything reading this store from *inside* the DI graph must go through here too, and not
         * rebuild the path from `ApplicationConfig.homeDirectory`: the `mock` flavor binds
         * `InMemoryApplicationConfig`, whose home directory is a temp dir, so the two would resolve
         * to different files and a setting written by the UI would not be the one bootstrap reads.
         */
        val default: BootstrapPreferences
            get() = BootstrapPreferences(File(ApplicationHome.directory, FILE_NAME))
    }
}
