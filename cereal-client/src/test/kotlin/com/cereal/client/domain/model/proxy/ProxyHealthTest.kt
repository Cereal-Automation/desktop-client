package com.cereal.client.domain.model.proxy

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ProxyHealthTest {
    @Test
    fun `should create healthy ProxyHealth`() {
        val now = Instant.fromEpochMilliseconds(0)
        val health = ProxyHealth.healthy(lastCheckedAt = now, latencyMs = 42)

        assertEquals(ProxyHealthStatus.HEALTHY, health.status)
        assertEquals(42, health.latencyMs)
        assertEquals(null, health.lastError)
    }

    @Test
    fun `should throw when latency is negative`() {
        assertFailsWith<IllegalArgumentException> {
            ProxyHealth(
                status = ProxyHealthStatus.HEALTHY,
                lastCheckedAt = Instant.fromEpochMilliseconds(0),
                latencyMs = -1,
                lastError = null,
            )
        }
    }

    @Test
    fun `should throw when last error is blank`() {
        assertFailsWith<IllegalArgumentException> {
            ProxyHealth(
                status = ProxyHealthStatus.FAILED,
                lastCheckedAt = Instant.fromEpochMilliseconds(0),
                latencyMs = null,
                lastError = "   ",
            )
        }
    }

    @Test
    fun `Unknown should have all nullable fields null`() {
        assertEquals(ProxyHealthStatus.UNKNOWN, ProxyHealth.Unknown.status)
        assertEquals(null, ProxyHealth.Unknown.lastCheckedAt)
        assertEquals(null, ProxyHealth.Unknown.latencyMs)
        assertEquals(null, ProxyHealth.Unknown.lastError)
    }
}
