@file:OptIn(ExperimentalTime::class)

package com.cereal.client.domain.model.marketplace

import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.model.script.ScriptPublicIdentifier
import java.math.BigDecimal
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A script as it is listed on the Cereal marketplace.
 *
 * This is the domain representation; the wire/transport shape lives in the infrastructure layer and
 * is mapped to this type at the repository boundary.
 *
 * @property id Stable marketplace identifier (used for display keying and colour derivation).
 * @property publicIdentifier The package identifier used to install and reference the script.
 * @property latestRelease The most recent release available to install, or `null` if none exists yet.
 * @property developer The script owner, or `null` when no author is attributed.
 */
data class MarketplaceScript(
    val id: String,
    val publicIdentifier: ScriptPublicIdentifier,
    val title: String,
    val description: String? = null,
    val shortDescription: String? = null,
    val price: BigDecimal? = null,
    val formattedPrice: String? = null,
    val isFree: Boolean = false,
    val freeTrialEnabled: Boolean = false,
    val freeTrialDays: Int? = null,
    val supportUrl: String? = null,
    val averageRating: Double? = null,
    val ratingCount: Int = 0,
    val isCommunity: Boolean = false,
    val isNew: Boolean = false,
    val maintenanceMode: Boolean = false,
    val latestRelease: Release? = null,
    val developer: ScriptOwner? = null,
    val tags: List<String> = emptyList(),
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    /** A script can only be installed when it has a published release to pull. */
    val hasInstallableRelease: Boolean get() = latestRelease != null

    /** Paid scripts are everything not explicitly marked free. */
    val isPaid: Boolean get() = !isFree

    /** True when the script offers a usable free trial (enabled with a positive day count). */
    val hasActiveFreeTrial: Boolean get() = freeTrialEnabled && (freeTrialDays ?: 0) > 0
}
