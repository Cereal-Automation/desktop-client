package com.cereal.client.updater

import com.cereal.client.infrastructure.data.datasource.os.UpdateApplier
import com.cereal.client.infrastructure.data.datasource.os.UpdaterArguments
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

/**
 * Standalone entry point for the Windows update helper. It is launched as a *separate* JVM by
 * [com.cereal.client.infrastructure.data.datasource.os.WindowsUpdateInstaller] — the app-image's
 * bundled `javaw.exe` run against the app classpath — so it survives the main app's exit and can
 * apply the update once the install directory's file locks are released.
 *
 * Invoked as: `javaw -cp <classpath> com.cereal.client.updater.UpdaterMain <parentPid> <installer>
 * <appPath> <sha256|->`. This class and its `main` are kept by ProGuard because the launch is by
 * reflection (class name on the command line), not a static call the shrinker can trace.
 *
 * The real work lives in the testable [UpdateApplier]; this wrapper only parses argv and maps the
 * outcome to an exit code.
 */
object UpdaterMain {
    private val logger = LoggerFactory.getLogger(UpdaterMain::class.java)

    private const val EXIT_OK = 0
    private const val EXIT_APPLY_FAILED = 1
    private const val EXIT_BAD_ARGS = 2

    @JvmStatic
    fun main(args: Array<String>) {
        val arguments = UpdaterArguments.fromArgs(args)
        if (arguments == null) {
            logger.error(
                "UpdaterMain expects <parentPid> <installer> <appPath> <sha256|->, got: ${args.joinToString(" ")}",
            )
            exitProcess(EXIT_BAD_ARGS)
        }

        val applied = UpdateApplier().apply(arguments)
        exitProcess(if (applied) EXIT_OK else EXIT_APPLY_FAILED)
    }
}
