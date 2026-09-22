package com.cereal.client.domain.model.app

/**
 * Outcome of handing a downloaded update installer to [com.cereal.client.domain.provider.SystemProvider.installUpdate].
 */
enum class UpdateInstallResult {
    /**
     * A self-install was performed and the app will be replaced by a freshly launched instance. The
     * caller should terminate the current process so the relaunched one takes over.
     *
     * The exact timing differs per platform: on Linux (AppImage) and macOS (`.app` bundle swap) the
     * new instance is launched *before* the caller exits; on Windows the running install is locked,
     * so a detached helper waits for the caller's process to exit, applies the update, and only then
     * relaunches — i.e. the relaunch is helper-driven *after* this process is gone. In every case the
     * caller's obligation is the same: exit so the relaunched instance can take over.
     */
    Relaunching,

    /**
     * The installer was handed off to the OS and launched (e.g. the macOS `.dmg`/Windows installer).
     * The caller should exit so the installer can replace the running app.
     */
    Opened,

    /**
     * The installer could not be opened, but its containing folder was revealed so the user can
     * install it manually. The caller should keep running and surface the download location.
     */
    Revealed,

    /** The installer could neither be opened nor revealed. The caller should surface the location. */
    Failed,
}
