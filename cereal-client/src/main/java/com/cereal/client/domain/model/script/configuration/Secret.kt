package com.cereal.client.domain.model.script.configuration

/**
 * A credential the user supplied for a configuration item — an API key, a session token, a webhook
 * secret.
 *
 * The domain owns this type rather than reusing the SDK's `com.cereal.sdk.models.Secret`, so the
 * masking invariant is enforced here and cannot be weakened by an external artifact. It is converted
 * to the SDK type at the execution boundary, exactly as [com.cereal.client.domain.model.proxy.Proxy]
 * is converted to the SDK proxy today.
 *
 * Reaching the plaintext requires an explicit [reveal] call, so every unwrapping site is findable
 * with a single search. Everything built from [toString] — interpolated status messages, the task
 * list, whole-configuration debug dumps — sees a fixed mask instead.
 *
 * **What this does not do.** It is not what encrypts the value: every configuration value is already
 * stored encrypted at rest regardless of type (see `docs/adr/0001-encrypted-data-format-contract.md`).
 * `Secret` controls where a value may *appear*, not whether it is encrypted.
 *
 * @param value the plaintext credential.
 */
class Secret(
    private val value: String,
) {
    /**
     * Returns the plaintext credential.
     *
     * Deliberately a distinct verb rather than a property, so that every unwrapping site is greppable.
     */
    fun reveal(): String = value

    /** Returns a fixed mask. Never returns the wrapped value. */
    override fun toString(): String = MASK

    /**
     * Value-based equality.
     *
     * Deliberately not constant-time: the type is used for local configuration change detection, not
     * for authenticating a credential, so timing-safe comparison would be ceremony without a threat.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Secret) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    companion object {
        /**
         * The mask every secret renders as. Public so tests and any future display code assert
         * against one constant rather than a repeated literal.
         */
        const val MASK: String = "***"
    }
}
