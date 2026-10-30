package com.cereal.client.presentation.tasks.script.overview

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAuthProvider
import fixtures.aScriptPackage
import fixtures.aScriptPackageWithVariant
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import testutil.runScreenTest
import testutil.setScreenContent

/**
 * Renders the real [ScriptSelectionDialog] on in-memory repositories to check the entry picker: a package's variants
 * are offered in the configuration pane and picking one opens its form.
 */
@OptIn(ExperimentalTestApi::class)
class ScriptEntryPickerScreenTest {
    @Test
    fun `picking a variant in the picker opens that variant's form`() =
        runPickerScreenTest(aScriptPackageWithVariant(PACKAGE_NAME, variantId = "checkout", variantName = "Checkout")) {
            waitForText("2 available")

            onNodeWithText("Variant field").assertDoesNotExist()
            onNodeWithContentDescription("Workflow").performClick()
            onNodeWithText("Checkout").performClick()

            waitForText("Variant field")
            onNodeWithText("Variant field").assertIsDisplayed()
        }

    @Test
    fun `a package without variants shows no picker`() =
        runPickerScreenTest(aScriptPackage(PACKAGE_NAME)) {
            waitForText("Notification Overrides")
            onNodeWithText("available", substring = true).assertDoesNotExist()
        }

    private fun runPickerScreenTest(
        scriptPackage: ScriptPackage,
        assertions: ComposeUiTest.() -> Unit,
    ) {
        val named = scriptPackage.copy(manifest = scriptPackage.manifest.copy(name = "Agent"))
        val overrides =
            module {
                single<AuthProvider> { InMemoryAuthProvider(subscriptions = listOf(subscription(PACKAGE_NAME))) }
                single<ScriptRepository> { InMemoryScriptRepository().apply { seed(listOf(named)) } }
            }
        runScreenTest(listOf(overrides)) {
            setScreenContent {
                ScriptSelectionDialog(
                    scriptPackageGroup = ScriptPackageGroup("group-1", "Group 1"),
                    initialRequest = ScriptRequest(PACKAGE_NAME),
                    onScriptInstanceCreated = {},
                    onDismissRequest = {},
                )
            }
            assertions()
        }
    }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

    private companion object {
        private const val PACKAGE_NAME = "com.demo.variants"

        private fun subscription(publicId: String) =
            Subscription(
                id = "sub-$publicId",
                entitlement =
                    ScriptEntitlement(
                        publicIdentifier = publicId,
                        title = publicId,
                        latestRelease = null,
                        latestDraftRelease = null,
                        shortDescription = null,
                        price = null,
                        capacity = ScriptCapacity.None,
                    ),
            )
    }
}
