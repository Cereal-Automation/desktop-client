package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.application.exception.InvalidScriptConfigurationDefinitionException
import com.cereal.client.domain.model.script.configuration.ConfigSection
import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.ScriptConfigurationItem
import com.cereal.sdk.ScriptConfigurationSections
import com.cereal.sdk.Section
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.KClass

/** Seam: the script configuration definition builder, fed configuration interface fixtures. */
class ScriptConfigurationDefinitionBuilderSectionTest {
    private val builder = ScriptConfigurationDefinitionBuilder()

    @ScriptConfigurationSections(Section("login", "Login"), Section("adv", "Advanced", collapsed = true))
    interface SectionedConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "plain", name = "Plain", description = "", position = 0)
        fun plain(): String

        @ScriptConfigurationItem(keyName = "user", name = "User", description = "", position = 1, section = "login")
        fun user(): String

        @ScriptConfigurationItem(keyName = "retries", name = "Retries", description = "", position = 2, section = "adv")
        fun retries(): Int
    }

    @ScriptConfigurationSections(Section("login", "Login"))
    interface UndeclaredSectionConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "debug", name = "Debug", description = "", section = "troubleshooting")
        fun debug(): Boolean
    }

    @ScriptConfigurationSections(Section("login", "Login"), Section("login", "Login again"))
    interface DuplicateSectionConfig : ScriptConfiguration {
        @ScriptConfigurationItem(keyName = "user", name = "User", description = "", section = "login")
        fun user(): String
    }

    @Suppress("UNCHECKED_CAST")
    private fun createFrom(configuration: KClass<*>) = builder.createFrom(configuration as KClass<ScriptConfiguration>)

    @Test
    fun `createFrom carries each field's declared section`() {
        val sections = createFrom(SectionedConfig::class).configurationItems.associate { it.key to it.section }

        assertNull(sections["plain"])
        assertEquals(ConfigSection("login", "Login", collapsed = false, order = 0), sections["user"])
        assertEquals(ConfigSection("adv", "Advanced", collapsed = true, order = 1), sections["retries"])
    }

    @Test
    fun `createFrom rejects a field naming an undeclared section`() {
        val error = assertThrows<InvalidScriptConfigurationDefinitionException> { createFrom(UndeclaredSectionConfig::class) }

        assertTrue(error.message!!.contains("troubleshooting"))
        assertTrue(error.message!!.contains("Debug"))
    }

    @Test
    fun `createFrom rejects a duplicate section key`() {
        val error = assertThrows<InvalidScriptConfigurationDefinitionException> { createFrom(DuplicateSectionConfig::class) }

        assertTrue(error.message!!.contains("login"))
    }
}
