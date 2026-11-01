package com.cereal.client.domain.repository

/**
 * The machine-wide goal sharing choice (Settings → Privacy) and what goes with it: whether the one-time Home
 * notice was seen, and the pseudonymous install id goal run reports carry. Holds before anyone signs in.
 */
interface GoalSharingRepository {
    /** Whether goal runs may be reported. `true` unless the user has opted out. */
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)

    fun isNoticeSeen(): Boolean

    fun setNoticeSeen()

    /** A random id created on first use and kept from then on. */
    fun installId(): String
}
