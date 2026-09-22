package com.cereal.client.domain.model.task

import com.cereal.client.domain.model.exception.InvalidScriptPackageGroupException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ScriptPackageGroupTest {
    @Test
    fun `should create ScriptPackageGroup with valid data`() {
        assertDoesNotThrow {
            ScriptPackageGroup(
                id = "valid-id",
                name = "valid-name",
                totalScriptPackages = 5,
            )
        }
    }

    @Test
    fun `should throw exception when id is blank`() {
        assertThrows(InvalidScriptPackageGroupException::class.java) {
            ScriptPackageGroup(
                id = "",
                name = "valid-name",
                totalScriptPackages = 5,
            )
        }
    }

    @Test
    fun `should throw exception when name is blank`() {
        assertThrows(InvalidScriptPackageGroupException::class.java) {
            ScriptPackageGroup(
                id = "valid-id",
                name = "   ",
                totalScriptPackages = 5,
            )
        }
    }

    @Test
    fun `should throw exception when name is longer than 50 characters`() {
        assertThrows(InvalidScriptPackageGroupException::class.java) {
            ScriptPackageGroup(
                id = "valid-id",
                name = "a".repeat(51),
                totalScriptPackages = 5,
            )
        }
    }

    @Test
    fun `should create ScriptPackageGroup with valid characters including special ones`() {
        assertDoesNotThrow {
            ScriptPackageGroup(
                id = "valid-id",
                name = "Valid Name @.123",
                totalScriptPackages = 5,
            )
        }
    }

    @Test
    fun `should throw exception when name contains invalid characters`() {
        assertThrows(InvalidScriptPackageGroupException::class.java) {
            ScriptPackageGroup(
                id = "valid-id",
                name = "Invalid!Name",
                totalScriptPackages = 5,
            )
        }
    }

    @Test
    fun `should throw exception when totalScriptPackages is negative`() {
        assertThrows(InvalidScriptPackageGroupException::class.java) {
            ScriptPackageGroup(
                id = "valid-id",
                name = "valid-name",
                totalScriptPackages = -1,
            )
        }
    }
}
