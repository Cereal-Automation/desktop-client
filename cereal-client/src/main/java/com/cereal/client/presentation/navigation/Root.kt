package com.cereal.client.presentation.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import com.cereal.client.presentation.customdataset.CustomDatasetsScreen
import com.cereal.client.presentation.home.HomeScreen
import com.cereal.client.presentation.marketplace.MarketplaceScreen
import com.cereal.client.presentation.myscripts.MyScriptsScreen
import com.cereal.client.presentation.navigation.router.BackStack
import com.cereal.client.presentation.navigation.router.Router
import com.cereal.client.presentation.notification.NotificationCenterScreen
import com.cereal.client.presentation.profile.ProfileScreen
import com.cereal.client.presentation.proxy.ProxiesScreen
import com.cereal.client.presentation.settings.ApplicationSettingsScreen
import com.cereal.client.presentation.tasks.TasksScreen
import com.cereal.client.presentation.tasks.script.overview.ScriptRequest
import com.cereal.client.presentation.view.VerticalDivider

interface Root {
    sealed class Routing {
        data object Home : Routing()

        data class Tasks(
            val openScriptSelectionFor: ScriptRequest? = null,
            val focusTaskId: String? = null,
        ) : Routing()

        data object ProxyManager : Routing()

        data object NotificationCenter : Routing()

        data object CustomDatasetsManager : Routing()

        data object Profile : Routing()

        data object Marketplace : Routing()

        data object MyScripts : Routing()

        /** [checkForUpdates] opens Settings straight into the client update flow; [scrollToAi] opens it at the AI section. */
        data class ApplicationSettings(
            val checkForUpdates: Boolean = false,
            val scrollToAi: Boolean = false,
        ) : Routing()
    }

    companion object {
        @ExperimentalFoundationApi
        @Composable
        fun content(
            defaultRouting: Routing,
            menuComposable: @Composable (BackStack<Routing>) -> Unit,
            onNavigateTo: ((Routing) -> Unit)? = null,
        ) {
            Router(Root::class.java.name, defaultRouting) { backStack ->
                // "Start new instance" from Marketplace or My Scripts: new root on Tasks with that script's selection open.
                val startNewInstance: (String) -> Unit = { publicIdentifier ->
                    val route = Routing.Tasks(openScriptSelectionFor = ScriptRequest(publicIdentifier))
                    backStack.newRoot(route)
                    onNavigateTo?.invoke(route)
                }
                Row {
                    menuComposable(backStack)

                    VerticalDivider()

                    Column {
                        when (backStack.last()) {
                            is Routing.Home -> {
                                HomeScreen(
                                    onNavigateToMarketplace = {
                                        backStack.newRoot(Routing.Marketplace)
                                        onNavigateTo?.invoke(Routing.Marketplace)
                                    },
                                    onOpenClientUpdate = {
                                        backStack.newRoot(Routing.ApplicationSettings(checkForUpdates = true))
                                        onNavigateTo?.invoke(Routing.ApplicationSettings(checkForUpdates = true))
                                    },
                                    onOpenTasks = { taskId ->
                                        val route = Routing.Tasks(focusTaskId = taskId)
                                        backStack.newRoot(route)
                                        onNavigateTo?.invoke(route)
                                    },
                                    onRunScript = { request ->
                                        val route = Routing.Tasks(openScriptSelectionFor = request)
                                        backStack.newRoot(route)
                                        onNavigateTo?.invoke(route)
                                    },
                                    onNavigateToAiSettings = {
                                        val route = Routing.ApplicationSettings(scrollToAi = true)
                                        backStack.newRoot(route)
                                        onNavigateTo?.invoke(route)
                                    },
                                )
                            }

                            is Routing.Tasks -> {
                                TasksScreen(
                                    onNavigateToMarketplace = {
                                        backStack.newRoot(Routing.Marketplace)
                                        onNavigateTo?.invoke(Routing.Marketplace)
                                    },
                                    openScriptSelectionFor = (backStack.last() as? Routing.Tasks)?.openScriptSelectionFor,
                                    focusTaskId = (backStack.last() as? Routing.Tasks)?.focusTaskId,
                                )
                            }

                            is Routing.ProxyManager -> {
                                ProxiesScreen()
                            }

                            is Routing.NotificationCenter -> {
                                NotificationCenterScreen(
                                    onOpenTask = { taskId ->
                                        backStack.newRoot(Routing.Tasks(focusTaskId = taskId))
                                        onNavigateTo?.invoke(Routing.Tasks(focusTaskId = taskId))
                                    },
                                )
                            }

                            is Routing.CustomDatasetsManager -> {
                                CustomDatasetsScreen()
                            }

                            is Routing.Profile -> {
                                ProfileScreen()
                            }

                            is Routing.Marketplace -> {
                                MarketplaceScreen(
                                    onStartNewInstance = startNewInstance,
                                )
                            }

                            is Routing.MyScripts -> {
                                MyScriptsScreen(
                                    onOpenMarketplace = {
                                        backStack.newRoot(Routing.Marketplace)
                                        onNavigateTo?.invoke(Routing.Marketplace)
                                    },
                                    onStartNewInstance = startNewInstance,
                                )
                            }

                            is Routing.ApplicationSettings -> {
                                val route = backStack.last() as Routing.ApplicationSettings
                                ApplicationSettingsScreen(
                                    checkForUpdatesOnOpen = route.checkForUpdates,
                                    scrollToAiOnOpen = route.scrollToAi,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
