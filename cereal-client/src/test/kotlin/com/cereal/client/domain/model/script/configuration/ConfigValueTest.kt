@file:OptIn(ExperimentalTime::class)

package com.cereal.client.domain.model.script.configuration

import com.cereal.client.domain.model.exception.UnsupportedConfigurationTypeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ConfigValueTest {
    private enum class SampleColor { RED, GREEN }

    @Test
    fun `toConfigValue wraps a Boolean`() {
        assertEquals(ConfigValue.BooleanValue(true), true.toConfigValue())
    }

    @Test
    fun `toConfigValue wraps a String`() {
        assertEquals(ConfigValue.StringValue("hello"), "hello".toConfigValue())
    }

    @Test
    fun `toConfigValue wraps a Secret as a SecretValue, not a StringValue`() {
        assertEquals(ConfigValue.SecretValue(Secret("token")), Secret("token").toConfigValue())
    }

    @Test
    fun `a SecretValue's own toString masks the credential`() {
        // SecretValue is a data class, so its generated toString includes `raw` — which is a Secret,
        // and therefore masks. A whole-configuration debug dump cannot leak through this variant.
        val rendered = ConfigValue.SecretValue(Secret("sk-live-token")).toString()

        assertTrue(rendered.contains(Secret.MASK), rendered)
        assertFalse(rendered.contains("sk-live-token"), rendered)
    }

    @Test
    fun `toConfigValue wraps an Int`() {
        assertEquals(ConfigValue.IntValue(42), 42.toConfigValue())
    }

    @Test
    fun `toConfigValue wraps a Long`() {
        assertEquals(ConfigValue.LongValue(42L), 42L.toConfigValue())
    }

    @Test
    fun `toConfigValue wraps a Short`() {
        val short: Short = 7
        assertEquals(ConfigValue.ShortValue(short), short.toConfigValue())
    }

    @Test
    fun `toConfigValue wraps a Float`() {
        assertEquals(ConfigValue.FloatValue(1.5f), 1.5f.toConfigValue())
    }

    @Test
    fun `toConfigValue wraps a Double`() {
        assertEquals(ConfigValue.DoubleValue(2.5), 2.5.toConfigValue())
    }

    @Test
    fun `toConfigValue wraps an Instant`() {
        val instant = Instant.fromEpochMilliseconds(1_000)
        assertEquals(ConfigValue.InstantValue(instant), instant.toConfigValue())
    }

    @Test
    fun `toConfigValue wraps a File`() {
        val file = File("/tmp/example.txt")
        assertEquals(ConfigValue.FileValue(file), file.toConfigValue())
    }

    @Test
    fun `toConfigValue wraps an Enum`() {
        assertEquals(ConfigValue.EnumValue(SampleColor.RED), SampleColor.RED.toConfigValue())
    }

    @Test
    fun `toConfigValue throws for a raw List`() {
        // A list configuration item's value arrives as ListRows; a bare list is unsupported rather
        // than something to coerce into a list of strings.
        assertThrows(UnsupportedConfigurationTypeException::class.java) {
            listOf("a", "b").toConfigValue()
        }
    }

    @Test
    fun `toConfigValue throws for an unsupported type`() {
        val unsupported = Any()
        assertThrows(UnsupportedConfigurationTypeException::class.java) {
            unsupported.toConfigValue()
        }
    }
}
