package com.cereal.client.infrastructure.data.datasource.os

import java.io.File

/**
 * The four values the detached Windows updater helper needs, and the single definition of how they
 * cross its `javaw` argv boundary.
 *
 * These always travel together: [WindowsUpdateInstaller] builds the helper command line with
 * [toArgs] and [com.cereal.client.updater.UpdaterMain] reconstructs them with [fromArgs] on the
 * other side of the process launch. Keeping the positional layout, the "no digest" sentinel and the
 * argument count here means the producer and consumer share one definition instead of each carrying
 * its own copy that has to be kept in sync by hand.
 *
 * @property parentPid the app PID to wait for before applying — its exit releases the install-dir file locks.
 * @property installer the downloaded, signed installer to run silently.
 * @property app the launcher to relaunch once the install completes.
 * @property expectedSha256 the digest the installer is re-verified against, or null when none was published.
 */
data class UpdaterArguments(
    val parentPid: Long,
    val installer: File,
    val app: File,
    val expectedSha256: String?,
) {
    /**
     * The trailing arguments the helper is launched with (after `javaw -cp <classpath>
     * <UpdaterMain>`). A null [expectedSha256] is encoded as [NO_SHA] so the positions stay fixed.
     */
    fun toArgs(): List<String> =
        listOf(
            parentPid.toString(),
            installer.absolutePath,
            app.absolutePath,
            expectedSha256 ?: NO_SHA,
        )

    companion object {
        /** argv placeholder for "no digest available", so the positional arguments stay aligned. */
        private const val NO_SHA = "-"

        private const val PARENT_PID = 0
        private const val INSTALLER = 1
        private const val APP = 2
        private const val SHA = 3
        private const val COUNT = 4

        /**
         * Reconstructs the arguments from the helper's argv, or null when they are missing or
         * malformed (fewer than [COUNT] arguments, or a non-numeric pid) so the caller can exit with
         * a usage error. The [NO_SHA] sentinel decodes back to a null [expectedSha256].
         */
        fun fromArgs(args: Array<String>): UpdaterArguments? {
            if (args.size < COUNT) return null
            val parentPid = args[PARENT_PID].toLongOrNull() ?: return null
            return UpdaterArguments(
                parentPid = parentPid,
                installer = File(args[INSTALLER]),
                app = File(args[APP]),
                expectedSha256 = args[SHA].takeIf { it.isNotBlank() && it != NO_SHA },
            )
        }
    }
}
