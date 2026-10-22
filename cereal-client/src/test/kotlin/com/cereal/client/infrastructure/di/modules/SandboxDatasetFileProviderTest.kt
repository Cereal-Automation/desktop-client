package com.cereal.client.infrastructure.di.modules

import com.cereal.client.domain.model.datasets.DatasetType
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.provider.DatasetFileProvider
import com.cereal.client.infrastructure.di.Injector
import fixtures.anAgentItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.dsl.koinApplication
import java.io.File

/**
 * The `mock` flavor still opens real native file dialogs, so downloading an import template must write
 * a real file. Resolves the provider from the exact mock-flavor module list [Injector] builds.
 */
class SandboxDatasetFileProviderTest {
    @Test
    fun `mock flavor writes the csv template to the chosen file`(
        @TempDir dir: File,
    ) {
        val koin =
            koinApplication {
                modules(
                    Injector.appModules(InMemoryRepositoryModule.modules, InMemoryProviderModule.modules) +
                        SandboxRepositoryModule.modules,
                )
            }.koin
        val type =
            DatasetType.Custom(
                listOf(
                    anAgentItem("email", ConfigItemType.StringConfigItem, position = 0),
                    anAgentItem("password", ConfigItemType.StringConfigItem, position = 1),
                ),
            )

        koin.get<DatasetFileProvider>().saveTemplate(type, File(dir, "template"))

        assertEquals("email,password", File(dir, "template.csv").readText().trim())
    }
}
