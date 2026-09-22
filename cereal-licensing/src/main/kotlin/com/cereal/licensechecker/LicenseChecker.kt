package com.cereal.licensechecker

import com.cereal.sdk.component.license.HttpResponse
import com.cereal.sdk.component.license.LicenseComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

sealed class LicenseState {
    data object Licensed : LicenseState()

    data object Unlicensed : LicenseState()

    class ErrorValidatingLicense(
        val message: String,
    ) : LicenseState()
}

/**
 * The capacity a license entitles the holder to, parsed from the server's `capacity`/`capacity_unit`
 * fields. `capacity_unit` names what is being capped (e.g. `"records"`); it is only meaningful when the
 * response is [LicenseState.Licensed]. The three variants make the server's nullable pair unambiguous:
 * a bare cap with no unit is a contradiction the contract forbids, so it is normalised to [None].
 */
sealed class LicenseCapacity {
    /** No capacity concept: `capacity_unit` was absent or null (a free / single-price script). */
    data object None : LicenseCapacity()

    /** A tiered script with no cap: `capacity_unit` present, `capacity` null. */
    data class Unlimited(
        val unit: String,
    ) : LicenseCapacity()

    /** A tiered script entitled to at most [amount] [unit]. */
    data class Limited(
        val amount: Int,
        val unit: String,
    ) : LicenseCapacity()
}

/**
 * The full outcome of a license check: the [state] plus the entitled [capacity]. [capacity] is always
 * [LicenseCapacity.None] unless [state] is [LicenseState.Licensed] — the contract only ascribes meaning
 * to capacity when `licensed == true`.
 */
data class LicenseResult(
    val state: LicenseState,
    val capacity: LicenseCapacity,
)

// Salt is shared across all LicenseChecker instances so that the checkScriptLicense cache
// can be hit regardless of how many instances are created. Generated once per JVM lifetime
// using SecureRandom to ensure the value is cryptographically unpredictable.
private val salt = getRandomString(32)

private const val MILLIS_PER_SECOND = 1000

class LicenseChecker(
    private val scriptId: String,
    private val publicKey: String,
    private val licenseComponent: LicenseComponent,
    // Epoch-second clock, injectable so the freshness check can be tested deterministically.
    private val currentTimeSeconds: () -> Long = { System.currentTimeMillis() / MILLIS_PER_SECOND },
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Verifies the script's license and returns the resulting [LicenseState]. Retained unchanged for
     * callers that only care about access; [checkLicense] additionally surfaces the entitled capacity.
     */
    suspend fun checkAccess(): LicenseState = checkLicense().state

    /**
     * Verifies the script's license and returns both the [LicenseState] and the entitled
     * [LicenseCapacity]. The capacity fields are additive: an older server that omits them yields
     * [LicenseCapacity.None], exactly as if the script had no capacity concept.
     */
    suspend fun checkLicense(): LicenseResult {
        try {
            val response = licenseComponent.checkScriptLicense(scriptId, salt)
            response.use {
                if (!response.isSuccessful) {
                    return LicenseResult(
                        LicenseState.ErrorValidatingLicense(
                            "Bad response received from server, status code: ${response.code}",
                        ),
                        LicenseCapacity.None,
                    )
                }

                return try {
                    if (!verifySignature(salt, response)) {
                        return LicenseResult(LicenseState.Unlicensed, LicenseCapacity.None)
                    }
                    val body = json.parseToJsonElement(response.body().decodeToString()).jsonObject
                    if (isLicensed(body) && isFresh(body)) {
                        LicenseResult(LicenseState.Licensed, parseCapacity(body))
                    } else {
                        LicenseResult(LicenseState.Unlicensed, LicenseCapacity.None)
                    }
                } catch (e: IOException) {
                    LicenseResult(
                        LicenseState.ErrorValidatingLicense("Unable to read license response: ${e.message}"),
                        LicenseCapacity.None,
                    )
                }
            }
        } catch (e: IOException) {
            return LicenseResult(
                LicenseState.ErrorValidatingLicense("Unable to connect to server: ${e.message}"),
                LicenseCapacity.None,
            )
        }
    }

    private fun verifySignature(
        salt: String,
        response: HttpResponse,
    ): Boolean {
        val responseSignature = response.header("X-Script-Signature", null) ?: return false

        val keyFactory = KeyFactory.getInstance("RSA")

        val realPublicKey: String =
            publicKey
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("\n", "")
        val pubKey: ByteArray = Base64.getDecoder().decode(realPublicKey)
        val specPub = X509EncodedKeySpec(pubKey)
        val publicKey: PublicKey = keyFactory.generatePublic(specPub)

        val signature = Signature.getInstance("SHA256withRSA")

        signature.initVerify(publicKey)
        // peekBody is used to read the content because: https://github.com/square/okhttp/issues/1240
        signature.update(salt.toByteArray() + Base64.getEncoder().encode(response.body()))

        return signature.verify(Base64.getDecoder().decode(responseSignature))
    }

    private fun isLicensed(body: JsonObject): Boolean = body["licensed"]?.jsonPrimitive?.booleanOrNull ?: false

    /**
     * Parses the additive `capacity`/`capacity_unit` pair. Only called once the response is confirmed
     * licensed, so the values carry meaning. `capacity_unit` drives the interpretation: absent or null
     * unit → [LicenseCapacity.None] (also covering a stray `capacity` with no unit, which the contract
     * forbids); a non-null unit with a null `capacity` → [LicenseCapacity.Unlimited]; a non-null unit
     * with an integer `capacity` → [LicenseCapacity.Limited].
     */
    private fun parseCapacity(body: JsonObject): LicenseCapacity {
        val unit = body["capacity_unit"]?.jsonPrimitive?.contentOrNull ?: return LicenseCapacity.None
        val amount = body["capacity"]?.jsonPrimitive?.intOrNull
        return if (amount == null) {
            LicenseCapacity.Unlimited(unit)
        } else {
            LicenseCapacity.Limited(amount, unit)
        }
    }

    /**
     * Enforces the server-issued freshness window so a captured `licensed:true` response cannot be
     * replayed offline forever. The server folds an `expires_at` (epoch seconds) into the signed
     * body; a response is fresh while the client's clock is at or before it.
     *
     * The field is additive: an older server that does not stamp a window omits `expires_at`, in
     * which case there is nothing to enforce and the response is treated as fresh (backwards
     * compatible). Because the check runs against the client's own clock it bounds — rather than
     * eliminates — replay, and can be sidestepped by rolling the clock back; the short server TTL
     * keeps that window small.
     */
    private fun isFresh(body: JsonObject): Boolean {
        val expiresAt = body["expires_at"]?.jsonPrimitive?.longOrNull ?: return true
        return currentTimeSeconds() <= expiresAt
    }
}
