package com.cereal.client.domain.model.script

import com.cereal.client.domain.model.exception.InvalidScriptEntitlementException
import java.math.BigDecimal

/**
 * A user's entitlement to a script — the grant produced by a subscription or by team membership,
 * carrying the granted script's identity/metadata plus the grant attributes.
 *
 * [capacity] is an attribute of the *grant*, not of the script itself: the same script grants
 * different capacity depending on the subscription tier (or unlimited for team members). This is
 * why capacity lives here and not on the marketplace/catalog script model.
 */
data class ScriptEntitlement(
    val publicIdentifier: ScriptPublicIdentifier,
    val title: String,
    val latestRelease: Release?,
    val latestDraftRelease: Release?,
    val shortDescription: String?,
    val price: BigDecimal?,
    val supportUrl: String? = null,
    /**
     * The record capacity this entitlement grants. [ScriptCapacity.None] for scripts with no capacity
     * concept and for older backends that do not yet send the field.
     */
    val capacity: ScriptCapacity = ScriptCapacity.None,
) {
    init {
        if (publicIdentifier.isBlank()) {
            throw InvalidScriptEntitlementException("publicIdentifier cannot be blank")
        }
        if (title.isBlank()) {
            throw InvalidScriptEntitlementException("title cannot be blank")
        }
        if (price != null && price < BigDecimal.ZERO) {
            throw InvalidScriptEntitlementException("price cannot be negative")
        }
    }

    /** True when this entitlement caps how many records a single run may process. */
    val isCapacityLimited: Boolean get() = capacity is ScriptCapacity.Limited

    /**
     * Whether a run processing [records] is permitted under this entitlement. Only a
     * [ScriptCapacity.Limited] grant can refuse; [ScriptCapacity.Unlimited] and [ScriptCapacity.None]
     * always allow. This is the single source of truth for the honor-system capacity check.
     */
    fun allows(records: Int): Boolean {
        val limited = capacity as? ScriptCapacity.Limited ?: return true
        return records <= limited.records
    }

    /**
     * The number of records still available after [usedRecords] have been consumed, or `null` when
     * this entitlement imposes no finite cap ([ScriptCapacity.Unlimited] / [ScriptCapacity.None]).
     * Never negative — an over-cap [usedRecords] clamps to `0`.
     */
    fun remainingCapacity(usedRecords: Int): Int? {
        val limited = capacity as? ScriptCapacity.Limited ?: return null
        return (limited.records - usedRecords).coerceAtLeast(0)
    }
}

data class Release(
    val versionName: String,
    val versionCode: Long,
    val releaseNotes: String?,
)
