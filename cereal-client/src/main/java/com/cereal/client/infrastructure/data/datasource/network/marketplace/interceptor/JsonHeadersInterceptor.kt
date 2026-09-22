package com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor

import okhttp3.Interceptor
import okhttp3.Response

class JsonHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        request =
            request
                .newBuilder()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build()

        return chain.proceed(request)
    }
}
