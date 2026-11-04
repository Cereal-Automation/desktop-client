package com.cereal.client.domain.repository

/**
 * The machine-wide Share diagnostics choice (Settings → Privacy) and the pseudonymous install id goal run
 * reports carry. Holds before anyone signs in.
 */
interface DiagnosticsSharingRepository {
    /** Whether diagnostics may be shared. `true` unless the user has opted out. */
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)

    /** A random id created on first use and kept from then on. */
    fun installId(): String
}
