package com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the session token. A `401` on a request that carried one means the marketplace rejected
 * the session and is reported to [onSessionRejected]; a `401` without a token (sign-in, code
 * exchange), or for a token that is no longer the session's, is not.
 */
class AuthorizationInterceptor(
    private val tokenDataSource: TokenDataSource,
    private val onSessionRejected: () -> Unit = {},
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val token = tokenDataSource.getToken()

        token?.let {
            request =
                request
                    .newBuilder()
                    .header("Authorization", "Bearer $it")
                    .build()
        }

        val response = chain.proceed(request)
        // Only when the rejected token is still the session's: a stale in-flight request must not sign out a fresh session.
        if (token != null && response.code == HTTP_UNAUTHORIZED && token == tokenDataSource.getToken()) onSessionRejected()
        return response
    }

    interface TokenDataSource {
        fun getToken(): String?
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
