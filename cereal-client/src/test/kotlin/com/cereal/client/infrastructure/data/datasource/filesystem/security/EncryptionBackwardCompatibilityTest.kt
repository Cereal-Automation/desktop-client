package com.cereal.client.infrastructure.data.datasource.filesystem.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.Base64

/**
 * Backward-compatibility corpus for [Encryption].
 *
 * Each entry below is a **frozen** ciphertext blob captured from a real encryption format/era
 * the app has shipped, paired with the plaintext it must still decrypt to. The bytes were
 * produced once with a fixed, well-known key (see [APP_KEY]/[USER_KEY]) and pasted here as
 * literal Base64. The test only ever *reads* them with the current [Encryption.decryptBytes];
 * it never re-encrypts.
 *
 * ## Why this exists
 *
 * Ordinary round-trip tests (`encrypt(x)` then `decrypt(...)`) can never catch a
 * backward-compatibility break, because both sides move together when the key derivation or
 * ciphertext format changes. This is exactly how #618 slipped through: the key-derivation fix
 * (#488) silently orphaned every value 1.10.0 had written with AES/GCM under the old ASCII key.
 *
 * Frozen fixtures pin the bytes a previous release actually wrote, so any future change that
 * breaks reading them fails here immediately.
 *
 * ## Rules for this file (read before editing)
 *
 * - **Never modify or regenerate an existing fixture.** These bytes represent data sitting on
 *   real users' disks. If a code change makes one fail, the change is breaking backward
 *   compatibility — add a new reader/migration path in [Encryption], do not touch the fixture.
 * - **Only ever append.** When the ciphertext format or key derivation changes, capture a new
 *   fixture for the new era and add it to [corpus]; leave the older ones in place forever.
 * - The fixed key material is part of the contract — changing [APP_KEY]/[USER_KEY] would
 *   invalidate every fixture, which defeats the purpose.
 */
class EncryptionBackwardCompatibilityTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    fun `current code decrypts every historical ciphertext era`(fixture: Fixture) {
        val key = Encryption.getEncryptionKey(APP_KEY, USER_KEY, 32)

        val decrypted = String(Encryption.decryptBytes(Base64.getDecoder().decode(fixture.base64), key))

        assertEquals(fixture.expectedPlaintext, decrypted)
    }

    data class Fixture(
        val era: String,
        val base64: String,
        val expectedPlaintext: String,
    ) {
        override fun toString(): String = era
    }

    companion object {
        // Key material the fixtures were generated with. Part of the frozen contract — do not change.
        private const val APP_KEY = "corpus-app-key-v1"
        private const val USER_KEY = "corpus-user-key-v1"

        // ---- FROZEN CIPHERTEXT — DO NOT EDIT OR REGENERATE (see class kdoc) ----

        /** Era 1: pre-#489 AES/CBC, keyed with the old low-entropy ASCII key. */
        private const val ERA1_CBC_LEGACY =
            "rBUcbk8EMejcR9qJNY27Og9jjlGkfMtVHsbzHg9ZPATThEmqeYS0Dj1nf+nOIg62"

        /** Era 2: 1.10.0 AES/GCM, still keyed with the old ASCII key (the #618 regression). */
        private const val ERA2_GCM_LEGACY =
            "AEdDTaErixlCNYuOPf19wgLz1GiUeH45TwvC2JiixABBZyQm+WJtcvnxdk6YNLKKjJkWMqmQ0DO7h4P+ZOWMquB8SZ/i/5aPq/3vl9raWpkG96Z3Qw=="

        /** Era 3: post-#488 AES/GCM, keyed with the strong raw-digest key (current writer). */
        private const val ERA3_GCM_STRONG =
            "AEdDTakSKMfRCZLwfXGVuvFIn3acJxGSobbw1PNadamWEeXi1vLsj4Y6UXeZIGUPmDjes6EhKWqHiX1CfW1L8co4J929NizkauxMOY3cV7E1L0w5eei0qWAUJOo="

        @JvmStatic
        fun corpus(): List<Arguments> =
            listOf(
                Arguments.of(
                    Fixture(
                        era = "era 1 — pre-#489 CBC, legacy ASCII key",
                        base64 = ERA1_CBC_LEGACY,
                        expectedPlaintext = "pre-GCM CBC value (era 1)",
                    ),
                ),
                Arguments.of(
                    Fixture(
                        era = "era 2 — 1.10.0 GCM, legacy ASCII key (#618)",
                        base64 = ERA2_GCM_LEGACY,
                        expectedPlaintext = "1.10.0 GCM value keyed with the old ASCII key (era 2)",
                    ),
                ),
                Arguments.of(
                    Fixture(
                        era = "era 3 — post-#488 GCM, strong digest key",
                        base64 = ERA3_GCM_STRONG,
                        expectedPlaintext = "post-#488 GCM value keyed with the strong digest key (era 3)",
                    ),
                ),
            )
    }
}
