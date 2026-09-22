package com.cereal.client.smoke

import com.cereal.client.App
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.nio.file.Files

/**
 * Headless obfuscation gate for the Cereal client.
 *
 * Invoked from `presentation/main.kt` when [isRequested] is true (env var [ENV_FLAG]=1), *before*
 * the headless guard and the Compose window, so it runs the genuine production launcher and
 * entrypoint on any runner — including headless CI — without ever opening a window. It boots the
 * real [App.initialize] and then exercises the obfuscation-fragile subsystems (DI graph, Room /
 * SQLite, the pinned network stack, kotlinx-serialization, release-signature crypto), returning a
 * process exit code: `0` when every fragile path survived obfuscation, non-zero when a build /
 * packaging path is broken and the release must be blocked.
 *
 * This automates the previously-manual "boot the prod build and read the log" ritual: instead of
 * eyeballing `session.log`, the release pipeline reads the exit code.
 */
object SmokeTest {
    const val ENV_FLAG = "CEREAL_SMOKE_TEST"

    private val logger = LoggerFactory.getLogger(SmokeTest::class.java)

    /** True when the smoke gate was requested via the [ENV_FLAG] environment variable. */
    fun isRequested(value: String? = System.getenv(ENV_FLAG)): Boolean =
        when (value?.trim()?.lowercase()) {
            "1", "true", "yes" -> true
            else -> false
        }

    /**
     * Runs every probe and returns the process exit code ([SmokeReport.EXIT_OK] /
     * [SmokeReport.EXIT_FAILED]). Never throws for a probe failure — a broken path is reported as a
     * blocking result, not an exception — but re-throws [CancellationException] to respect
     * structured concurrency.
     *
     * Catching [Throwable] is deliberate: obfuscation breakage surfaces as `LinkageError`/`Error`
     * (not `Exception`), and the whole point of the gate is to classify those rather than crash.
     */
    @Suppress("TooGenericExceptionCaught")
    fun run(): Int {
        isolateHomeDirectory()

        val results = mutableListOf<SmokeResult>()

        // Runs before App.initialize() on purpose: the bootstrap preference store's reason to exist
        // is being readable before DI, so probing it afterwards would not test that property.
        results.add(SmokeResult("bootstrap-preferences", SmokeProbes.exerciseBootstrapPreferences()))

        val koin =
            try {
                App.initialize()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Boot itself failed (native lib, DI graph, cert-pin self-test); the graph probes
                // cannot run, so report just this and block. App.initialize() is display-free today,
                // so this stays headless-safe — keep it that way: if boot ever grows an AWT/display
                // touch it would throw HeadlessException here and the classifier's conservative
                // "unclassified ⇒ blocking" rule would falsely fail the gate on headless CI runners.
                results.add(SmokeResult("boot", SmokeFailureClassifier.classify(e)))
                return finish(results)
            }
        results.add(SmokeResult("boot", ProbeOutcome.Pass("App.initialize() completed")))

        results.add(SmokeResult("di-resolve", SmokeProbes.resolveRootDefinitions(koin)))
        results.add(SmokeResult("database", SmokeProbes.exerciseDatabase(koin)))
        results.add(SmokeResult("network", SmokeProbes.exerciseNetwork(koin)))
        results.add(SmokeResult("serialization", SmokeProbes.exerciseSerialization()))
        results.add(SmokeResult("crypto", SmokeProbes.exerciseReleaseSignatureCrypto(koin)))

        return finish(results)
    }

    private fun finish(results: List<SmokeResult>): Int {
        val report = SmokeReport.render(results)
        // Print to stdout for CI capture and also log so it lands in session.log for local runs.
        println(report)
        logger.info("Smoke test finished:\n{}", report)
        return SmokeReport.exitCode(results)
    }

    /**
     * Points the app at a throwaway home directory so the smoke run never seeds or mutates the real
     * `~/<App>` data / DB / logs and stays reproducible.
     * [com.cereal.client.infrastructure.bootstrap.ApplicationHome] resolves the home directory from
     * `user.home` on each access (and `CerealConfiguration` delegates to it), so overriding the
     * property here takes effect for everything that follows. The Sekret native lib loads from `compose.application.resources.dir` (set by the
     * launcher), not `user.home`, so this does not disturb it.
     */
    private fun isolateHomeDirectory() {
        val tempHome = Files.createTempDirectory("cereal-smoke").toFile()
        // The run writes a DB and logs into this dir, so File.deleteOnExit() (which only removes an
        // empty dir) would leak it. Recursively delete the whole tree on shutdown instead.
        Runtime.getRuntime().addShutdownHook(Thread { tempHome.deleteRecursively() })
        System.setProperty("user.home", tempHome.absolutePath)
    }
}
