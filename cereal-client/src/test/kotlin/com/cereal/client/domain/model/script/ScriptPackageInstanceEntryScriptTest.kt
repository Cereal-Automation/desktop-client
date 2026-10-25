package com.cereal.client.domain.model.script

import fixtures.aScriptPackageInstance
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ScriptPackageInstanceEntryScriptTest {
    @Test
    fun `entry script of an instance without a variant is the package's main script`() {
        val instance = aScriptPackageInstance(id = "1", packageName = "com.example")

        assertSame(instance.definition.mainScript, instance.entryScript)
    }
}
