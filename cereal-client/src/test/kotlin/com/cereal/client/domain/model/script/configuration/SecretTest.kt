package com.cereal.client.domain.model.script.configuration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The root guarantee every downstream mask depends on: if `toString` ever leaks the plaintext, the
 * task list, the configuration summary, and every log line built from a secret leak with it.
 */
class SecretTest {
    @Test
    fun `toString returns the mask and never the wrapped value`() {
        val secret = Secret("sk-live-super-sensitive")

        assertEquals(Secret.MASK, secret.toString())
        assertFalse(secret.toString().contains("sk-live"))
    }

    @Test
    fun `toString masks even an empty value so presence is never inferable from the mask`() {
        assertEquals(Secret.MASK, Secret("").toString())
    }

    @Test
    fun `string interpolation goes through toString and is therefore masked`() {
        val secret = Secret("sk-live-super-sensitive")

        assertEquals("token: ${Secret.MASK}", "token: $secret")
    }

    @Test
    fun `reveal returns the wrapped plaintext`() {
        assertEquals("sk-live-super-sensitive", Secret("sk-live-super-sensitive").reveal())
    }

    @Test
    fun `secrets wrapping the same value are equal and share a hash code`() {
        val a = Secret("token")
        val b = Secret("token")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `secrets wrapping different values are not equal`() {
        assertNotEquals(Secret("token"), Secret("other"))
    }

    @Test
    fun `a secret is not equal to the bare string it wraps`() {
        assertFalse(Secret("token").equals("token"))
    }

    @Test
    fun `is not a data class, so destructuring cannot hand out the plaintext`() {
        // A data class would generate component1()/copy() and a toString() containing the value,
        // which is exactly the accidental-leak path this type exists to close.
        assertFalse(Secret::class.isData)
        assertTrue(Secret::class.java.declaredMethods.none { it.name == "component1" })
    }
}
