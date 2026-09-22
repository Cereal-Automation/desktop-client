package com.cereal.client.domain.provider

import com.cereal.client.domain.model.OpenFileResult
import com.cereal.client.domain.model.app.UpdateInstallResult
import java.io.File

interface SystemProvider {
    suspend fun createTrayIcon()

    suspend fun browser(url: String)

    /**
     * Opens [directory] (a file or folder) with the system's default handler.
     *
     * When the file itself cannot be opened (common on Linux when no handler is registered for
     * the downloaded installer), its containing folder is revealed in the file manager instead so
     * the user can open it manually. The returned [OpenFileResult] reflects which happened.
     */
    suspend fun open(directory: File): OpenFileResult

    /**
     * Installs a freshly downloaded update [installer].
     *
     * When the client is running as an installed app on a supported OS, this self-applies the update
     * and relaunches ([UpdateInstallResult.Relaunching]): on Linux by replacing the running AppImage,
     * on macOS by swapping the running `.app` bundle, and on Windows by spawning a detached helper
     * that reinstalls silently after this process exits. When self-apply is not possible (not running
     * as an installed app, a self-apply step failed, or the install location would need elevation) it
     * falls back to handing the installer to the OS, returning [UpdateInstallResult.Opened] when it
     * launched, or [UpdateInstallResult.Revealed]/[UpdateInstallResult.Failed] when only its folder
     * could be shown for a manual install.
     *
     * When [expectedSha256] is provided, the installer is re-hashed immediately before it is
     * executed and the install aborts with
     * [com.cereal.client.domain.model.exception.UpdateVerificationException] on mismatch. The
     * download was already hash-checked while streaming, but the file sits on disk between that
     * check and launch (CWE-367), so the digest must be verified again at the point of use.
     */
    suspend fun installUpdate(
        installer: File,
        expectedSha256: String?,
    ): UpdateInstallResult
}
