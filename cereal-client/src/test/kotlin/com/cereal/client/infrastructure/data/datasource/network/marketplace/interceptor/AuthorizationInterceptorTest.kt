package com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthorizationInterceptorTest {
    private lateinit var mockWebServer: MockWebServer

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun clientWithToken(token: String?): OkHttpClient {
        val tokenDataSource =
            object : AuthorizationInterceptor.TokenDataSource {
                override fun getToken(): String? = token
            }
        return OkHttpClient
            .Builder()
            .addInterceptor(AuthorizationInterceptor(tokenDataSource))
            .build()
    }

    @Test
    fun `intercept adds a bearer authorization header when a token is available`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        clientWithToken("my-token")
            .newCall(Request.Builder().url(mockWebServer.url("/")).build())
            .execute()
            .use { }

        val recorded = mockWebServer.takeRequest()
        assertEquals("Bearer my-token", recorded.getHeader("Authorization"))
    }

    @Test
    fun `intercept omits the authorization header when there is no token`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        clientWithToken(null)
            .newCall(Request.Builder().url(mockWebServer.url("/")).build())
            .execute()
            .use { }

        val recorded = mockWebServer.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }
}
