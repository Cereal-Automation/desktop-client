package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationConfig
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FakeSubscriptionDataSourceTest {
    private val dataSource = FakeSubscriptionDataSource(InMemoryApplicationConfig())

    @Test
    fun `getSubscriptions returns the static seeded subscriptions`() =
        runTest {
            val subscriptions = dataSource.getSubscriptions()

            assertEquals(2, subscriptions.size)
            assertEquals("sub_com.cereal", subscriptions[0].id)
            assertEquals("com.cereal", subscriptions[0].entitlement.publicIdentifier)
            assertNotNull(subscriptions[0].entitlement.price)
            // The second sample script is free (no price).
            assertNull(subscriptions[1].entitlement.price)
            assertEquals("com.cereal.monitor.tgtg", subscriptions[1].entitlement.publicIdentifier)
        }

    @Test
    fun `getSubscriptions returns the same static data on repeated calls`() =
        runTest {
            assertEquals(dataSource.getSubscriptions(), dataSource.getSubscriptions())
        }

    @Test
    fun `invalidateCache is a no-op and leaves the data intact`() =
        runTest {
            val before = dataSource.getSubscriptions()

            dataSource.invalidateCache()

            assertEquals(before, dataSource.getSubscriptions())
        }
}
