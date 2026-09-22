package com.cereal.client.presentation.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.cereal.client.application.ApplicationConfig
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test

/**
 * Renders [AuthenticatedProfileScreen] with an [ApplicationConfig] and [ProfileViewModel] resolved
 * from the in-memory Koin graph, mirroring how [ProfileScreen] wires the screen in production.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class AuthenticatedProfileScreenTest {
    @Test
    fun rendersAuthenticatedSections() =
        runScreenTest {
            setScreenContent {
                val scope = rememberCoroutineScope()
                val applicationConfig = koinInject<ApplicationConfig>()
                val vm =
                    KoinJavaComponent.get<ProfileViewModel>(
                        ProfileViewModel::class.java,
                        parameters = { parametersOf(scope) },
                    )
                AuthenticatedProfileScreen(applicationConfig = applicationConfig, vm = vm)
            }

            onNodeWithText("Profile").assertIsDisplayed()
            onNodeWithText("View profile").assertIsDisplayed()
            onNodeWithText("Subscriptions").assertIsDisplayed()
            onNodeWithText("Logout").assertIsDisplayed()
        }
}
