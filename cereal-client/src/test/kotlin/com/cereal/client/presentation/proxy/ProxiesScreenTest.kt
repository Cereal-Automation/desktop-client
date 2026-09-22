package com.cereal.client.presentation.proxy

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test

/**
 * Renders [ProxiesScreen] on in-memory repositories. Proves the harness generalises to a screen
 * whose ViewModel is resolved with a `parametersOf(scope)` argument and which is backed by the
 * in-memory `InMemoryProxyRepository`.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class ProxiesScreenTest {
    @Test
    fun rendersProxiesHeader() =
        runScreenTest {
            setScreenContent { ProxiesScreen() }

            onNodeWithText("Proxies").assertIsDisplayed()
        }

    @Test
    fun showsProvidersSectionWhenFeatureFlagEnabled() =
        runScreenTest {
            setScreenContent { ProxiesScreen(proxyProviderEnabled = true) }

            // The "Providers" section header renders uppercased, so match case-insensitively.
            onNodeWithText("Providers", substring = true, ignoreCase = true).assertExists()
        }

    @Test
    fun hidesProxyProviderFeatureWhenFeatureFlagDisabled() =
        runScreenTest {
            setScreenContent { ProxiesScreen(proxyProviderEnabled = false) }

            // The screen still renders its manual proxy-group UI...
            onNodeWithText("Proxies").assertIsDisplayed()
            // ...but the gated proxy-provider "Providers" section is absent.
            onNodeWithText("Providers", substring = true, ignoreCase = true).assertDoesNotExist()
        }
}
