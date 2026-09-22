package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.infrastructure.data.datasource.network.exception.ApiException
import com.cereal.client.infrastructure.data.datasource.network.exception.AuthenticationException
import com.cereal.client.infrastructure.data.datasource.network.marsproxies.MarsProxiesApiClient
import com.cereal.client.infrastructure.data.datasource.network.marsproxies.dto.MarsProxiesGenerateProxyListRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

/**
 * Exercises the real MarsProxies network stack ([MarsProxiesApiClient] + [MarsProxiesProviderDataSource])
 * over MockWebServer: the HTTP contract for `/me` and `/subusers` (incl. 401 and a network failure) and
 * the DTO → domain mapping.
 */
class MarsProxiesProviderDataSourceTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var dataSource: MarsProxiesProviderDataSource

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val client = MarsProxiesApiClient(url = mockWebServer.url("").toString(), enableLogging = false)
        dataSource = MarsProxiesProviderDataSource(client)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `validateAndFetchAccount maps the me response and sends a bearer token`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"traffic_available": 84.2, "subusers_count": 3, "hash": "acct_hash"}"""),
            )

            val account = dataSource.validateAndFetchAccount("mp_live_token")

            assertEquals(84.2, account.availableTrafficGb)
            assertEquals(3, account.subUserCount)
            assertEquals("acct_hash", account.accountHash)

            val request = mockWebServer.takeRequest()
            assertEquals("/v1/residential/me", request.path)
            assertEquals("GET", request.method)
            assertEquals("Bearer mp_live_token", request.getHeader("Authorization"))
        }

    @Test
    fun `validateAndFetchAccount throws AuthenticationException on 401`() =
        runTest {
            mockWebServer.enqueue(MockResponse().setResponseCode(401))

            assertThrows<AuthenticationException> {
                dataSource.validateAndFetchAccount("bad_token")
            }
        }

    @Test
    fun `validateAndFetchAccount surfaces an IOException on a network failure`() =
        runTest {
            // No enqueued response + shutdown server → the call fails at the transport layer.
            mockWebServer.shutdown()

            assertThrows<IOException> {
                dataSource.validateAndFetchAccount("mp_live_token")
            }
        }

    @Test
    fun `fetchSubUsers maps the subusers response`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {"data": [
                          {"id": "su_1", "hash": "h1", "username": "u1", "password": "p1", "traffic_available": 10.0, "traffic_used": 2.0},
                          {"id": "su_2", "hash": "h2", "username": "u2", "password": "p2", "traffic_available": 40.0, "traffic_used": 1.0}
                        ]}
                        """.trimIndent(),
                    ),
            )

            val subUsers = dataSource.fetchSubUsers("mp_live_token")

            assertEquals(2, subUsers.size)
            assertEquals("h2", subUsers[1].hash)
            assertEquals(40.0, subUsers[1].trafficAvailable)
            assertEquals("/v1/residential/subusers", mockWebServer.takeRequest().path)
        }

    @Test
    fun `fetchSubUsers returns empty list when the account has no subusers`() =
        runTest {
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"data": []}"""))

            val subUsers = dataSource.fetchSubUsers("mp_live_token")

            assertTrue(subUsers.isEmpty())
        }

    @Test
    fun `generateProxies posts the request body and parses the rendered array`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        // The rendered passwords contain ':' and '_' to prove they survive transport intact.
                        """
                        [
                          "ultra.marsproxies.com:44443:subuser_us:pw_country-us_session-aabbccdd",
                          "ultra.marsproxies.com:44443:subuser_us:p:a:ss_with_colons"
                        ]
                        """.trimIndent(),
                    ),
            )

            val request =
                MarsProxiesGenerateProxyListRequest(
                    format = "{hostname}:{port}:{username}:{password}",
                    hostname = "ultra.marsproxies.com",
                    port = 44443,
                    rotation = "sticky",
                    lifetime = "30m",
                    subUserHash = "hash_top",
                    location = "country-us_state-texas_city-dallas",
                    proxyCount = 500,
                )

            val rendered = dataSource.generateProxies("mp_live_token", request)

            assertEquals(2, rendered.size)
            assertEquals("ultra.marsproxies.com:44443:subuser_us:p:a:ss_with_colons", rendered[1])

            val recorded = mockWebServer.takeRequest()
            assertEquals("/v1/residential/access/generate-proxy-list", recorded.path)
            assertEquals("POST", recorded.method)
            assertEquals("Bearer mp_live_token", recorded.getHeader("Authorization"))

            // Assert every field of the serialized body.
            val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
            assertEquals("{hostname}:{port}:{username}:{password}", body["format"]!!.jsonPrimitive.content)
            assertEquals("ultra.marsproxies.com", body["hostname"]!!.jsonPrimitive.content)
            assertEquals(44443, body["port"]!!.jsonPrimitive.content.toInt())
            assertEquals("sticky", body["rotation"]!!.jsonPrimitive.content)
            assertEquals("30m", body["lifetime"]!!.jsonPrimitive.content)
            assertEquals("hash_top", body["subuser_hash"]!!.jsonPrimitive.content)
            assertEquals("country-us_state-texas_city-dallas", body["location"]!!.jsonPrimitive.content)
            assertEquals(500, body["proxy_count"]!!.jsonPrimitive.content.toInt())
        }

    @Test
    fun `generateProxies omits lifetime and count for a rotating request`() =
        runTest {
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""["ultra.marsproxies.com:44443:u:p"]"""))

            val request =
                MarsProxiesGenerateProxyListRequest(
                    format = "{hostname}:{port}:{username}:{password}",
                    hostname = "ultra.marsproxies.com",
                    port = 44443,
                    rotation = "rotating",
                    lifetime = null,
                    subUserHash = "hash_top",
                    location = "country-gb",
                    proxyCount = null,
                )

            dataSource.generateProxies("mp_live_token", request)

            val body = Json.parseToJsonElement(mockWebServer.takeRequest().body.readUtf8()).jsonObject
            assertEquals("rotating", body["rotation"]!!.jsonPrimitive.content)
            assertNull(body["lifetime"])
            assertNull(body["proxy_count"])
        }

    @Test
    fun `generateProxies surfaces a generation failure as ApiException`() =
        runTest {
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            val request =
                MarsProxiesGenerateProxyListRequest(
                    format = "{hostname}:{port}:{username}:{password}",
                    hostname = "ultra.marsproxies.com",
                    port = 44443,
                    rotation = "sticky",
                    lifetime = "30m",
                    subUserHash = "hash_top",
                    location = null,
                    proxyCount = 10,
                )

            assertThrows<ApiException> {
                dataSource.generateProxies("mp_live_token", request)
            }
        }
}
