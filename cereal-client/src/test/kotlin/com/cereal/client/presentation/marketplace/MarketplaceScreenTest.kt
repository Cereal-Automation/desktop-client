package com.cereal.client.presentation.marketplace

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test

/**
 * Renders [MarketplaceScreen] on the in-memory marketplace repository, which the harness seeds with
 * `SandboxSampleData.marketplaceCatalog`. Asserts the toolbar title plus a catalog script the
 * `InMemoryMarketplaceProvider` returns once the asynchronous first page resolves.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class MarketplaceScreenTest {
    @Test
    fun rendersMarketplaceTitleAndCatalogScripts() =
        runScreenTest {
            setScreenContent { MarketplaceScreen() }

            // The header is static; the grid is populated asynchronously after the first page loads.
            onNodeWithText("Marketplace").assertIsDisplayed()

            waitUntil(timeoutMillis = 5_000) {
                onAllNodes(hasText("Instagram Follower Bot")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Instagram Follower Bot").assertIsDisplayed()
        }
}
