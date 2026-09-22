package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyHealth
import com.cereal.client.domain.service.ProxyHealthChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration

class OkHttpProxyHealthChecker(
    private val probeUrl: String = DEFAULT_PROBE_URL,
    private val clock: Clock = Clock.System,
) : ProxyHealthChecker {
    @OptIn(ExperimentalTime::class)
    override suspend fun check(proxy: Proxy): ProxyHealth =
        withContext(Dispatchers.IO) {
            val javaProxy =
                java.net.Proxy(
                    java.net.Proxy.Type.HTTP,
                    InetSocketAddress(proxy.address, proxy.port),
                )
            val client =
                OkHttpClient
                    .Builder()
                    .proxy(javaProxy)
                    .connectTimeout(PROBE_TIMEOUT.toJavaDuration())
                    .readTimeout(PROBE_TIMEOUT.toJavaDuration())
                    .callTimeout(PROBE_TIMEOUT.toJavaDuration())
                    .apply {
                        val user = proxy.username
                        val pass = proxy.password
                        if (user != null && pass != null) {
                            proxyAuthenticator { _, response ->
                                response.request
                                    .newBuilder()
                                    .header("Proxy-Authorization", Credentials.basic(user, pass))
                                    .build()
                            }
                        }
                    }.build()

            val started = clock.now()
            runCatching {
                client.newCall(Request.Builder().url(probeUrl).build()).execute().use { resp ->
                    val body = resp.body.string()
                    if (resp.isSuccessful && body.isNotBlank()) {
                        val latency = (clock.now() - started).inWholeMilliseconds
                        ProxyHealth.healthy(clock.now(), latency)
                    } else {
                        ProxyHealth.failed(clock.now(), "HTTP ${resp.code}")
                    }
                }
            }.getOrElse { e ->
                ProxyHealth.failed(clock.now(), e.message ?: (e::class.simpleName ?: "Unknown error"))
            }
        }

    companion object {
        const val DEFAULT_PROBE_URL = "https://api.ipify.org"
        private val PROBE_TIMEOUT = 8.seconds
    }
}
