package com.cereal.client.fixtures

import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.infrastructure.data.datasource.network.SubscriptionDataSource

/**
 * Configurable in-memory fake of [SubscriptionDataSource] for repository tests.
 *
 * [subscriptions] is the canned list returned by [getSubscriptions]; the test sets it to drive the
 * mapping under test. [invalidateCacheCallCount] records how many times the cache was invalidated.
 */
class FakeSubscriptionDataSource : SubscriptionDataSource {
    /** Canned subscriptions returned by [getSubscriptions]. */
    var subscriptions: List<Subscription> = emptyList()

    /** Number of times [invalidateCache] has been called. */
    var invalidateCacheCallCount: Int = 0
        private set

    override suspend fun getSubscriptions(): List<Subscription> = subscriptions

    override fun invalidateCache() {
        invalidateCacheCallCount++
    }
}
