package com.cereal.client.presentation.view

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ClickableUrlTextTest {
    private class RecordingUriHandler : UriHandler {
        val opened = mutableListOf<String>()

        override fun openUri(uri: String) {
            opened += uri
        }
    }

    private fun ComposeUiTest.render(text: String): RecordingUriHandler {
        val handler = RecordingUriHandler()
        setScreenContent {
            CompositionLocalProvider(LocalUriHandler provides handler) { ClickableUrlText(text) }
        }
        return handler
    }

    @Test
    fun `a markdown link renders as its text and opens its target`() =
        runScreenTest {
            val handler = render("[Anthropic](https://x.test/a)")

            onNodeWithText("Anthropic").performClick()

            assertEquals(listOf("https://x.test/a"), handler.opened)
        }

    @Test
    fun `a markdown link inside a sentence shows only its text`() =
        runScreenTest {
            render("Get a key: [Anthropic](https://x.test/a).")

            onNodeWithText("Get a key: Anthropic.").assertExists()
        }

    @Test
    fun `a markdown link to a non-web target is left as plain text`() =
        runScreenTest {
            val handler = render("[secrets](file:///etc/passwd)")

            onNodeWithText("[secrets](file:///etc/passwd)").performClick()

            assertEquals(emptyList(), handler.opened)
        }

    @Test
    fun `a bare url does not swallow trailing punctuation`() =
        runScreenTest {
            val handler = render("https://example.com/a).")

            onNodeWithText("https://example.com/a).").performClick()

            assertEquals(listOf("https://example.com/a"), handler.opened)
        }

    @Test
    fun `a bare url keeps a comma in its query string`() =
        runScreenTest {
            val handler = render("https://example.com/q?ids=1,2&sort=asc")

            onNodeWithText("https://example.com/q?ids=1,2&sort=asc").performClick()

            assertEquals(listOf("https://example.com/q?ids=1,2&sort=asc"), handler.opened)
        }

    @Test
    fun `a bare url keeps a closing parenthesis that balances an inner one`() =
        runScreenTest {
            val handler = render("https://en.wikipedia.org/wiki/Foo_(bar).")

            onNodeWithText("https://en.wikipedia.org/wiki/Foo_(bar).").performClick()

            assertEquals(listOf("https://en.wikipedia.org/wiki/Foo_(bar)"), handler.opened)
        }

    @Test
    fun `plain text renders unchanged and opens nothing`() =
        runScreenTest {
            val handler = render("Just a description, no links.")

            onNodeWithText("Just a description, no links.").performClick()

            assertEquals(emptyList(), handler.opened)
        }
}
