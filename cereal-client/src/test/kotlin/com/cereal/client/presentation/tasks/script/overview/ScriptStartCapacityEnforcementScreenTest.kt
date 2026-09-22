package com.cereal.client.presentation.tasks.script.overview

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.domain.repository.CustomDatasetRepository
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryCustomDatasetRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import com.cereal.client.infrastructure.di.Injector
import com.cereal.client.infrastructure.di.modules.InMemoryProviderModule
import com.cereal.client.infrastructure.di.modules.InMemoryRepositoryModule
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAuthProvider
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.sdk.ScriptConfiguration
import fixtures.aScriptPackage
import org.jetbrains.skiko.Library
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Drives the real script start flow end-to-end through [ScriptSelectionDialog] on the in-memory graph:
 * an installed script entitled to a `Limited` record capacity, configured with an over-limit dataset,
 * is refused at start with the [com.cereal.client.application.interactor.script.CapacityExceededException]
 * message surfaced by the `ErrorResolver` (ticket #741). Self-skips where skiko is unavailable.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalTime::class)
class ScriptStartCapacityEnforcementScreenTest {
    @Test
    fun `starting an over-limit run is blocked with the capacity message`() {
        assumeTrue(runCatching { Library.load() }.isSuccess, "Compose desktop rendering unavailable")

        val authProvider = InMemoryAuthProvider(subscriptions = listOf(subscription(PACKAGE, ScriptCapacity.Limited(100, "records"))))
        val scriptRepository = InMemoryScriptRepository().apply { seed(listOf(scriptWithDataset())) }
        val datasetRepository = InMemoryCustomDatasetRepository(initialGroups = listOf(bigDataset()))
        val overrides =
            module {
                single<AuthProvider> { authProvider }
                single<ScriptRepository> { scriptRepository }
                single<CustomDatasetRepository> { datasetRepository }
            }

        runDesktopComposeUiTest {
            stopKoin()
            startKoin { modules(Injector.appModules(InMemoryRepositoryModule.modules, InMemoryProviderModule.modules) + overrides) }
            try {
                setContent {
                    CerealTheme {
                        ScriptSelectionDialog(
                            scriptPackageGroup =
                                com.cereal.client.domain.model.task
                                    .ScriptPackageGroup("group-1", "Group 1"),
                            initialPublicIdentifier = PACKAGE,
                            onScriptInstanceCreated = {},
                            onDismissRequest = {},
                            onNavigateToMarketplace = {},
                        )
                    }
                }

                // The config pane renders once the script auto-selects.
                waitForText(DATASET_FIELD_LABEL)
                // Open the dataset dropdown (the editable field, not the section title of the same name)
                // and pick the over-limit (150-record) group.
                onNode(hasText(DATASET_FIELD_LABEL) and hasSetTextAction()).performClick()
                waitForText(DATASET_NAME)
                onNodeWithText(DATASET_NAME).performClick()

                // Start the run.
                onNodeWithContentDescription("Start task").performClick()
                // No notification channels are configured in the in-memory graph, so accept that warning first.
                waitForText("Continue Anyway")
                onNodeWithText("Continue Anyway").performClick()

                // The run is refused: the capacity message is shown, not a started instance.
                waitForText("your plan allows 100", substring = true)
                onNodeWithText("your plan allows 100", substring = true).assertExists()
            } finally {
                stopKoin()
            }
        }
    }

    private fun ComposeUiTest.waitForText(
        text: String,
        substring: Boolean = false,
    ) = waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun scriptWithDataset(): ScriptPackage {
        val base = aScriptPackage(PACKAGE)
        return base.copy(
            mainScript =
                MainScript(
                    clazz = base.mainScript.clazz,
                    configuration =
                        ScriptConfigurationDefinition(
                            scriptConfigurationClass = ScriptConfiguration::class,
                            configurationItems = listOf(datasetItemDef()),
                        ),
                ),
        )
    }

    private fun datasetItemDef() =
        ScriptConfigurationItemDefinition(
            name = DATASET_FIELD_LABEL,
            description = "The dataset to process",
            key = "dataset",
            position = 0,
            type = ConfigItemType.GroupedConfigItem(items = emptyList()),
            isNullable = false,
            stateModifier = null,
            isScriptIdentifier = false,
            defaultValue = null,
        )

    private fun bigDataset() =
        CustomDatasetGroup(
            id = "ds-big",
            name = DATASET_NAME,
            numberOfItems = 150,
            itemDefinitions = emptyList(),
            items = emptySequence(),
            createdAt = Instant.fromEpochMilliseconds(0),
        )

    private fun subscription(
        publicId: String,
        capacity: ScriptCapacity,
    ) = Subscription(
        id = "sub-$publicId",
        entitlement =
            ScriptEntitlement(
                publicIdentifier = publicId,
                title = publicId,
                latestRelease = null,
                latestDraftRelease = null,
                shortDescription = null,
                price = null,
                capacity = capacity,
            ),
    )

    private companion object {
        private const val PACKAGE = "com.demo.capacity"
        private const val DATASET_FIELD_LABEL = "Accounts"
        private const val DATASET_NAME = "Big dataset"
    }
}
