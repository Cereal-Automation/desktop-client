package com.cereal.client.domain.model

/**
 * Outcome of asking the system to open a file (e.g. a downloaded installer).
 */
enum class OpenFileResult {
    /** The file was handed off to an application (the installer/opener launched). */
    Opened,

    /**
     * The file could not be opened directly, but its containing folder was revealed in the
     * system file manager so the user can open it manually.
     */
    Revealed,

    /** The file could neither be opened nor revealed. */
    Failed,
}
