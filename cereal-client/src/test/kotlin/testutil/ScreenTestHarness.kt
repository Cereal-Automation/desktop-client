package testutil

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.cereal.client.infrastructure.di.Injector
import com.cereal.client.infrastructure.di.modules.InMemoryProviderModule
import com.cereal.client.infrastructure.di.modules.InMemoryRepositoryModule
import com.cereal.client.presentation.theme.CerealTheme
import org.jetbrains.skiko.Library
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * Whether Compose desktop rendering can run here. Probes skiko's native library once: on headless
 * CI runners that lack the native graphics stack the load throws, and these tests are skipped
 * rather than failed. On developer machines it succeeds and the tests run for real.
 */
private val composeRenderingAvailable: Boolean by lazy {
    runCatching { Library.load() }.isSuccess
}

/**
 * Renders a single screen inside a Compose desktop UI test, wired to the real ViewModel/interactor
 * graph but with every repository replaced by an in-memory implementation (see
 * [InMemoryRepositoryModule]).
 *
 * Boots a fresh Koin context per test and tears it down afterwards. Screens resolve their own
 * ViewModel via `KoinJavaComponent.get(...)`, so simply having a started container is enough.
 *
 * Usage:
 * ```
 * @Test fun rendersToggles() = runScreenTest {
 *     setScreenContent { ApplicationSettingsScreen() }
 *     onNodeWithText("Notifications").assertExists()
 * }
 * ```
 */
@OptIn(ExperimentalTestApi::class)
fun runScreenTest(block: ComposeUiTest.() -> Unit) {
    assumeTrue(composeRenderingAvailable, "Compose desktop rendering is unavailable in this environment")
    runDesktopComposeUiTest {
        // Defend against a previous test that failed before tearing Koin down.
        stopKoin()
        startKoin { modules(Injector.appModules(InMemoryRepositoryModule.modules, InMemoryProviderModule.modules)) }
        try {
            block()
        } finally {
            stopKoin()
        }
    }
}

/** Sets the screen [content] wrapped in [CerealTheme], as the real app does. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.setScreenContent(content: @Composable () -> Unit) =
    setContent {
        CerealTheme { content() }
    }
