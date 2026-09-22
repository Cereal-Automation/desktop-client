package com.cereal.client.domain.model.script

import com.cereal.client.domain.model.exception.InvalidScriptEntitlementException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class ScriptEntitlementTest {
    private fun entitlement(
        publicIdentifier: String = "com.example.script",
        title: String = "Example",
        price: BigDecimal? = null,
        capacity: ScriptCapacity = ScriptCapacity.None,
    ) = ScriptEntitlement(
        publicIdentifier = publicIdentifier,
        title = title,
        latestRelease = null,
        latestDraftRelease = null,
        shortDescription = null,
        price = price,
        capacity = capacity,
    )

    @Test
    fun `throws when publicIdentifier is blank`() {
        val exception = assertThrows<InvalidScriptEntitlementException> { entitlement(publicIdentifier = " ") }
        assertEquals("publicIdentifier cannot be blank", exception.message)
    }

    @Test
    fun `throws when title is blank`() {
        val exception = assertThrows<InvalidScriptEntitlementException> { entitlement(title = "") }
        assertEquals("title cannot be blank", exception.message)
    }

    @Test
    fun `throws when price is negative`() {
        val exception = assertThrows<InvalidScriptEntitlementException> { entitlement(price = BigDecimal("-0.01")) }
        assertEquals("price cannot be negative", exception.message)
    }

    @Test
    fun `allows a zero price`() {
        assertEquals(BigDecimal.ZERO, entitlement(price = BigDecimal.ZERO).price)
    }

    @Test
    fun `isCapacityLimited is true only for a Limited grant`() {
        assertTrue(entitlement(capacity = ScriptCapacity.Limited(10, "records")).isCapacityLimited)
        assertFalse(entitlement(capacity = ScriptCapacity.Unlimited("records")).isCapacityLimited)
        assertFalse(entitlement(capacity = ScriptCapacity.None).isCapacityLimited)
    }

    @Test
    fun `allows always permits when capacity is None or Unlimited`() {
        assertTrue(entitlement(capacity = ScriptCapacity.None).allows(Int.MAX_VALUE))
        assertTrue(entitlement(capacity = ScriptCapacity.Unlimited("records")).allows(Int.MAX_VALUE))
    }

    @Test
    fun `allows permits a Limited run up to and including the cap`() {
        val grant = entitlement(capacity = ScriptCapacity.Limited(100, "records"))
        assertTrue(grant.allows(99))
        assertTrue(grant.allows(100))
    }

    @Test
    fun `allows refuses a Limited run above the cap`() {
        assertFalse(entitlement(capacity = ScriptCapacity.Limited(100, "records")).allows(101))
    }

    @Test
    fun `remainingCapacity is null when there is no finite cap`() {
        assertNull(entitlement(capacity = ScriptCapacity.None).remainingCapacity(5))
        assertNull(entitlement(capacity = ScriptCapacity.Unlimited("records")).remainingCapacity(5))
    }

    @Test
    fun `remainingCapacity subtracts used records from a Limited cap`() {
        assertEquals(70, entitlement(capacity = ScriptCapacity.Limited(100, "records")).remainingCapacity(30))
    }

    @Test
    fun `remainingCapacity clamps to zero when used records exceed the cap`() {
        assertEquals(0, entitlement(capacity = ScriptCapacity.Limited(100, "records")).remainingCapacity(150))
    }
}
