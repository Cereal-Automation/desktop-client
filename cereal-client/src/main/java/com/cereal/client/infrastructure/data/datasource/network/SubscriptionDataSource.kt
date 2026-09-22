package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.domain.model.user.Subscription

interface SubscriptionDataSource {
    suspend fun getSubscriptions(): List<Subscription>

    fun invalidateCache()
}
