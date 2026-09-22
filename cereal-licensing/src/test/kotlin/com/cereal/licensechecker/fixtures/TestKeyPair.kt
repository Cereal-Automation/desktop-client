package com.cereal.licensechecker.fixtures

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64

/**
 * A freshly generated RSA key pair plus the helpers needed to drive
 * [com.cereal.licensechecker.LicenseChecker]'s signature verification with a *real* signature.
 *
 * Generating a key pair per test (rather than hardcoding a precomputed signature against a fixed
 * salt) means the tests no longer depend on the private salt value and survive refactors of the
 * salt-generation logic.
 */
class TestKeyPair private constructor(
    private val keyPair: KeyPair,
) {
    /** The public key encoded as a PEM string, in the form the checker expects to receive. */
    val publicKeyPem: String = encodePublicKeyToPem(keyPair)

    private val privateKey: PrivateKey get() = keyPair.private

    /**
     * Produces a valid `X-Script-Signature` header value over the exact bytes the checker signs:
     * `salt.toByteArray() + Base64.encode(body)`.
     */
    fun sign(
        salt: String,
        body: ByteArray,
    ): String {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(salt.toByteArray() + Base64.getEncoder().encode(body))
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    companion object {
        fun generate(): TestKeyPair {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
            return TestKeyPair(generator.generateKeyPair())
        }

        private fun encodePublicKeyToPem(keyPair: KeyPair): String {
            val base64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
            val wrapped = base64.chunked(64).joinToString("\n")
            return "-----BEGIN PUBLIC KEY-----\n$wrapped\n-----END PUBLIC KEY-----\n"
        }
    }
}
