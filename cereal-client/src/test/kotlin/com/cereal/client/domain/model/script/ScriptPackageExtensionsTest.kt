package com.cereal.client.domain.model.script

import io.mockk.mockk
import net.swiftzer.semver.SemVer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ScriptPackageExtensionsTest {
    @Test
    fun `isClientUpdateRequired should return false when manifest has no sdkVersion`() {
        val scriptPackage = aScriptPackage(sdkVersion = null)

        val result = scriptPackage.isClientUpdateRequired(SemVer(1, 0, 0))

        assertFalse(result)
    }

    @Test
    fun `isClientUpdateRequired should return true when required major is higher`() {
        val scriptPackage = aScriptPackage(sdkVersion = "2.0.0")

        val result = scriptPackage.isClientUpdateRequired(SemVer(1, 0, 0))

        assertTrue(result)
    }

    @Test
    fun `isClientUpdateRequired should return false when installed major is higher`() {
        val scriptPackage = aScriptPackage(sdkVersion = "1.0.0")

        val result = scriptPackage.isClientUpdateRequired(SemVer(2, 0, 0))

        assertFalse(result)
    }

    @Test
    fun `isClientUpdateRequired should return true when required minor is higher`() {
        val scriptPackage = aScriptPackage(sdkVersion = "1.3.0")

        val result = scriptPackage.isClientUpdateRequired(SemVer(1, 2, 0))

        assertTrue(result)
    }

    @Test
    fun `isClientUpdateRequired should return false when installed minor is higher`() {
        val scriptPackage = aScriptPackage(sdkVersion = "1.2.0")

        val result = scriptPackage.isClientUpdateRequired(SemVer(1, 3, 0))

        assertFalse(result)
    }

    @Test
    fun `isClientUpdateRequired should return false when required patch is higher but major and minor match`() {
        val scriptPackage = aScriptPackage(sdkVersion = "1.2.9")

        val result = scriptPackage.isClientUpdateRequired(SemVer(1, 2, 0))

        assertFalse(result)
    }

    @Test
    fun `isClientUpdateRequired should return false when installed patch is higher but major and minor match`() {
        val scriptPackage = aScriptPackage(sdkVersion = "1.2.0")

        val result = scriptPackage.isClientUpdateRequired(SemVer(1, 2, 9))

        assertFalse(result)
    }

    @Test
    fun `isClientUpdateRequired should return false when versions are identical`() {
        val scriptPackage = aScriptPackage(sdkVersion = "1.2.3")

        val result = scriptPackage.isClientUpdateRequired(SemVer(1, 2, 3))

        assertFalse(result)
    }

    @Test
    fun `isClientUpdateRequired should return true when minor is higher regardless of patch`() {
        val scriptPackage = aScriptPackage(sdkVersion = "1.3.0")

        val result = scriptPackage.isClientUpdateRequired(SemVer(1, 2, 99))

        assertTrue(result)
    }

    private fun aScriptPackage(sdkVersion: String?) =
        ScriptPackage(
            source = File("/tmp/fake.jar"),
            manifest =
                Manifest(
                    packageName = "com.example.script",
                    name = "Test Script",
                    versionCode = 1L,
                    sdkVersion = sdkVersion,
                ),
            mainScript = mockk(),
            childScripts = emptyMap(),
        )
}
