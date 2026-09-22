package com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor

import com.cereal.client.application.exception.InvalidSignatureException
import com.cereal.client.infrastructure.data.datasource.network.diagnosticHeaders
import okhttp3.Interceptor
import okhttp3.Response
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class SignatureInterceptor(
    private val publicKey: String,
) : Interceptor {
    private val secureRandom = SecureRandom()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val salt = getRandomString(SALT_LENGTH)
        val newRequest =
            request
                .newBuilder()
                .header("X-Salt", salt)
                .build()

        val response = chain.proceed(newRequest)

        // Only 2xx responses carry data we act on, and only they pass through the backend's signing
        // middleware. Edge-generated errors (rate limits, WAF blocks, 502/503) never reach the signer,
        // so verifying them replaced the real HTTP status with a false tampering signal and hid the
        // actual failure from both the user and Sentry. Let the caller see the 429/5xx instead.
        //
        // Consequence: error *bodies* are unverified, so they must not drive security decisions —
        // MarketplaceApiClient only reads them for status mapping and user-facing messages. An on-path
        // attacker able to forge an error response could at worst force a logout or a retry, which it
        // could already achieve by resetting the connection — and certificate pinning blocks it anyway.
        if (!response.isSuccessful) {
            return response
        }

        val failureReason = verificationFailureReason(salt, response)
        if (failureReason != null) {
            // A dedicated exception type (rather than a plain IOException) so the interactor
            // error mapping reports it to Sentry instead of treating it as expected network noise.
            response.close()
            throw InvalidSignatureException(
                url = request.url.toString(),
                httpStatus = response.code,
                reason = failureReason,
                edgeHeaders = response.diagnosticHeaders(),
            )
        }

        return response
    }

    private fun getRandomString(length: Int): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars[secureRandom.nextInt(allowedChars.size)] }
            .joinToString("")
    }

    /** Returns `null` when the response verifies, otherwise an `InvalidSignatureException.REASON_*`. */
    private fun verificationFailureReason(
        salt: String,
        response: Response,
    ): String? {
        val responseSignature =
            response.header("X-Signature", null)
                ?: return InvalidSignatureException.REASON_HEADER_ABSENT

        val decodedSignature =
            try {
                Base64.getDecoder().decode(responseSignature)
            } catch (_: IllegalArgumentException) {
                // A malformed header is a verification failure, not a programming error. Letting the
                // IllegalArgumentException escape would break OkHttp's IOException contract and be
                // reported as an unexpected crash instead of a signature problem.
                return InvalidSignatureException.REASON_NOT_BASE64
            }

        return try {
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initVerify(parsePublicKey())
            // peekBody is used to read the content because: https://github.com/square/okhttp/issues/1240
            signature.update(salt.toByteArray() + Base64.getEncoder().encode(response.peekBody(Long.MAX_VALUE).bytes()))

            if (signature.verify(decodedSignature)) {
                null
            } else {
                InvalidSignatureException.REASON_MISMATCH
            }
        } catch (e: GeneralSecurityException) {
            // Signature.verify throws for improperly encoded signature bytes (e.g. wrong length), and
            // key parsing throws for a malformed configured key. Both are "could not verify", not
            // crashes — and neither may escape a non-IOException from an interceptor.
            "${InvalidSignatureException.REASON_CRYPTO_ERROR}:${e::class.simpleName}"
        }
    }

    private fun parsePublicKey(): PublicKey {
        val realPublicKey: String =
            publicKey
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("\n", "")
                .replace(" ", "")
        val specPub = X509EncodedKeySpec(Base64.getDecoder().decode(realPublicKey))
        return KeyFactory.getInstance("RSA").generatePublic(specPub)
    }

    private companion object {
        const val SALT_LENGTH = 32
    }
}
