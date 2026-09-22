package com.cereal.client.presentation.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import com.cereal.client.presentation.customdataset.CustomDatasetsScreen
import com.cereal.client.presentation.marketplace.MarketplaceScreen
import com.cereal.client.presentation.myscripts.MyScriptsScreen
import com.cereal.client.presentation.navigation.router.BackStack
import com.cereal.client.presentation.navigation.router.Router
import com.cereal.client.presentation.notification.NotificationCenterScreen
import com.cereal.client.presentation.profile.ProfileScreen
import com.cereal.client.presentation.proxy.ProxiesScreen
import com.cereal.client.presentation.settings.ApplicationSettingsScreen
import com.cereal.client.presentation.tasks.TasksScreen
import com.cereal.client.presentation.view.VerticalDivider

interface Root {
    sealed class Routing {
        data class Tasks(
            val openScriptSelectionForPackage: String? = null,
            val focusTaskId: String? = null,
        ) : Routing()

        data object ProxyManager : Routing()

        data object NotificationCenter : Routing()

        data object CustomDatasetsManager : Routing()

        data object Profile : Routing()

        data object Marketplace : Routing()

        data object MyScripts : Routing()

        data object ApplicationSettings : Routing()
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
                Row {
                    menuComposable(backStack)

                    VerticalDivider()

                    Column {
                        when (backStack.last()) {
                            is Routing.Tasks -> {
                                TasksScreen(
                                    onNavigateToMarketplace = {
                                        backStack.newRoot(Routing.Marketplace)
                                        onNavigateTo?.invoke(Routing.Marketplace)
                                    },
                                    openScriptSelectionForPackage = (backStack.last() as? Routing.Tasks)?.openScriptSelectionForPackage,
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
                                    onStartNewInstance = { publicIdentifier ->
                                        backStack.newRoot(Routing.Tasks(openScriptSelectionForPackage = publicIdentifier))
                                        onNavigateTo?.invoke(Routing.Tasks(openScriptSelectionForPackage = publicIdentifier))
                                    },
                                )
                            }

                            is Routing.MyScripts -> {
                                MyScriptsScreen(
                                    onOpenMarketplace = {
                                        backStack.newRoot(Routing.Marketplace)
                                        onNavigateTo?.invoke(Routing.Marketplace)
                                    },
                                )
                            }

                            is Routing.ApplicationSettings -> {
                                ApplicationSettingsScreen(
                                    onNavigateToMyScripts = {
                                        backStack.push(Routing.MyScripts)
                                        onNavigateTo?.invoke(Routing.MyScripts)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
