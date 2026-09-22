package com.cereal.client.domain.model.script

import com.cereal.client.domain.model.exception.InvalidScriptCapacityException

/**
 * The capacity a script tier entitles the holder to, where the unit maps to dataset **records** — the
 * maximum number of records a run of the script may process.
 *
 * The server expresses this as a nullable `capacity` / `capacity_unit` pair; [of] normalises that pair
 * into this closed set so downstream code never has to reason about the raw nulls (and the illegal
 * "cap with no unit" combination the contract forbids collapses to [None]):
 *
 * - unit `null`/absent → [None] (a free / single-price script, no capacity concept).
 * - unit present, `capacity` `null` → [Unlimited].
 * - unit present, `capacity` an integer → [Limited].
 *
 * This type carries no user-facing copy: the presentation layer formats it via string resources.
 */
sealed class ScriptCapacity {
    /** The script has no capacity concept — nothing to display or enforce. */
    data object None : ScriptCapacity()

    /** A tiered script with no ceiling. [unit] names what is uncapped (e.g. `"records"`). */
    data class Unlimited(
        val unit: String,
    ) : ScriptCapacity()

    /** A tiered script entitled to at most [records] of [unit]. */
    data class Limited(
        val records: Int,
        val unit: String,
    ) : ScriptCapacity() {
        init {
            if (records < 0) {
                throw InvalidScriptCapacityException("records cannot be negative")
            }
        }
    }

    companion object {
        /**
         * Normalises the server's nullable `capacity` / `capacity_unit` pair into a [ScriptCapacity].
         * A `null`/absent [unit] — including a stray [capacity] with no unit — yields [None].
         */
        fun of(
            capacity: Int?,
            unit: String?,
        ): ScriptCapacity =
            when {
                unit == null -> None
                capacity == null -> Unlimited(unit)
                else -> Limited(capacity, unit)
            }
    }
}
