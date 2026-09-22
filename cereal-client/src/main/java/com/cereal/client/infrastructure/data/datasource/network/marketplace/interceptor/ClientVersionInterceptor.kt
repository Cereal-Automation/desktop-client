package com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Tags every outgoing marketplace API request with the running client's version via the
 * `X-Client-Version` header, so the backend (and debug logs) can attribute requests to a
 * specific client build. The [version] is sent verbatim; even a dev/`unspecified` value is
 * useful signal server-side, so no filtering is applied.
 */
class ClientVersionInterceptor(
    private val version: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request =
            chain
                .request()
                .newBuilder()
                .header("X-Client-Version", version)
                .build()

        return chain.proceed(request)
    }
}
