package com.cereal.client.infrastructure.data.datasource.network.security

import com.cereal.client.infrastructure.data.datasource.network.models.LatestAppVersionJsonResponse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Backward-compatibility corpus for [ReleaseMetadataVerifier].
 *
 * Each entry is a **frozen** release-metadata payload paired with the RSA-SHA256 signature the
 * release pipeline actually produced over its canonical message, plus the public key that signature
 * was made for. The current [ReleaseMetadataVerifier.verify] must keep accepting every one of them.
 *
 * ## Why this exists
 *
 * Signed `latest-<os>.json` files live on the CDN and are **immutable** — once a release ships, its
 * signature is frozen on disk and on every user's update path. There is no migration: if the
 * canonical-message format ([ReleaseMetadataVerifier.canonicalMessage]) ever changes, every
 * already-signed release stops verifying and auto-update silently breaks for the entire install base.
 *
 * The existing [ReleaseMetadataVerifierTest] generates a fresh key pair per run and signs with the
 * current `canonicalMessage`, so signer and verifier move together — it can prove the verification
 * *logic* works but can **never** catch a canonical-format break. This corpus pins real bytes a
 * release would have written, so any change to the canonical message, the signature algorithm, or
 * the public-key parsing fails here immediately.
 *
 * ## Rules for this file (read before editing)
 *
 * - **Never modify or regenerate an existing fixture.** These bytes stand in for signatures sitting
 *   on the CDN and on users' machines. If a code change makes one fail, the change is breaking
 *   backward compatibility — preserve a reader for the historical canonical format, do not touch
 *   the fixture.
 * - **Only ever append.** If the canonical message or signing scheme changes, freeze a new fixture
 *   for the new era and add it to [corpus]; leave the older ones in place forever.
 * - The key material and field values are part of the contract. They were generated once with
 *   OpenSSL (RSA-2048, `openssl dgst -sha256 -sign`) over the canonical message
 *   `version|min_version|download_url|download_sha256`.
 */
class ReleaseMetadataSignatureBackwardCompatibilityTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    fun `current verifier accepts every historical signed release`(fixture: Fixture) {
        val verifier = ReleaseMetadataVerifier(fixture.publicKeyPem)

        assertTrue(verifier.verify(fixture.response)) {
            "Backward-compatibility break: a previously-signed release no longer verifies. " +
                "The canonical-message format or signature scheme changed — restore compatibility " +
                "instead of editing this fixture."
        }
    }

    data class Fixture(
        val era: String,
        val publicKeyPem: String,
        val response: LatestAppVersionJsonResponse,
    ) {
        override fun toString(): String = era
    }

    companion object {
        // ---- FROZEN RELEASE METADATA + SIGNATURE — DO NOT EDIT OR REGENERATE (see class kdoc) ----

        /**
         * Era 1: the original signed format introduced with #484.
         * Canonical message signed:
         *   `1.0.0|1.0.0|https://downloads.cereal-automation.com/client/1.0.0/Cereal-1.0.0.dmg|<sha256>`
         */
        private val ERA1_PUBLIC_KEY =
            """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqcZ+HrT90FXmNq3lG4C+
            s6tYg32DfLv6oUwW65a0BoazzuDH1hL+xf9QG+aWM8RJb9LmNRheO8KmRdyTeXmJ
            Z5YiR5Y55xXrCOIVwpMmUjzMGeb/nZrTKZNOc8sVkekX5XD137Re15ue91rVeGnj
            bkknylRzTRsPZAHYL6+bXPCJ6jxIEJAEKt1QJAuZyOwVoXqXDlxJ+EZozO69X/aQ
            dSB+DujUCnzLHfCeHkD6595w9mrAJvuS5Z0AoFi9/b4QYp4UFoGZpbBLDvIM0vHK
            3ppMVa4JrltTwPoE6xDUVvGCMUwS1Lb65hbsHpPsx0Lndq7PQ7zhqrDzBHg80okf
            TwIDAQAB
            -----END PUBLIC KEY-----
            """.trimIndent()

        private const val ERA1_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        private const val ERA1_SIGNATURE =
            "pVizvKgO/5n3SosR+j6Y6SXSVePN2RRcD7fiwYfrYA9Rz2t7QtA3XbIBVnGKdIhJ6BhIEjqdSsGGaDGtnFF" +
                "+1yaaE2W6sqeaTCbrDnQBdwSvFEyZhIvH6kkH4CSmTxeZpIRVKMn8xHnOKM+raVHN5GjSltzGMJy0HXh4Kavc" +
                "+yPtoPQeUJEG8MUIAGJUSgb8k+qx+QK8b+do5ybebg6CfOFnoekltDpeKdWvhOnTX62OiIStF7Xn3D/70Vvwn" +
                "umzUbYSqxilKCSdUIoL3ymrYyC0VxE9PkTdFSbFsseXnsiyrdZ/VYMGFZbFKHWrmC55rn/TOQYguSRXemsd+LEUcw=="

        @JvmStatic
        fun corpus(): List<Arguments> =
            listOf(
                Arguments.of(
                    Fixture(
                        era = "era 1 — original #484 signed format, RSA-2048 SHA-256",
                        publicKeyPem = ERA1_PUBLIC_KEY,
                        response =
                            LatestAppVersionJsonResponse(
                                version = "1.0.0",
                                minVersion = "1.0.0",
                                downloadUrl =
                                    "https://downloads.cereal-automation.com/client/1.0.0/Cereal-1.0.0.dmg",
                                downloadSha256 = ERA1_SHA256,
                                downloadSignature = ERA1_SIGNATURE,
                            ),
                    ),
                ),
            )
    }
}
