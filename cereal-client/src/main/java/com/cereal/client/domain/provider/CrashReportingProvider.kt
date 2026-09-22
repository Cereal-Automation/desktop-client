package com.cereal.client.domain.provider

/**
 * The user's choice about crash reporting, and the switch that enforces it.
 *
 * A *provider* rather than a repository: the thing being controlled is an outbound channel to a
 * third party, not an entity this application owns. Enabling or disabling it is an effect on that
 * channel — the stored flag is only how the choice survives a restart.
 *
 * The contract is that disabling **stops the reporting client**, rather than letting it run and
 * discarding events afterwards. An opt-out that still starts the SDK and still opens a connection
 * is not an opt-out, and in an auditable client it would read as one in the source.
 */
interface CrashReportingProvider {
    /** Whether crash reports may be sent. `true` unless the user has opted out. */
    fun isEnabled(): Boolean

    /**
     * Records the choice and applies it immediately: starting the reporting client when [enabled],
     * shutting it down when not. Takes effect without a restart in both directions.
     */
    fun setEnabled(enabled: Boolean)
}
