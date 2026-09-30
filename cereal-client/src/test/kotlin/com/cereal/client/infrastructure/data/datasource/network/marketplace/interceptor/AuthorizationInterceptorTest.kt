package com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

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

    private var sessionRejections = 0

    private fun clientWithToken(token: String?): OkHttpClient {
        val tokenDataSource =
            object : AuthorizationInterceptor.TokenDataSource {
                override fun getToken(): String? = token
            }
        return OkHttpClient
            .Builder()
            .addInterceptor(AuthorizationInterceptor(tokenDataSource) { sessionRejections++ })
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

    private fun call(
        token: String?,
        code: Int,
    ) {
        mockWebServer.enqueue(MockResponse().setResponseCode(code))
        clientWithToken(token)
            .newCall(Request.Builder().url(mockWebServer.url("/")).build())
            .execute()
            .use { }
    }

    @Test
    fun `a 401 to a request carrying the token reports the session rejected`() {
        call("my-token", 401)

        assertEquals(1, sessionRejections)
    }

    @Test
    fun `a 401 without a token, a server error or a network failure is not a rejected session`() {
        call(null, 401)
        call("my-token", 500)
        call("my-token", 403)
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertThrows(IOException::class.java) {
            clientWithToken("my-token").newCall(Request.Builder().url(mockWebServer.url("/")).build()).execute()
        }

        assertEquals(0, sessionRejections)
    }
}
