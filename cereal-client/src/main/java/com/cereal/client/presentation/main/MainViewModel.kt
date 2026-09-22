package com.cereal.client.presentation.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.app.UpdateCheckResult
import com.cereal.client.application.interactor.app.CheckForUpdatesInteractor
import com.cereal.client.application.interactor.auth.GetAuthenticatedUserInteractor
import com.cereal.client.application.interactor.notification.ObserveUnseenNotificationCountInteractor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.task.ObserveTasksInteractor
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.presentation.navigation.MenuReselectionCoordinator
import com.cereal.client.presentation.navigation.Root
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.marketplace
import com.cereal_automation.cereal_client.generated.resources.notifications
import com.cereal_automation.cereal_client.generated.resources.proxies
import com.cereal_automation.cereal_client.generated.resources.script_datasets
import com.cereal_automation.cereal_client.generated.resources.settings
import com.cereal_automation.cereal_client.generated.resources.tasks
import com.cereal_automation.cereal_client.generated.resources.your_profile
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    dispatcherProvider: CoroutinesDispatcherProvider,
    getAuthenticatedUserInteractor: GetAuthenticatedUserInteractor,
    private val applicationConfig: ApplicationConfig,
    private val checkForUpdatesInteractor: CheckForUpdatesInteractor,
    private val menuReselectionCoordinator: MenuReselectionCoordinator,
    getScriptsInteractor: GetScriptsInteractor,
    private val observeTasksInteractor: ObserveTasksInteractor,
    private val observeUnseenNotificationCountInteractor: ObserveUnseenNotificationCountInteractor,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.main)

    // selectedRoute must be initialized before menuItems, since buildMenuItems() reads it.
    private var selectedRoute: Root.Routing = Root.Routing.Tasks()
    private var username: String? = null
    private var isUpdateAvailable: Boolean = false
    private var activeTaskCount: Int = 0
    private var unseenNotificationCount: Int = 0

    val menuItems = mutableStateOf(buildMenuItems())
    val appVersion: String = applicationConfig.versionName
    val activeScriptCount = mutableStateOf(0)

    init {
        scope.launch(dispatcherProvider.io) {
            getAuthenticatedUserInteractor(Interactor.None()).collectLatest { result ->
                withContext(dispatcherProvider.main) {
                    if (result is SuspendableResult.Success) {
                        username = result.value?.name
                    }
                    menuItems.value = buildMenuItems()
                }
            }
        }

        // Re-subscribe to the script flow on every login/logout. `GetScriptsInteractor`
        // calls `UserSession.requireUser()` which throws synchronously if no user is set,
        // so launching it before login (which is when this VM is constructed) would
        // permanently fail the flow. Gating on the user makes the count update after
        // login and reset on logout.
        scope.launch(dispatcherProvider.io) {
            getAuthenticatedUserInteractor(Interactor.None())
                .map { (it as? SuspendableResult.Success)?.value }
                .flatMapLatest { user ->
                    if (user == null) {
                        activeScriptCount.value = 0
                        emptyFlow()
                    } else {
                        getScriptsInteractor(Interactor.None())
                    }
                }.collectLatest { result ->
                    if (result is SuspendableResult.Success) {
                        val count = result.value.size
                        withContext(dispatcherProvider.main) {
                            activeScriptCount.value = count
                        }
                    }
                }
        }

        scope.launch(dispatcherProvider.io) {
            observeUnseenNotificationCountInteractor(Interactor.None()).collectLatest { result ->
                if (result is SuspendableResult.Success) {
                    val count = result.value
                    withContext(dispatcherProvider.main) {
                        unseenNotificationCount = count
                        menuItems.value = buildMenuItems()
                    }
                }
            }
        }

        scope.launch(dispatcherProvider.io) {
            observeTasksInteractor(Interactor.None()).collectLatest { result ->
                if (result is SuspendableResult.Success) {
                    val count = result.value.count { it.status is TaskStatus.Running || it.status is TaskStatus.Idle }
                    withContext(dispatcherProvider.main) {
                        activeTaskCount = count
                        menuItems.value = buildMenuItems()
                    }
                }
            }
        }

        scope.launch(dispatcherProvider.io) {
            checkForUpdatesInteractor(Interactor.None()) { result ->
                if (result is SuspendableResult.Success) {
                    val updateResult = result.value
                    isUpdateAvailable = updateResult is UpdateCheckResult.UpdateAvailable ||
                        updateResult is UpdateCheckResult.UpdateRequired
                    withContext(dispatcherProvider.main) {
                        menuItems.value = buildMenuItems()
                    }
                }
            }
        }
    }

    fun onMenuItemClicked(item: MenuItemUiModel) {
        item.route?.let { route ->
            selectedRoute = route
            menuItems.value = buildMenuItems()
        }
    }

    fun onMenuItemReselected(route: Root.Routing) {
        menuReselectionCoordinator.notifyReselected(route)
    }

    private fun buildMenuItems(): ImmutableList<MenuItemUiModel> =
        buildList {
            add(
                createMenuItem(
                    route = Root.Routing.Tasks(),
                    resourcePath = IconSource.Vector(Icons.Filled.Bolt),
                    titleResource = Res.string.tasks,
                    mainItem = true,
                    badge = activeTaskCount.takeIf { it > 0 },
                ),
            )
            add(
                createMenuItem(
                    route = Root.Routing.NotificationCenter,
                    resourcePath = IconSource.Vector(Icons.Filled.Notifications),
                    titleResource = Res.string.notifications,
                    mainItem = true,
                    badge = unseenNotificationCount.takeIf { it > 0 },
                ),
            )
            // The marketplace is hidden in white-label builds: the appliance is locked to its
            // Brand scripts, with no browsing or installing of others (see docs/adr/0003).
            if (!applicationConfig.isBranded) {
                add(
                    createMenuItem(
                        route = Root.Routing.Marketplace,
                        resourcePath = IconSource.Vector(Icons.Filled.Storefront),
                        titleResource = Res.string.marketplace,
                        mainItem = true,
                    ),
                )
            }
            add(
                createMenuItem(
                    route = Root.Routing.ProxyManager,
                    resourcePath = IconSource.Vector(Icons.Filled.AccountTree),
                    titleResource = Res.string.proxies,
                    mainItem = true,
                ),
            )
            add(
                createMenuItem(
                    route = Root.Routing.CustomDatasetsManager,
                    resourcePath = IconSource.Vector(Icons.Filled.Storage),
                    titleResource = Res.string.script_datasets,
                    mainItem = true,
                ),
            )
            add(
                createMenuItem(
                    route = Root.Routing.Profile,
                    resourcePath = IconSource.Vector(Icons.Filled.VerifiedUser),
                    titleResource = Res.string.your_profile,
                    mainItem = false,
                    titleOverride = username,
                ),
            )
            add(
                createMenuItem(
                    route = Root.Routing.ApplicationSettings,
                    resourcePath = IconSource.Vector(Icons.Filled.Settings),
                    titleResource = Res.string.settings,
                    mainItem = false,
                    hasNotificationDot = isUpdateAvailable,
                ),
            )
        }.toImmutableList()

    private fun createMenuItem(
        route: Root.Routing,
        resourcePath: IconSource,
        titleResource: StringResource,
        mainItem: Boolean,
        titleOverride: String? = null,
        hasNotificationDot: Boolean = false,
        badge: Int? = null,
    ): MenuItemUiModel =
        MenuItemUiModel(
            route,
            null,
            resourcePath,
            titleResource,
            titleOverride,
            // Compare by class, not equality: `Routing.Tasks` carries an optional
            // deep-link payload, so `Tasks(null) != Tasks("x")` even though both are
            // "the Tasks route" for sidebar-selection purposes.
            route::class == selectedRoute::class,
            mainItem,
            hasNotificationDot,
            badge,
        )
}
