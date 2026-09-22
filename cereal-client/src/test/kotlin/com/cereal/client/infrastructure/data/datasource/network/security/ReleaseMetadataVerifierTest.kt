package com.cereal.client.infrastructure.data.datasource.network.security

import com.cereal.client.infrastructure.data.datasource.network.models.LatestAppVersionJsonResponse
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class ReleaseMetadataVerifierTest {
    private lateinit var keyPair: KeyPair
    private lateinit var verifier: ReleaseMetadataVerifier

    @BeforeEach
    fun setUp() {
        keyPair =
            KeyPairGenerator
                .getInstance("RSA")
                .apply { initialize(RSA_KEY_SIZE) }
                .generateKeyPair()
        val publicKeyPem =
            "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getEncoder().encodeToString(keyPair.public.encoded) +
                "\n-----END PUBLIC KEY-----"
        verifier = ReleaseMetadataVerifier(publicKeyPem)
    }

    private fun sign(message: String): String {
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(message.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    @Test
    fun `verify returns true for a correctly signed response`() {
        val sha = "a".repeat(SHA256_HEX_LENGTH)
        val message = ReleaseMetadataVerifier.canonicalMessage("2.0.0", "1.0.0", "https://x/y.dmg", sha)
        val response =
            LatestAppVersionJsonResponse(
                version = "2.0.0",
                minVersion = "1.0.0",
                downloadUrl = "https://x/y.dmg",
                downloadSha256 = sha,
                downloadSignature = sign(message),
            )

        assertTrue(verifier.verify(response))
    }

    @Test
    fun `verify returns false when the signature is missing`() {
        val response =
            LatestAppVersionJsonResponse(
                version = "2.0.0",
                minVersion = "1.0.0",
                downloadUrl = "https://x/y.dmg",
                downloadSha256 = "a".repeat(SHA256_HEX_LENGTH),
                downloadSignature = null,
            )

        assertFalse(verifier.verify(response))
    }

    @Test
    fun `verify returns false when the sha256 is missing`() {
        val message = ReleaseMetadataVerifier.canonicalMessage("2.0.0", "1.0.0", "https://x/y.dmg", "")
        val response =
            LatestAppVersionJsonResponse(
                version = "2.0.0",
                minVersion = "1.0.0",
                downloadUrl = "https://x/y.dmg",
                downloadSha256 = null,
                downloadSignature = sign(message),
            )

        assertFalse(verifier.verify(response))
    }

    @Test
    fun `verify returns false when a signed field is tampered with`() {
        val sha = "a".repeat(SHA256_HEX_LENGTH)
        val message = ReleaseMetadataVerifier.canonicalMessage("2.0.0", "1.0.0", "https://trusted/y.dmg", sha)
        val response =
            LatestAppVersionJsonResponse(
                version = "2.0.0",
                minVersion = "1.0.0",
                // URL differs from the one that was signed.
                downloadUrl = "https://evil/y.dmg",
                downloadSha256 = sha,
                downloadSignature = sign(message),
            )

        assertFalse(verifier.verify(response))
    }

    @Test
    fun `verify returns false for a malformed signature`() {
        val response =
            LatestAppVersionJsonResponse(
                version = "2.0.0",
                minVersion = "1.0.0",
                downloadUrl = "https://x/y.dmg",
                downloadSha256 = "a".repeat(SHA256_HEX_LENGTH),
                downloadSignature = "not-base64-!@#",
            )

        assertFalse(verifier.verify(response))
    }

    @Test
    fun `verify returns false when signed by a different key`() {
        val otherKeyPair =
            KeyPairGenerator
                .getInstance("RSA")
                .apply { initialize(RSA_KEY_SIZE) }
                .generateKeyPair()
        val sha = "a".repeat(SHA256_HEX_LENGTH)
        val message = ReleaseMetadataVerifier.canonicalMessage("2.0.0", "1.0.0", "https://x/y.dmg", sha)
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(otherKeyPair.private)
        signer.update(message.toByteArray(Charsets.UTF_8))
        val foreignSignature = Base64.getEncoder().encodeToString(signer.sign())

        val response =
            LatestAppVersionJsonResponse(
                version = "2.0.0",
                minVersion = "1.0.0",
                downloadUrl = "https://x/y.dmg",
                downloadSha256 = sha,
                downloadSignature = foreignSignature,
            )

        assertFalse(verifier.verify(response))
    }

    private companion object {
        const val RSA_KEY_SIZE = 2048
        const val SHA256_HEX_LENGTH = 64
    }
}
