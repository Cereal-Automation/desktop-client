package com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor

import okhttp3.Interceptor
import okhttp3.Response

class AuthorizationInterceptor(
    private val tokenDataSource: TokenDataSource,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        tokenDataSource.getToken()?.let {
            request =
                request
                    .newBuilder()
                    .header("Authorization", "Bearer $it")
                    .build()
        }

        return chain.proceed(request)
    }

    interface TokenDataSource {
        fun getToken(): String?
    }
}
