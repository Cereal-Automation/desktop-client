package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.Script
import io.github.reactivecircus.cache4k.Cache
import kotlin.time.Duration.Companion.hours
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.Release as ApiRelease

class RealSubscriptionDataSource(
    private val marketplaceDataSource: MarketplaceDataSource,
    private val userSession: UserSession,
) : SubscriptionDataSource {
    private val cache = Cache.Builder<String, List<Subscription>>().expireAfterWrite(1.hours).build()

    override suspend fun getSubscriptions(): List<Subscription> =
        userSession.requireUser().let { user ->
            val cached = cache.get(user.id)
            cached ?: run {
                val subscriptions =
                    marketplaceDataSource.getMySubscriptions().map { subscription ->
                        Subscription(
                            id = subscription.id,
                            entitlement = subscription.script.toDomain(),
                        )
                    }
                cache.put(user.id, subscriptions)
                subscriptions
            }
        }

    override fun invalidateCache() {
        cache.invalidateAll()
    }

    private fun Script.toDomain(): ScriptEntitlement =
        ScriptEntitlement(
            publicIdentifier = this.publicIdentifier,
            title = this.title,
            latestRelease = this.latestRelease?.toDomain(),
            latestDraftRelease = this.latestDraftRelease?.toDomain(),
            shortDescription = this.shortDescription,
            price = this.price,
            supportUrl = this.supportUrl,
            capacity = ScriptCapacity.of(this.capacity, this.capacityUnit),
        )

    private fun ApiRelease.toDomain(): Release = Release(versionName = versionName, versionCode = versionCode, releaseNotes = releaseNotes)
}
