package com.cereal.client.infrastructure.data.datasource.filesystem.security

import com.cereal.client.application.Environment
import com.cereal_automation.cereal_client.BuildConfig
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.crypto.AEADBadTagException

class EncryptionTest {
    private val key = Encryption.getEncryptionKey("app-key", "user-key")

    // -------------------------------------------------------- key derivation --

    @Test
    fun `derived key is the raw SHA-256 digest, not a low-entropy ASCII string`() {
        val derived = Encryption.getEncryptionKey("app-key", "user-key", 32)

        val expected = MessageDigest.getInstance("SHA-256").digest("app-keyuser-key".toByteArray())
        assertArrayEquals(expected, derived.keyBytes)
        assertEquals(32, derived.keyBytes.size)

        // The bug: the old key only ever used 11 distinct byte values (decimal digits + '-').
        // A correct full-entropy key spreads across far more of the byte range.
        val distinctBytes = derived.keyBytes.toSet().size
        assertTrue(distinctBytes > 11) { "key has only $distinctBytes distinct byte values" }
    }

    @Test
    fun `key derivation is deterministic for the same inputs`() {
        val a = Encryption.getEncryptionKey("app-key", "user-key")
        val b = Encryption.getEncryptionKey("app-key", "user-key")

        assertArrayEquals(a.keyBytes, b.keyBytes)
    }

    @Test
    fun `sizes larger than the digest are stretched with PBKDF2 to full length`() {
        val derived = Encryption.getEncryptionKey("app-key", null, 64)

        assertEquals(64, derived.keyBytes.size)
        // PBKDF2 output is not a zero-padded digest.
        assertTrue(derived.keyBytes.drop(32).any { it != 0.toByte() }) { "key was zero-padded, not stretched" }
    }

    // ------------------------------------------------------------------ GCM --

    @Test
    fun `encryptBytes and decryptBytes round-trip with GCM`() {
        val plaintext = "hello world".toByteArray()

        val ciphertext = Encryption.encryptBytes(plaintext, key)
        val decrypted = Encryption.decryptBytes(ciphertext, key)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `encryptBytes produces different ciphertext on each call (random IV)`() {
        val plaintext = "same data".toByteArray()

        val ct1 = Encryption.encryptBytes(plaintext, key)
        val ct2 = Encryption.encryptBytes(plaintext, key)

        assertNotEquals(ct1.toList(), ct2.toList())
    }

    @Test
    fun `decryptBytes throws when GCM ciphertext is tampered`() {
        val plaintext = "sensitive".toByteArray()
        val ciphertext = Encryption.encryptBytes(plaintext, key).copyOf()

        // Flip a bit in the ciphertext payload (after magic + IV)
        val tamperIndex = 4 + 12 + 5 // magic(4) + iv(12) + offset into ciphertext
        ciphertext[tamperIndex] = (ciphertext[tamperIndex].toInt() xor 0xFF).toByte()

        assertThrows(AEADBadTagException::class.java) {
            Encryption.decryptBytes(ciphertext, key)
        }
    }

    @Test
    fun `decryptBytes throws when wrong key is used for GCM ciphertext`() {
        val plaintext = "sensitive".toByteArray()
        val ciphertext = Encryption.encryptBytes(plaintext, key)

        val wrongKey = Encryption.getEncryptionKey("other-app", "other-user")
        assertThrows(AEADBadTagException::class.java) {
            Encryption.decryptBytes(ciphertext, wrongKey)
        }
    }

    // -------------------------------------------------------- legacy CBC migration --

    @Test
    fun `decryptBytes can decrypt legacy CBC ciphertext for migration`() {
        val plaintext = "legacy value".toByteArray()

        // Produce a legacy CBC blob the same way the old code did, using the legacy key bytes.
        val legacyCiphertext = encryptCbcLegacy(plaintext, key.legacyKeyBytes)
        val decrypted = Encryption.decryptBytes(legacyCiphertext, key)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `decryptBytes can decrypt legacy GCM ciphertext keyed with the old ASCII key`() {
        // Regression for #618: builds before the key-derivation fix (#488) already used GCM but
        // keyed it with the low-entropy ASCII bytes. Such a blob carries the GCM magic, so it is
        // not routed to the CBC path; decryption must fall back to the legacy key under the magic.
        val plaintext = "settings value from 1.10.0".toByteArray()

        val legacyGcmCiphertext = encryptGcmWithKeyBytes(plaintext, key.legacyKeyBytes)
        val decrypted = Encryption.decryptBytes(legacyGcmCiphertext, key)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `round-trip through decryptBytes after encryptBytes does not use legacy path`() {
        // Verifies that new ciphertext carries the GCM magic prefix
        val plaintext = "new data".toByteArray()
        val ciphertext = Encryption.encryptBytes(plaintext, key)

        // First 4 bytes must be the GCM magic
        assertEquals(0x00.toByte(), ciphertext[0]) { "expected GCM magic byte 0" }
        assertEquals(0x47.toByte(), ciphertext[1]) { "expected GCM magic byte 1 'G'" }
        assertEquals(0x43.toByte(), ciphertext[2]) { "expected GCM magic byte 2 'C'" }
        assertEquals(0x4D.toByte(), ciphertext[3]) { "expected GCM magic byte 3 'M'" }
    }

    // -------------------------------------------------------- jar entry reads --

    @Test
    fun `readJarEntry reads a valid entry's bytes in full without truncation`(
        @TempDir tempDir: File,
    ) {
        // A non-trivial payload that spans many read-buffer iterations so a truncating
        // read would be caught. Bytes are deterministic so the comparison is exact.
        val payload = ByteArray(100 * 1024) { (it % 256).toByte() }
        val jarFile = File(tempDir, "valid.jar")
        JarOutputStream(jarFile.outputStream()).use { out ->
            out.putNextEntry(JarEntry("payload.bin"))
            out.write(payload)
            out.closeEntry()
        }

        val read = Encryption().readJarEntry(jarFile, "payload.bin", key)

        assertArrayEquals(payload, read)
    }

    @Test
    fun `readJarEntry returns null for a missing entry`(
        @TempDir tempDir: File,
    ) {
        val jarFile = File(tempDir, "valid.jar")
        JarOutputStream(jarFile.outputStream()).use { out ->
            out.putNextEntry(JarEntry("present.bin"))
            out.write(byteArrayOf(1, 2, 3))
            out.closeEntry()
        }

        assertEquals(null, Encryption().readJarEntry(jarFile, "absent.bin", key))
    }

    @Test
    fun `readJarEntry recovers a legacy GCM jar entry keyed with the old ASCII key`(
        @TempDir tempDir: File,
    ) {
        // Regression for #618 at the script-loading boundary: jars downloaded by builds before
        // the key-derivation fix (#488) hold GCM entries keyed with the old ASCII key. After the
        // upgrade readJarEntry must still decrypt them via the legacy-key fallback rather than
        // returning null (which would silently break script loading).
        // Force the encrypted path — the default test environment is LOCAL, which skips decryption.
        mockkObject(BuildConfig)
        try {
            every { BuildConfig.ENVIRONMENT } returns Environment.PRODUCTION

            val payload = "class bytes from 1.10.0".toByteArray()
            val legacyGcmEntry = encryptGcmWithKeyBytes(payload, key.legacyKeyBytes)
            val jarFile = File(tempDir, "legacy.jar")
            JarOutputStream(jarFile.outputStream()).use { out ->
                out.putNextEntry(JarEntry("Script.class"))
                out.write(legacyGcmEntry)
                out.closeEntry()
            }

            val read = Encryption().readJarEntry(jarFile, "Script.class", key)

            assertArrayEquals(payload, read)
        } finally {
            unmockkObject(BuildConfig)
        }
    }

    // ---------------------------------------------------------------- helpers --

    /**
     * Reproduces the old CBC encryption logic to generate legacy blobs for migration tests.
     * This is intentionally duplicated here to keep the test independent and to make it
     * clear what exact format is being tested.
     */
    private fun encryptCbcLegacy(
        data: ByteArray,
        keyBytes: ByteArray,
    ): ByteArray {
        val iv = ByteArray(16)
        java.security.SecureRandom().nextBytes(iv)
        val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, javax.crypto.spec.IvParameterSpec(iv))
        return iv + cipher.doFinal(data)
    }

    /**
     * Reproduces the GCM encryption a pre-#488 build performed: the same magic + IV + tag layout
     * as the current writer, but keyed with raw [keyBytes] (the old ASCII key). Used to forge a
     * legacy GCM blob for the migration test.
     */
    private fun encryptGcmWithKeyBytes(
        data: ByteArray,
        keyBytes: ByteArray,
    ): ByteArray {
        val gcmMagic = byteArrayOf(0x00, 0x47, 0x43, 0x4D)
        val iv = ByteArray(12)
        java.security.SecureRandom().nextBytes(iv)
        val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, javax.crypto.spec.GCMParameterSpec(128, iv))
        return gcmMagic + iv + cipher.doFinal(data)
    }
}
