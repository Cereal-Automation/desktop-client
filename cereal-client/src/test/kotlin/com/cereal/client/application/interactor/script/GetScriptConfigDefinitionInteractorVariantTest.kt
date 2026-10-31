package com.cereal.client.application.interactor.script

import com.cereal.client.domain.model.exception.UnknownScriptVariantException
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryCustomDatasetRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import fixtures.VARIANT_FIELD
import fixtures.aScriptPackageWithVariant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class GetScriptConfigDefinitionInteractorVariantTest {
    private val interactor = GetScriptConfigDefinitionInteractor(InMemoryProxyRepository(), InMemoryCustomDatasetRepository())
    private val scriptPackage = aScriptPackageWithVariant("com.example", variantId = "checkout")

    @Test
    fun `run should return the variant's own fields when a variant is requested`() =
        runTest {
            val result = interactor.run(GetScriptConfigDefinitionInteractor.Params(scriptPackage, null, variantId = "checkout"))

            assertEquals(listOf(VARIANT_FIELD), result.mainConfiguration.map { it.definition.key })
        }

    @Test
    fun `run should fail when no variant is requested from a package with variants`() =
        runTest {
            assertFailsWith<UnknownScriptVariantException> {
                interactor.run(GetScriptConfigDefinitionInteractor.Params(scriptPackage, null))
            }
        }
}
