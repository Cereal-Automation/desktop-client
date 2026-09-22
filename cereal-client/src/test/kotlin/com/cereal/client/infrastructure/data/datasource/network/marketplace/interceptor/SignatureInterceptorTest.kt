package com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor

import com.cereal.client.application.exception.InvalidSignatureException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64

class SignatureInterceptorTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var keyPair: KeyPair
    private lateinit var wrongKeyPair: KeyPair
    private lateinit var client: OkHttpClient

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        keyPair = generator.generateKeyPair()
        wrongKeyPair = generator.generateKeyPair()

        val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        client =
            OkHttpClient
                .Builder()
                .addInterceptor(SignatureInterceptor(publicKeyBase64))
                .build()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    /** Signs `salt-bytes ++ base64(body-bytes)` exactly the way the interceptor verifies it. */
    private fun signedSignatureHeader(
        privateKey: PrivateKey,
        salt: String,
        body: ByteArray,
    ): String {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(salt.toByteArray() + Base64.getEncoder().encode(body))
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    private fun signingDispatcher(
        privateKey: PrivateKey,
        body: String,
    ) = dispatcher { request ->
        val salt = request.getHeader("X-Salt") ?: return@dispatcher MockResponse().setResponseCode(400)
        MockResponse()
            .setResponseCode(200)
            .setBody(body)
            .setHeader("X-Signature", signedSignatureHeader(privateKey, salt, body.toByteArray()))
    }

    private fun dispatcher(block: (RecordedRequest) -> MockResponse) =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = block(request)
        }

    private fun call() = client.newCall(Request.Builder().url(mockWebServer.url("/")).build())

    private fun assertThrowsInvalidSignature(): InvalidSignatureException = assertThrows(InvalidSignatureException::class.java) { call().execute() }

    @Test
    fun `intercept adds a 32 character salt header and passes a valid signature`() {
        val body = "response-payload"
        mockWebServer.dispatcher = signingDispatcher(keyPair.private, body)

        val response = call().execute()
        response.use {
            assertEquals(200, it.code)
        }

        val recorded = mockWebServer.takeRequest()
        assertEquals(32, recorded.getHeader("X-Salt")?.length)
    }

    @Test
    fun `intercept throws InvalidSignatureException when the response has no signature header`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("response-payload"))

        val exception = assertThrowsInvalidSignature()

        assertEquals(InvalidSignatureException.REASON_HEADER_ABSENT, exception.reason)
        assertEquals(200, exception.httpStatus)
    }

    @Test
    fun `intercept throws InvalidSignatureException when the signature was produced by a different key`() {
        // Valid Base64 and correct length, but signed with the wrong private key.
        mockWebServer.dispatcher = signingDispatcher(wrongKeyPair.private, "response-payload")

        val exception = assertThrowsInvalidSignature()

        assertEquals(InvalidSignatureException.REASON_MISMATCH, exception.reason)
    }

    @Test
    fun `intercept throws InvalidSignatureException when a cached response is signed for an earlier salt`() {
        // An edge cache replaying a previously signed body against a request carrying a fresh salt.
        val body = "response-payload"
        mockWebServer.dispatcher =
            dispatcher {
                MockResponse()
                    .setResponseCode(200)
                    .setBody(body)
                    .setHeader("X-Signature", signedSignatureHeader(keyPair.private, "an-earlier-request-salt", body.toByteArray()))
            }

        val exception = assertThrowsInvalidSignature()

        assertEquals(InvalidSignatureException.REASON_MISMATCH, exception.reason)
    }

    @Test
    fun `intercept throws InvalidSignatureException when the body was mutated after signing`() {
        val body = "response-payload"
        mockWebServer.dispatcher =
            dispatcher { request ->
                val salt = request.getHeader("X-Salt")!!
                MockResponse()
                    .setResponseCode(200)
                    .setBody("$body-injected")
                    .setHeader("X-Signature", signedSignatureHeader(keyPair.private, salt, body.toByteArray()))
            }

        val exception = assertThrowsInvalidSignature()

        assertEquals(InvalidSignatureException.REASON_MISMATCH, exception.reason)
    }

    @Test
    fun `intercept reports a malformed signature header as a signature failure rather than crashing`() {
        // Base64 decoding a garbage header throws IllegalArgumentException; letting it escape would
        // break OkHttp's IOException contract and be reported as an unexpected crash.
        mockWebServer.dispatcher =
            dispatcher {
                MockResponse().setResponseCode(200).setBody("response-payload").setHeader("X-Signature", "not-base64!!!")
            }

        val exception = assertThrowsInvalidSignature()

        assertEquals(InvalidSignatureException.REASON_NOT_BASE64, exception.reason)
    }

    @Test
    fun `intercept reports a wrong length signature as a signature failure rather than crashing`() {
        // Decodes as Base64, but Signature#verify rejects the length with a SignatureException.
        mockWebServer.dispatcher =
            dispatcher {
                MockResponse()
                    .setResponseCode(200)
                    .setBody("response-payload")
                    .setHeader("X-Signature", Base64.getEncoder().encodeToString(ByteArray(8)))
            }

        val exception = assertThrowsInvalidSignature()

        assertTrue(
            exception.reason.startsWith(InvalidSignatureException.REASON_CRYPTO_ERROR),
            "Expected a crypto_error reason but was ${exception.reason}",
        )
    }

    @Test
    fun `intercept passes unsigned error responses through so the real HTTP status survives`() {
        // Edge-generated errors (rate limits, WAF blocks, 5xx) never reach the backend's signing
        // middleware. Verifying them replaced the real status with a false tampering signal.
        listOf(429, 500, 502, 503).forEach { code ->
            mockWebServer.dispatcher =
                dispatcher {
                    MockResponse().setResponseCode(code).setBody("<html>edge error</html>")
                }

            call().execute().use { assertEquals(code, it.code) }
        }
    }

    @Test
    fun `intercept captures CDN diagnostics on an unsigned success response`() {
        mockWebServer.dispatcher =
            dispatcher {
                MockResponse()
                    .setResponseCode(200)
                    .setBody("response-payload")
                    .setHeader("cf-ray", "9a1b2c3d4e5f6789-HEL")
                    .setHeader("cf-cache-status", "HIT")
                    .setHeader("age", "42")
            }

        val exception = assertThrowsInvalidSignature()

        assertEquals("9a1b2c3d4e5f6789-HEL", exception.edgeHeaders["cf-ray"])
        assertEquals("HIT", exception.edgeHeaders["cf-cache-status"])
        assertEquals("42", exception.edgeHeaders["age"])
        assertEquals("HIT", exception.sentryContext()["cf-cache-status"])
        assertEquals(200, exception.sentryContext()["http_status"])
    }

    @Test
    fun `intercept verifies the decompressed body when the response arrives gzipped`() {
        // OkHttp adds Accept-Encoding itself, so application interceptors see decompressed bytes and
        // the origin's signature over the uncompressed body still verifies.
        val body = "response-payload-that-compresses"
        mockWebServer.dispatcher =
            dispatcher { request ->
                val salt = request.getHeader("X-Salt")!!
                val compressed = Buffer()
                GzipSink(compressed).buffer().use { it.writeUtf8(body) }
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Encoding", "gzip")
                    .setHeader("X-Signature", signedSignatureHeader(keyPair.private, salt, body.toByteArray()))
                    .setBody(compressed)
            }

        call().execute().use { assertEquals(200, it.code) }
    }

    @Test
    fun `intercept verifies a correctly signed empty body`() {
        mockWebServer.dispatcher =
            dispatcher { request ->
                val salt = request.getHeader("X-Salt")!!
                MockResponse()
                    .setResponseCode(200)
                    .setBody("")
                    .setHeader("X-Signature", signedSignatureHeader(keyPair.private, salt, ByteArray(0)))
            }

        call().execute().use { assertEquals(200, it.code) }
    }

    @Test
    fun `intercept keeps the salt on the follow up request after a redirect`() {
        val body = "response-payload"
        mockWebServer.dispatcher =
            dispatcher { request ->
                if (request.path == "/") {
                    MockResponse().setResponseCode(302).setHeader("Location", "/final")
                } else {
                    val salt = request.getHeader("X-Salt")!!
                    MockResponse()
                        .setResponseCode(200)
                        .setBody(body)
                        .setHeader("X-Signature", signedSignatureHeader(keyPair.private, salt, body.toByteArray()))
                }
            }

        call().execute().use { assertEquals(200, it.code) }
    }
}
