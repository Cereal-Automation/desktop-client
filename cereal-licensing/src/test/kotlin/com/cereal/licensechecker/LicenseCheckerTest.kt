package com.cereal.licensechecker

import com.cereal.licensechecker.fixtures.FakeHttpResponse
import com.cereal.licensechecker.fixtures.FakeLicenseComponent
import com.cereal.licensechecker.fixtures.TestKeyPair
import com.cereal.sdk.component.license.HttpResponse
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Behavioural tests for [LicenseChecker]. Each test wires the real signature-verification and
 * JSON-parsing code against in-memory fakes ([FakeLicenseComponent] / [FakeHttpResponse]) and a
 * freshly generated [TestKeyPair], asserting on the resulting [LicenseState] rather than on
 * interactions. The fake captures the salt the checker generates so the response can be signed
 * against the genuine value, exercising the real crypto path end-to-end.
 */
class LicenseCheckerTest {
    private lateinit var keyPair: TestKeyPair

    @BeforeEach
    fun setUp() {
        keyPair = TestKeyPair.generate()
    }

    @Test
    fun `returns Licensed for a valid signature and a licensed body`() =
        runTest {
            val body = """{"licensed":true}""".toByteArray()
            val component =
                FakeLicenseComponent { _, salt ->
                    signedResponse(salt, body)
                }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component)

            val result = checker.checkAccess()

            assertEquals(LicenseState.Licensed, result)
        }

    @Test
    fun `returns Unlicensed when signature is valid but body says not licensed`() =
        runTest {
            val body = """{"licensed":false}""".toByteArray()
            val component = FakeLicenseComponent { _, salt -> signedResponse(salt, body) }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component)

            val result = checker.checkAccess()

            assertEquals(LicenseState.Unlicensed, result)
        }

    @Test
    fun `returns Unlicensed when the signature does not match the body`() =
        runTest {
            // Sign the salt against a different body than the one actually returned: the signature
            // is structurally valid but verification must fail, so access is denied.
            val component =
                FakeLicenseComponent { _, salt ->
                    val signatureOverOtherBody = keyPair.sign(salt, """{"licensed":true}""".toByteArray())
                    FakeHttpResponse(
                        body = """{"licensed":true,"tampered":true}""".toByteArray(),
                        headers = mapOf(SIGNATURE_HEADER to signatureOverOtherBody),
                    )
                }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component)

            val result = checker.checkAccess()

            assertEquals(LicenseState.Unlicensed, result)
        }

    @Test
    fun `returns Unlicensed when the signature was made with a different key`() =
        runTest {
            // Attacker signs with their own key pair; the checker verifies against the real one.
            val attackerKeyPair = TestKeyPair.generate()
            val body = """{"licensed":true}""".toByteArray()
            val component =
                FakeLicenseComponent { _, salt ->
                    FakeHttpResponse(
                        body = body,
                        headers = mapOf(SIGNATURE_HEADER to attackerKeyPair.sign(salt, body)),
                    )
                }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component)

            val result = checker.checkAccess()

            assertEquals(LicenseState.Unlicensed, result)
        }

    @Test
    fun `returns Unlicensed when the signature header is absent`() =
        runTest {
            val component =
                FakeLicenseComponent { _, _ ->
                    FakeHttpResponse(body = """{"licensed":true}""".toByteArray())
                }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component)

            val result = checker.checkAccess()

            assertEquals(LicenseState.Unlicensed, result)
        }

    @Test
    fun `returns ErrorValidatingLicense with status code when the response is not successful`() =
        runTest {
            val component =
                FakeLicenseComponent { _, salt ->
                    signedResponse(
                        salt = salt,
                        body = """{"licensed":true}""".toByteArray(),
                        isSuccessful = false,
                        code = 503,
                    )
                }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component)

            val result = checker.checkAccess()

            val error = assertInstanceOf(LicenseState.ErrorValidatingLicense::class.java, result)
            assertTrue(error.message.contains("503"), "Message should surface the status code: ${error.message}")
        }

    @Test
    fun `returns ErrorValidatingLicense when the transport throws an IOException`() =
        runTest {
            val component = FakeLicenseComponent.failing("connection refused")
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component)

            val result = checker.checkAccess()

            val error = assertInstanceOf(LicenseState.ErrorValidatingLicense::class.java, result)
            assertTrue(
                error.message.contains("Unable to connect to server"),
                "Message should explain the connection failure: ${error.message}",
            )
        }

    @Test
    fun `returns Unlicensed when the body has no licensed field`() =
        runTest {
            val body = """{"other":"value"}""".toByteArray()
            val component = FakeLicenseComponent { _, salt -> signedResponse(salt, body) }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component)

            val result = checker.checkAccess()

            assertEquals(LicenseState.Unlicensed, result)
        }

    @Test
    fun `returns Unlicensed when the licensed field is a non-boolean string`() =
        runTest {
            // A value that is neither true nor false (and not the string content "true"/"false")
            // yields null from booleanOrNull, which the checker treats as not licensed.
            val body = """{"licensed":"yes"}""".toByteArray()
            val component = FakeLicenseComponent { _, salt -> signedResponse(salt, body) }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component)

            val result = checker.checkAccess()

            assertEquals(LicenseState.Unlicensed, result)
        }

    @Test
    fun `treats a quoted true string as licensed`() =
        runTest {
            // Documents a sharp edge: kotlinx-serialization's booleanOrNull parses the primitive's
            // content, so a JSON string "true" is accepted exactly like the boolean true.
            val body = """{"licensed":"true"}""".toByteArray()
            val component = FakeLicenseComponent { _, salt -> signedResponse(salt, body) }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component)

            val result = checker.checkAccess()

            assertEquals(LicenseState.Licensed, result)
        }

    @Test
    fun `returns Licensed when the response is licensed and inside the freshness window`() =
        runTest {
            // Server-stamped window [issued_at, expires_at]; the client clock sits inside it.
            val body = """{"licensed":true,"issued_at":$NOW,"expires_at":${NOW + 300}}""".toByteArray()
            val component = FakeLicenseComponent { _, salt -> signedResponse(salt, body) }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component) { NOW }

            val result = checker.checkAccess()

            assertEquals(LicenseState.Licensed, result)
        }

    @Test
    fun `returns Unlicensed when the freshness window has already expired`() =
        runTest {
            // A validly signed, licensed response whose window closed before the client's clock:
            // this is the replayed-stale-response case the freshness window is meant to bound.
            val body = """{"licensed":true,"issued_at":${NOW - 600},"expires_at":${NOW - 300}}""".toByteArray()
            val component = FakeLicenseComponent { _, salt -> signedResponse(salt, body) }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component) { NOW }

            val result = checker.checkAccess()

            assertEquals(LicenseState.Unlicensed, result)
        }

    @Test
    fun `returns Licensed at the exact expiry boundary`() =
        runTest {
            // The window is inclusive of its final second: expires_at == now is still fresh.
            val body = """{"licensed":true,"issued_at":${NOW - 300},"expires_at":$NOW}""".toByteArray()
            val component = FakeLicenseComponent { _, salt -> signedResponse(salt, body) }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component) { NOW }

            val result = checker.checkAccess()

            assertEquals(LicenseState.Licensed, result)
        }

    @Test
    fun `returns Licensed for a licensed body with no expiry field (backwards compatible)`() =
        runTest {
            // Old server that does not stamp a window: the additive fields are absent, so the
            // updated client must not enforce freshness and simply behaves as before.
            val body = """{"licensed":true}""".toByteArray()
            val component = FakeLicenseComponent { _, salt -> signedResponse(salt, body) }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component) { NOW }

            val result = checker.checkAccess()

            assertEquals(LicenseState.Licensed, result)
        }

    @Test
    fun `returns Unlicensed when an expired window is not licensed`() =
        runTest {
            // Not-licensed short-circuits regardless of the window; still Unlicensed.
            val body = """{"licensed":false,"issued_at":${NOW - 600},"expires_at":${NOW - 300}}""".toByteArray()
            val component = FakeLicenseComponent { _, salt -> signedResponse(salt, body) }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component) { NOW }

            val result = checker.checkAccess()

            assertEquals(LicenseState.Unlicensed, result)
        }

    @Test
    fun `closes the response after reading it`() =
        runTest {
            var captured: FakeHttpResponse? = null
            val component =
                FakeLicenseComponent { _, salt ->
                    signedResponse(salt, """{"licensed":true}""".toByteArray()).also {
                        captured = it as FakeHttpResponse
                    }
                }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component)

            checker.checkAccess()

            assertTrue(captured!!.closed, "Response should be closed to release its resources")
        }

    @Test
    fun `forwards the configured scriptId to the license component`() =
        runTest {
            val component =
                FakeLicenseComponent { _, salt ->
                    signedResponse(salt, """{"licensed":true}""".toByteArray())
                }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component)

            checker.checkAccess()

            assertEquals(SCRIPT_ID, component.capturedScriptId)
        }

    @Test
    fun `parses a finite capacity from a licensed body`() =
        runTest {
            val body = """{"licensed":true,"capacity":100,"capacity_unit":"records"}""".toByteArray()
            val component = FakeLicenseComponent { _, salt -> signedResponse(salt, body) }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component)

            val result = checker.checkLicense()

            assertEquals(LicenseState.Licensed, result.state)
            assertEquals(LicenseCapacity.Limited(100, "records"), result.capacity)
        }

    @Test
    fun `parses unlimited capacity when the unit is present but the cap is null`() =
        runTest {
            // A tiered script with no ceiling: the unit says "this is a capacity script", the null cap
            // says "unlimited" — distinct from a script that has no capacity concept at all.
            val body = """{"licensed":true,"capacity":null,"capacity_unit":"records"}""".toByteArray()
            val component = FakeLicenseComponent { _, salt -> signedResponse(salt, body) }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component)

            val result = checker.checkLicense()

            assertEquals(LicenseState.Licensed, result.state)
            assertEquals(LicenseCapacity.Unlimited("records"), result.capacity)
        }

    @Test
    fun `treats a null unit as no capacity even when a cap is present`() =
        runTest {
            // A bare cap with no unit is a contract contradiction; it is normalised away to None.
            val body = """{"licensed":true,"capacity":100,"capacity_unit":null}""".toByteArray()
            val component = FakeLicenseComponent { _, salt -> signedResponse(salt, body) }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component)

            val result = checker.checkLicense()

            assertEquals(LicenseState.Licensed, result.state)
            assertEquals(LicenseCapacity.None, result.capacity)
        }

    @Test
    fun `treats absent capacity fields as no capacity (backwards compatible)`() =
        runTest {
            // Older server that never stamps the additive fields: behaves exactly as a no-capacity script.
            val body = """{"licensed":true}""".toByteArray()
            val component = FakeLicenseComponent { _, salt -> signedResponse(salt, body) }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component)

            val result = checker.checkLicense()

            assertEquals(LicenseState.Licensed, result.state)
            assertEquals(LicenseCapacity.None, result.capacity)
        }

    @Test
    fun `ignores capacity fields when the body is not licensed`() =
        runTest {
            // Capacity is only meaningful when licensed == true; an unlicensed body must not surface it.
            val body = """{"licensed":false,"capacity":100,"capacity_unit":"records"}""".toByteArray()
            val component = FakeLicenseComponent { _, salt -> signedResponse(salt, body) }
            val checker = LicenseChecker(SCRIPT_ID, keyPair.publicKeyPem, component)

            val result = checker.checkLicense()

            assertEquals(LicenseState.Unlicensed, result.state)
            assertEquals(LicenseCapacity.None, result.capacity)
        }

    private fun signedResponse(
        salt: String,
        body: ByteArray,
        isSuccessful: Boolean = true,
        code: Int = 200,
    ): HttpResponse =
        FakeHttpResponse(
            body = body,
            headers = mapOf(SIGNATURE_HEADER to keyPair.sign(salt, body)),
            isSuccessful = isSuccessful,
            code = code,
        )

    private companion object {
        const val SCRIPT_ID = "com.cereal.test-script"
        const val SIGNATURE_HEADER = "X-Script-Signature"

        // A fixed "current time" (epoch seconds) so freshness-window tests are deterministic.
        const val NOW = 1_700_000_000L
    }
}
