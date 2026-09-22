package com.cereal.client.presentation.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cereal.client.presentation.navigation.Root
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.marketplace
import com.cereal_automation.cereal_client.generated.resources.settings
import com.cereal_automation.cereal_client.generated.resources.tasks
import com.cereal_automation.cereal_client.generated.resources.your_profile
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.StringResource
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Renders [MainMenu] directly with deterministic [MenuItemUiModel] fixtures. Unlike [MainScreenTest]
 * this does not depend on the asynchronous authentication flow, so it can assert the static sidebar
 * structure (section headers, item labels, version chip, user card) and the click callback contract.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class MainMenuViewTest {
    private fun workspaceItem(
        route: Root.Routing,
        titleResource: StringResource,
    ) = MenuItemUiModel(
        route = route,
        externalUrl = null,
        resourcePath = IconSource.Vector(Icons.Filled.Bolt),
        titleResource = titleResource,
        titleOverride = null,
        selected = false,
        mainItem = true,
    )

    private val menuFixture =
        persistentListOf(
            MenuItemUiModel(
                route = Root.Routing.Tasks(),
                externalUrl = null,
                resourcePath = IconSource.Vector(Icons.Filled.Bolt),
                titleResource = Res.string.tasks,
                titleOverride = null,
                selected = true,
                mainItem = true,
            ),
            workspaceItem(Root.Routing.Marketplace, Res.string.marketplace),
            MenuItemUiModel(
                route = Root.Routing.Profile,
                externalUrl = null,
                resourcePath = IconSource.Vector(Icons.Filled.VerifiedUser),
                titleResource = Res.string.your_profile,
                titleOverride = "Ada Lovelace",
                selected = false,
                mainItem = false,
            ),
            MenuItemUiModel(
                route = Root.Routing.ApplicationSettings,
                externalUrl = null,
                resourcePath = IconSource.Vector(Icons.Filled.Settings),
                titleResource = Res.string.settings,
                titleOverride = null,
                selected = false,
                mainItem = false,
            ),
        )

    @Test
    fun rendersSectionsItemsAndUserCard() =
        runScreenTest {
            setScreenContent {
                MainMenu(
                    menuItems = menuFixture,
                    appVersion = "1.2.3",
                    activeScriptCount = 2,
                    onClick = {},
                )
            }

            // Section labels are uppercased by MenuSectionLabel.
            onNodeWithText("WORKSPACE").assertIsDisplayed()
            onNodeWithText("SYSTEM").assertIsDisplayed()

            // Workspace items.
            onNodeWithText("Tasks").assertIsDisplayed()
            onNodeWithText("Marketplace").assertIsDisplayed()

            // System items: the profile item renders its titleOverride, not "Profile".
            onNodeWithText("Settings").assertIsDisplayed()
            onNodeWithText("Ada Lovelace").assertIsDisplayed()

            // Brand version chip.
            onNodeWithText("v1.2.3").assertIsDisplayed()
        }

    @Test
    fun clickingMenuItemInvokesCallback() =
        runScreenTest {
            var clicked: MenuItemUiModel? = null
            setScreenContent {
                MainMenu(
                    menuItems = menuFixture,
                    appVersion = "1.2.3",
                    activeScriptCount = 0,
                    onClick = { clicked = it },
                )
            }

            onNodeWithText("Marketplace").performClick()

            assertEquals(Root.Routing.Marketplace, clicked?.route)
        }
}
