package com.cereal.client.infrastructure.data.datasource.network.security

import com.cereal.client.infrastructure.data.datasource.network.models.LatestAppVersionJsonResponse
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies the authenticity of release metadata (`latest-<os>.json`) before the client acts on it.
 *
 * The release pipeline signs a canonical message derived from the metadata with the private
 * counterpart of [publicKey] (RSA, SHA-256). Verifying that signature here guarantees that neither
 * the version-info JSON nor the installer URL/hash it points to was tampered with in transit or at
 * rest — closing the remote-code-execution path described in issue #484.
 *
 * The canonical message is built by [canonicalMessage] as:
 *
 *     <version>|<min_version>|<download_url>|<download_sha256>
 *
 * The release pipeline MUST produce byte-for-byte the same string (see
 * `.github/actions/create-latest-version-json/action.yml`) or verification will fail.
 */
class ReleaseMetadataVerifier(
    private val publicKey: String,
) {
    /**
     * Returns true only if [response] carries both a SHA-256 and a signature, and that signature is
     * valid for the embedded public key. Missing fields are treated as a verification failure so the
     * caller can reject unsigned metadata.
     */
    fun verify(response: LatestAppVersionJsonResponse): Boolean {
        val signature = response.downloadSignature ?: return false
        val sha256 = response.downloadSha256 ?: return false

        val message =
            canonicalMessage(
                version = response.version,
                minVersion = response.minVersion,
                downloadUrl = response.downloadUrl,
                downloadSha256 = sha256,
            )

        return verifySignature(message, signature)
    }

    private fun verifySignature(
        message: String,
        signatureBase64: String,
    ): Boolean =
        try {
            val keyFactory = KeyFactory.getInstance("RSA")
            val sanitizedKey =
                publicKey
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("\n", "")
                    .replace(" ", "")
            val keyBytes = Base64.getDecoder().decode(sanitizedKey)
            val parsedKey = keyFactory.generatePublic(X509EncodedKeySpec(keyBytes))

            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(parsedKey)
            verifier.update(message.toByteArray(Charsets.UTF_8))
            verifier.verify(Base64.getDecoder().decode(signatureBase64))
        } catch (_: GeneralSecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            // Thrown by Base64 decoding when the key or signature is not valid Base64.
            false
        }

    companion object {
        /**
         * Builds the canonical message that is signed by the release pipeline and verified here.
         * Keep this in sync with the signing step in the release pipeline.
         */
        fun canonicalMessage(
            version: String,
            minVersion: String,
            downloadUrl: String,
            downloadSha256: String,
        ): String = "$version|$minVersion|$downloadUrl|$downloadSha256"
    }
}
