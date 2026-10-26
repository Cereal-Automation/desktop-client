package com.cereal.client.presentation.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
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
import com.cereal.client.application.interactor.app.ObserveSidebarCollapsedInteractor
import com.cereal.client.application.interactor.app.SetSidebarCollapsedInteractor
import com.cereal.client.application.interactor.auth.GetAuthenticatedUserInteractor
import com.cereal.client.application.interactor.notification.ObserveUnseenNotificationCountInteractor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.task.ObserveTasksInteractor
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.task.needsAttention
import com.cereal.client.presentation.navigation.MenuReselectionCoordinator
import com.cereal.client.presentation.navigation.Root
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.home
import com.cereal_automation.cereal_client.generated.resources.marketplace
import com.cereal_automation.cereal_client.generated.resources.my_scripts
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource

@Suppress("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    getAuthenticatedUserInteractor: GetAuthenticatedUserInteractor,
    private val applicationConfig: ApplicationConfig,
    private val checkForUpdatesInteractor: CheckForUpdatesInteractor,
    private val menuReselectionCoordinator: MenuReselectionCoordinator,
    getScriptsInteractor: GetScriptsInteractor,
    private val observeTasksInteractor: ObserveTasksInteractor,
    private val observeUnseenNotificationCountInteractor: ObserveUnseenNotificationCountInteractor,
    observeSidebarCollapsedInteractor: ObserveSidebarCollapsedInteractor,
    private val setSidebarCollapsedInteractor: SetSidebarCollapsedInteractor,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.main)

    /** Where the app opens: Home, except white-label builds, which keep their own landing on Tasks (spec #141). */
    val landingRoute: Root.Routing = if (applicationConfig.isBranded) Root.Routing.Tasks() else Root.Routing.Home

    // selectedRoute must be initialized before menuItems, since buildMenuItems() reads it.
    private var selectedRoute: Root.Routing = landingRoute
    private var username: String? = null
    private var isUpdateAvailable: Boolean = false
    private var runningTaskCount: Int = 0

    /** Tasks that need the user: errored or waiting for input. */
    private var attentionTaskCount: Int = 0
    private var unseenNotificationCount: Int = 0

    val menuItems = mutableStateOf(buildMenuItems())
    val activeScriptCount = mutableStateOf(0)
    val isSidebarCollapsed = mutableStateOf(false)

    /** Toggles waiting to be saved; conflated, so a burst of clicks only saves the latest choice. */
    private val sidebarWrites = Channel<Boolean>(Channel.CONFLATED)

    init {
        scope.launch(dispatcherProvider.io) {
            getAuthenticatedUserInteractor(Interactor.None()).collectLatest { result ->
                withContext(dispatcherProvider.main) {
                    if (result is SuspendableResult.Success) {
                        username = result.value?.name
                        // Logging out tears down the router; the next login lands on the landing route again.
                        if (result.value == null) selectedRoute = landingRoute
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

        // The preference is stored per user, so (like the script count) it is only readable once logged in.
        scope.launch(dispatcherProvider.io) {
            getAuthenticatedUserInteractor(Interactor.None())
                .map { (it as? SuspendableResult.Success)?.value }
                .flatMapLatest { user ->
                    if (user == null) {
                        // Don't carry one user's choice over to the next login.
                        isSidebarCollapsed.value = false
                        emptyFlow()
                    } else {
                        observeSidebarCollapsedInteractor(Interactor.None())
                    }
                }.collectLatest { result ->
                    if (result is SuspendableResult.Success) {
                        val collapsed = result.value
                        withContext(dispatcherProvider.main) {
                            isSidebarCollapsed.value = collapsed
                        }
                    }
                }
        }

        // A single consumer saves toggles in click order, so a quick double-click can't land out of order.
        scope.launch(dispatcherProvider.io) {
            for (collapsed in sidebarWrites) {
                setSidebarCollapsedInteractor(SetSidebarCollapsedInteractor.Params(collapsed)) { result ->
                    if (result is SuspendableResult.Failure) {
                        withContext(dispatcherProvider.main) {
                            // Not saved: undo the optimistic flip, unless the user has toggled again since.
                            if (isSidebarCollapsed.value == collapsed) isSidebarCollapsed.value = !collapsed
                        }
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
                    val tasks = result.value
                    val running = tasks.count { it.status is TaskStatus.Running }
                    val attention = tasks.count { it.needsAttention }
                    withContext(dispatcherProvider.main) {
                        runningTaskCount = running
                        attentionTaskCount = attention
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
        item.route?.let { onNavigatedTo(it) }
    }

    /** Tracks in-app navigation, including routes without a menu item, so the highlight never goes stale. */
    fun onNavigatedTo(route: Root.Routing) {
        selectedRoute = route
        menuItems.value = buildMenuItems()
    }

    fun onMenuItemReselected(route: Root.Routing) {
        menuReselectionCoordinator.notifyReselected(route)
    }

    fun onToggleSidebar() {
        val collapsed = !isSidebarCollapsed.value
        // Flip immediately for a responsive toggle; the observed preference then confirms it.
        isSidebarCollapsed.value = collapsed
        sidebarWrites.trySend(collapsed)
    }

    private fun buildMenuItems(): ImmutableList<MenuItemUiModel> =
        buildList {
            if (!applicationConfig.isBranded) {
                this +=
                    createMenuItem(
                        route = Root.Routing.Home,
                        resourcePath = IconSource.Vector(Icons.Filled.Home),
                        titleResource = Res.string.home,
                        section = MenuSection.AGENT,
                    )
            }
            this +=
                createMenuItem(
                    route = Root.Routing.Tasks(),
                    resourcePath = IconSource.Vector(Icons.Filled.Bolt),
                    titleResource = Res.string.tasks,
                    section = MenuSection.AGENT,
                    // Red only when something needs the user; otherwise a neutral running count.
                    badge = (if (attentionTaskCount > 0) attentionTaskCount else runningTaskCount).takeIf { it > 0 },
                    isBadgeAttention = attentionTaskCount > 0,
                )
            this +=
                createMenuItem(
                    route = Root.Routing.NotificationCenter,
                    resourcePath = IconSource.Vector(Icons.Filled.Notifications),
                    titleResource = Res.string.notifications,
                    section = MenuSection.AGENT,
                    badge = unseenNotificationCount.takeIf { it > 0 },
                )
            // Ready-made scripts are hidden in white-label builds: the appliance is locked to its
            // Brand scripts, with no browsing or installing of others (see docs/adr/0003).
            if (!applicationConfig.isBranded) {
                this +=
                    createMenuItem(
                        route = Root.Routing.MyScripts,
                        resourcePath = IconSource.Vector(Icons.Filled.Code),
                        titleResource = Res.string.my_scripts,
                        section = MenuSection.READY_MADE_SCRIPTS,
                    )
                this +=
                    createMenuItem(
                        route = Root.Routing.Marketplace,
                        resourcePath = IconSource.Vector(Icons.Filled.Storefront),
                        titleResource = Res.string.marketplace,
                        section = MenuSection.READY_MADE_SCRIPTS,
                    )
            }
            this +=
                createMenuItem(
                    route = Root.Routing.ProxyManager,
                    resourcePath = IconSource.Vector(Icons.Filled.AccountTree),
                    titleResource = Res.string.proxies,
                    section = MenuSection.RESOURCES,
                )
            this +=
                createMenuItem(
                    route = Root.Routing.CustomDatasetsManager,
                    resourcePath = IconSource.Vector(Icons.Filled.Storage),
                    titleResource = Res.string.script_datasets,
                    section = MenuSection.RESOURCES,
                )
            this +=
                createMenuItem(
                    route = Root.Routing.Profile,
                    resourcePath = IconSource.Vector(Icons.Filled.VerifiedUser),
                    titleResource = Res.string.your_profile,
                    section = null,
                    titleOverride = username,
                )
            this +=
                createMenuItem(
                    route = Root.Routing.ApplicationSettings(),
                    resourcePath = IconSource.Vector(Icons.Filled.Settings),
                    titleResource = Res.string.settings,
                    section = MenuSection.SYSTEM,
                    hasNotificationDot = isUpdateAvailable,
                )
        }.toImmutableList()

    private fun createMenuItem(
        route: Root.Routing,
        resourcePath: IconSource,
        titleResource: StringResource,
        section: MenuSection?,
        titleOverride: String? = null,
        hasNotificationDot: Boolean = false,
        badge: Int? = null,
        isBadgeAttention: Boolean = true,
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
            section,
            hasNotificationDot,
            badge,
            isBadgeAttention,
        )
}
