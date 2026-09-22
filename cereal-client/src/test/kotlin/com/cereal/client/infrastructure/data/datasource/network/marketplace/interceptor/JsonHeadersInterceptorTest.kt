package com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JsonHeadersInterceptorTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        client =
            OkHttpClient
                .Builder()
                .addInterceptor(JsonHeadersInterceptor())
                .build()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `intercept forces the JSON content-type and accept headers`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request =
            Request
                .Builder()
                .url(mockWebServer.url("/"))
                // A pre-existing, conflicting Content-Type must be overridden.
                .header("Content-Type", "text/plain")
                .build()

        client.newCall(request).execute().use { /* drain and close */ }

        val recorded = mockWebServer.takeRequest()
        assertEquals("application/json", recorded.getHeader("Content-Type"))
        assertEquals("application/json", recorded.getHeader("Accept"))
    }
}
