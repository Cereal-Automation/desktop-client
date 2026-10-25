package com.cereal.client.application.interactor.script

import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryCustomDatasetRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import fixtures.VARIANT_FIELD
import fixtures.aScriptPackageWithVariant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetScriptConfigDefinitionInteractorVariantTest {
    private val interactor = GetScriptConfigDefinitionInteractor(InMemoryProxyRepository(), InMemoryCustomDatasetRepository())
    private val scriptPackage = aScriptPackageWithVariant("com.example", variantId = "checkout")

    @Test
    fun `a variant's configuration form shows the variant's own fields`() =
        runTest {
            val result = interactor.run(GetScriptConfigDefinitionInteractor.Params(scriptPackage, null, variantId = "checkout"))

            assertEquals(listOf(VARIANT_FIELD), result.mainConfiguration.map { it.definition.key })
        }

    @Test
    fun `the default entry's configuration form shows the main script's fields`() =
        runTest {
            val result = interactor.run(GetScriptConfigDefinitionInteractor.Params(scriptPackage, null))

            assertEquals(emptyList<String>(), result.mainConfiguration.map { it.definition.key })
        }
}
