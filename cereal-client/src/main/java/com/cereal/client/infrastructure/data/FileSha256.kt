package com.cereal.client.infrastructure.data

import com.cereal.client.domain.model.exception.UpdateVerificationException
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Computes the SHA-256 of a file as lowercase hex, matching the digest format carried in the
 * auto-update release metadata (`Version.downloadSha256`). Used to re-verify a downloaded
 * installer immediately before it is executed, closing the window between the streaming
 * download-time hash check and launch (CWE-367).
 */
object FileSha256 {
    /**
     * Re-hashes [file] and throws [UpdateVerificationException] when it does not match
     * [expectedSha256] (skipped when null) or cannot be read. [subject] names the artifact in the
     * error (e.g. "macOS update"). This is the hard-stop re-verification each self-installer runs
     * right before it launches or hands off the download, so a tampered/corrupt file is never
     * executed — never a silent fallback.
     */
    fun verifyMatch(
        file: File,
        expectedSha256: String?,
        subject: String,
    ) {
        if (expectedSha256 == null) return
        val actual =
            try {
                hash(file)
            } catch (e: IOException) {
                throw UpdateVerificationException("Could not read the $subject for verification", e)
            }
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            throw UpdateVerificationException(
                "$subject failed SHA-256 verification before launch: expected $expectedSha256 but was $actual",
            )
        }
    }

    fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and HEX_BYTE_MASK)
        }
    }

    /** 8 KiB read buffer. */
    private const val BUFFER_SIZE = 8 * 1024

    /** Mask to convert a signed byte to its unsigned value when hex-encoding the digest. */
    private const val HEX_BYTE_MASK = 0xFF
}
