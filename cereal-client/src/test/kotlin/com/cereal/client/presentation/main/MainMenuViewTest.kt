package com.cereal.client.presentation.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * structure (section headers, item labels, user card) and the click callback contract.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class MainMenuViewTest {
    private fun readyMadeItem(
        route: Root.Routing,
        titleResource: StringResource,
    ) = MenuItemUiModel(
        route = route,
        externalUrl = null,
        resourcePath = IconSource.Vector(Icons.Filled.Bolt),
        titleResource = titleResource,
        titleOverride = null,
        selected = false,
        section = MenuSection.READY_MADE_SCRIPTS,
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
                section = MenuSection.AGENT,
            ),
            readyMadeItem(Root.Routing.Marketplace, Res.string.marketplace),
            MenuItemUiModel(
                route = Root.Routing.Profile,
                externalUrl = null,
                resourcePath = IconSource.Vector(Icons.Filled.VerifiedUser),
                titleResource = Res.string.your_profile,
                titleOverride = "Ada Lovelace",
                selected = false,
                section = null,
            ),
            MenuItemUiModel(
                route = Root.Routing.ApplicationSettings(),
                externalUrl = null,
                resourcePath = IconSource.Vector(Icons.Filled.Settings),
                titleResource = Res.string.settings,
                titleOverride = null,
                selected = false,
                section = MenuSection.SYSTEM,
            ),
        )

    @Test
    fun rendersSectionsItemsAndUserCard() =
        runScreenTest {
            setScreenContent {
                MainMenu(
                    menuItems = menuFixture,
                    activeScriptCount = 2,
                    onClick = {},
                )
            }

            // Section labels are uppercased by MenuSectionLabel.
            onNodeWithText("AGENT").assertIsDisplayed()
            onNodeWithText("READY-MADE SCRIPTS").assertIsDisplayed()
            onNodeWithText("SYSTEM").assertIsDisplayed()
            // A section without items (no Resources in this fixture) is not shown.
            onNodeWithText("RESOURCES").assertDoesNotExist()

            // Section items.
            onNodeWithText("Tasks").assertIsDisplayed()
            onNodeWithText("Marketplace").assertIsDisplayed()

            // System items: the profile item renders its titleOverride, not "Profile".
            onNodeWithText("Settings").assertIsDisplayed()
            onNodeWithText("Ada Lovelace").assertIsDisplayed()
        }

    @Test
    fun clickingMenuItemInvokesCallback() =
        runScreenTest {
            var clicked: MenuItemUiModel? = null
            setScreenContent {
                MainMenu(
                    menuItems = menuFixture,
                    activeScriptCount = 0,
                    onClick = { clicked = it },
                )
            }

            onNodeWithText("Marketplace").performClick()

            assertEquals(Root.Routing.Marketplace, clicked?.route)
        }

    @Test
    fun collapsedRailShowsIconsBadgesAndAvatarWithoutLabels() =
        runScreenTest {
            var clicked: MenuItemUiModel? = null
            val withBadge = menuFixture.replacingAt(0, menuFixture[0].copy(badge = 3))
            setScreenContent {
                MainMenu(
                    menuItems = withBadge,
                    activeScriptCount = 2,
                    collapsed = true,
                    onClick = { clicked = it },
                )
            }

            onNodeWithText("AGENT").assertDoesNotExist()
            onNodeWithText("Marketplace").assertDoesNotExist()
            onNodeWithText("3").assertIsDisplayed()

            // Labels survive as content descriptions, so icon-only items are still reachable.
            onNodeWithContentDescription("Marketplace").performClick()
            assertEquals(Root.Routing.Marketplace, clicked?.route)

            onNodeWithContentDescription("Ada Lovelace").performClick()
            assertEquals(Root.Routing.Profile, clicked?.route)
        }

    @Test
    fun toggleButtonInvokesCallbackInBothStates() =
        runScreenTest {
            var collapsed by mutableStateOf(false)
            setScreenContent {
                MainMenu(
                    menuItems = menuFixture,
                    activeScriptCount = 0,
                    collapsed = collapsed,
                    onToggleCollapsed = { collapsed = !collapsed },
                    onClick = {},
                )
            }

            onNodeWithContentDescription("Collapse sidebar").performClick()
            onNodeWithContentDescription("Expand sidebar").performClick()
            onNodeWithContentDescription("Collapse sidebar").assertIsDisplayed()
        }
}
