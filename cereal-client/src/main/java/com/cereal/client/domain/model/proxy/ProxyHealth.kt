package com.cereal.client.domain.model.proxy

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

enum class ProxyHealthStatus {
    UNKNOWN,
    HEALTHY,
    FAILED,
}

@OptIn(ExperimentalTime::class)
data class ProxyHealth(
    val status: ProxyHealthStatus,
    val lastCheckedAt: Instant?,
    val latencyMs: Long?,
    val lastError: String?,
) {
    init {
        latencyMs?.let {
            require(it >= 0) { "Latency must not be negative" }
        }
        lastError?.let {
            require(it.isNotBlank()) { "Last error must not be blank if provided" }
        }
    }

    companion object {
        val Unknown: ProxyHealth = ProxyHealth(ProxyHealthStatus.UNKNOWN, null, null, null)

        fun healthy(
            lastCheckedAt: Instant,
            latencyMs: Long,
        ): ProxyHealth = ProxyHealth(ProxyHealthStatus.HEALTHY, lastCheckedAt, latencyMs, null)

        fun failed(
            lastCheckedAt: Instant,
            error: String,
        ): ProxyHealth = ProxyHealth(ProxyHealthStatus.FAILED, lastCheckedAt, null, error)
    }
}
