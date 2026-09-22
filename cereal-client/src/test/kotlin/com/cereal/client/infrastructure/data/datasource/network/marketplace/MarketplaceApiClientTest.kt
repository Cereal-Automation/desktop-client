package com.cereal.client.infrastructure.data.datasource.network.marketplace

import com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor.AuthorizationInterceptor
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.LoginRequestBody
import kotlinx.coroutines.runBlocking
import kotlin.test.assertNotNull

/**
 * Prerequisites:
 * - Api running on http://localhost/api
 * - A user with username test@cereal-automation.com and password Qwerty123
 * - A script with public_identifier com.cereal
 */
class MarketplaceApiClientTest {
    private val tokenStorage = InMemoryTokenStorage()

    val apiClient =
        MarketplaceApiClient(
            url = "http://localhost/api",
            version = "test",
            publicKey =
                "-----BEGIN PUBLIC KEY-----\n" +
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA296fBDCVBj23sYLkrWlh\n" +
                    "OMT7XFus7Ige9gb84sCJG9iea1xXlCqEE1pGFoW1mC5QgVhWFSMg8GBZx7Ts/euU\n" +
                    "U71G8StW30Aqtpn181P4Ddgk1Zprwctq/Z+KE9+LPK4kNj9e4DGu8w04CDOk8YqU\n" +
                    "QmMXC3sVSdy+k2eVhFBs5uwubTDhEiNgLOY1S2vzAgV3/7rJlvEZLKBoPxfEaMSG\n" +
                    "UnsrwaYrbQx5QsIwoPTZfZ+OzzPaE1QSdl+0Y9p6laNzPdR2POrrSz5Bo99vs8Qs\n" +
                    "Gz9bczjhCDyoubl/pSO8Lr8ZrBb/aqCeksSXLG9PGD3hChbliq9+rNRSpkt3phEJ\n" +
                    "L+pGnEySGOSReLdOEZK/j22rZSSeAFa/lsKGUQMT+PPF/mWf8YRqRVYeJTwo2E/M\n" +
                    "eHim4xSneFwgpDktxTKl/gIvZKaGqTOPgNd3g70DcMQ2AfBANyhBW75Yy2+9WAsJ\n" +
                    "glp4/NQiBFK07uJbdDXCzR7R3OxgMgQE+P+5FhEN5a6QDtwocR+VU7vsTbVwAAUP\n" +
                    "gpoZR/apz+bY3Ua8BnEGxsYi1yBuO1yWhjIHM0/PAXRmTl4LAF7iF/hzYC77w6F5\n" +
                    "/uFlvk4wC0hWPnIC8DHOeNS3YC5aK49fnWSMs7OJn0vD0Xas0JOIkgKH9yOJzk1E\n" +
                    "8GJr2KQ1t0uaO19Gb1jVHf0CAwEAAQ==\n" +
                    "-----END PUBLIC KEY-----",
            tokenDataSource = tokenStorage,
            enableLogging = true,
        )

    //    @Test
    fun testLoginOk() =
        runBlocking {
            login()
        }

    //    @Test
    fun testCheckScriptLicense() =
        runBlocking {
            login()

            apiClient.checkScriptLicense("com.cereal", "L7pylfHLCB8e6Q1jPZS1WNC3l9FGVuaK")
        }

    //    @Test
    fun testGetMySubscriptions() =
        runBlocking {
            login()

            val result = apiClient.getMySubscriptions()
            assertNotNull(result)
            Unit
        }

    //    @Test
    fun testGetFreeScripts() =
        runBlocking {
            login()

            val result = apiClient.getFreeScripts()
            assertNotNull(result)
            Unit
        }

    //    @Test
    fun getScriptDownloadLink() =
        runBlocking {
            login()

            val result = apiClient.getScriptDownloadLink("com.cereal", 1)
            assertNotNull(result)
            Unit
        }

    //    @Test
    fun verifyLicense() =
        runBlocking {
            login()

            val result = apiClient.verifyLicense("com.cereal")
            assertNotNull(result)
            Unit
        }

    private fun login() {
        runBlocking {
            val requestBody = LoginRequestBody("test@cereal-automation.com", "Qwerty123", "sample")
            val response = apiClient.authenticate(requestBody)
            tokenStorage.storedToken = response.token
        }
    }
}

private class InMemoryTokenStorage : AuthorizationInterceptor.TokenDataSource {
    var storedToken: String? = null

    override fun getToken(): String? = storedToken
}
